package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.data.repository.PersistentBackRepository
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.teleport.back.BackApplicationService
import cn.tj.dzd.mc.dzt.teleport.back.BackEntry
import cn.tj.dzd.mc.dzt.teleport.back.BackMutationResult
import cn.tj.dzd.mc.dzt.teleport.back.BackQueryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import taboolib.common.platform.event.SubscribeEvent
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 死亡返回点展示模型。
 *
 * @param time 死亡记录创建时间戳。
 * @param location 死亡返回点坐标。
 */
data class DTPBack(
    val time: Long,
    val location: Location,
)

object BackService {
    private val application = BackApplicationService(PersistentBackRepository)

    @SubscribeEvent
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val pl = event.entity
        val ownerId = pl.uniqueId
        val location = pl.location
        val storedLocation = StoredLocation(
            location.world?.key.toString(),
            location.x,
            location.y,
            location.z,
        )
        DztAsyncExecutor.supply {
            addBackRecord(ownerId, storedLocation)
        }
    }

    /**
     * 新增死亡返回点。
     *
     * @param pl 玩家对象。
     * @param location 死亡返回点位置对象。
     * @return 数据库写入是否成功。
     */
    fun addBackRecord(pl: Player, location: Location): Boolean {
        return addBackRecord(pl.uniqueId, location)
    }

    /**
     * 为指定玩家 UUID 新增死亡返回点。
     *
     * 该重载会读取 Bukkit [Location] 的世界信息，因此必须在允许访问世界状态的线程调用。
     * 后台数据任务请先创建 [StoredLocation]，再使用同名的快照重载。
     *
     * @param uuid 玩家 UUID。
     * @param location Bukkit 死亡位置。
     * @return 数据写入与超额清理是否全部成功。
     */
    fun addBackRecord(uuid: UUID, location: Location): Boolean {
        val storedLocation = StoredLocation(
            location.world?.key.toString(),
            location.x,
            location.y,
            location.z,
        )
        return addBackRecord(uuid, storedLocation)
    }

    /**
     * 使用纯坐标快照新增死亡返回点。
     *
     * 此重载不读取 Bukkit 状态，可由 [DztAsyncExecutor] 的数据库任务调用。
     *
     * @param uuid 玩家 UUID。
     * @param location 不依赖 Bukkit 的死亡位置快照。
     * @return 数据写入与超额清理是否全部成功。
     */
    fun addBackRecord(uuid: UUID, location: StoredLocation): Boolean {
        val time = System.currentTimeMillis()
        return application.recordBack(uuid, time, location) == BackMutationResult.SUCCESS
    }

    /**
     * 删除指定死亡返回点。
     *
     * @param pl 玩家对象。
     * @param time 死亡记录创建时间戳。
     * @return 数据库删除是否成功。
     */
    fun removeBackRecord(pl: Player, time: Long): Boolean {
        return removeBackRecord(pl.uniqueId, time)
    }

    /**
     * 删除指定玩家 UUID 的死亡返回点。
     *
     * 该重载适用于异步数据访问流程；调用方应在进入后台线程前先在玩家实体线程采样 UUID，
     * 避免在数据库任务中持有或访问 Bukkit Player。
     *
     * @param uuid 玩家 UUID。
     * @param time 死亡记录创建时间戳。
     * @return 数据库删除是否成功。
     */
    fun removeBackRecord(uuid: UUID, time: Long): Boolean {
        return application.deleteBack(uuid, time) == BackMutationResult.SUCCESS
    }

    /**
     * 获取玩家死亡返回点列表。
     *
     * 该兼容接口会解析 Bukkit World；不要在后台数据任务中调用，异步流程请使用 [getBackEntryList]。
     *
     * @param pl 玩家对象。
     * @return 按创建时间倒序排列的死亡返回点坐标和时间戳列表。
     */
    fun getBackRecordList(pl: Player): List<DTPBack> {
        return getBackRecordList(pl.uniqueId)
    }

    /**
     * 获取玩家死亡返回点列表。
     *
     * 该兼容接口会解析 Bukkit World；不要在后台数据任务中调用，异步流程请使用 [getBackEntryList]。
     *
     * @param uuid 玩家 UUID。
     * @return 按创建时间倒序排列的死亡返回点坐标和时间戳列表。
     */
    fun getBackRecordList(uuid: UUID): List<DTPBack> {
        return getBackEntryList(uuid).map(::toDTPBack)
    }

    /**
     * 读取玩家的死亡返回点持久化条目。
     *
     * 此接口只返回不依赖 Bukkit 的 [BackEntry] 与 [StoredLocation]，可在 [DztAsyncExecutor] 等后台数据任务中调用。
     * 需要展示或传送时，应在玩家实体线程通过 [toDTPBack] 转换为平台展示模型。
     *
     * @param uuid 玩家 UUID。
     * @return 按创建时间倒序排列的死亡返回点条目。数据库读取失败时返回空列表。
     */
    fun getBackEntryList(uuid: UUID): List<BackEntry> {
        return when (val result = application.listBacks(uuid)) {
            is BackQueryResult.Success -> result.backs
            BackQueryResult.InfrastructureFailure -> emptyList()
        }
    }

    /**
     * 将持久化死亡返回点条目转换为 Bukkit 展示模型。
     *
     * 转换会按世界名称解析 Bukkit World，因此必须在允许访问世界状态的线程调用；玩家 UI 流程应先通过
     * [org.bukkit.entity.Player.foliaRun][cn.tj.dzd.mc.dzt.util.foliaRun] 回到玩家实体线程后再调用。
     *
     * @param entry 已从存储读取的死亡返回点条目。
     * @return 包含 Bukkit 坐标的展示模型。
     */
    fun toDTPBack(entry: BackEntry): DTPBack {
        return entry.toBack()
    }

    /**
     * 前往指定死亡返回点。
     *
     * 该接口会在玩家所属实体线程上触发异步传送，可在 Folia 环境中安全调用。
     *
     * @param pl 玩家对象。
     * @param time 死亡记录创建时间戳。
     * @param cause 传送原因。
     * @return 传送结果 Future；未找到记录、目标世界不存在或玩家已离线时完成为 false。
     */
    fun teleportBack(pl: Player, time: Long, cause: TeleportCause = TeleportCause.PLUGIN): CompletableFuture<Boolean> {
        val back = when (val result = application.listBacks(pl.uniqueId)) {
            is BackQueryResult.Success -> result.backs.firstOrNull { it.time == time }?.toBack()
            BackQueryResult.InfrastructureFailure -> null
        } ?: return CompletableFuture.completedFuture(false)

        return teleportBack(pl, back, cause)
    }

    /**
     * 前往指定死亡返回点。
     *
     * 该接口会在玩家所属实体线程上触发异步传送，可在 Folia 环境中安全调用。
     *
     * @param pl 玩家对象。
     * @param back 死亡返回点展示模型。
     * @param cause 传送原因。
     * @return 传送结果 Future；目标世界不存在或玩家已离线时完成为 false。
     */
    fun teleportBack(pl: Player, back: DTPBack, cause: TeleportCause = TeleportCause.PLUGIN): CompletableFuture<Boolean> {
        return pl.foliaTeleport(back.location, cause)
    }

    /**
     * 前往最近一次死亡返回点。
     *
     * 该接口会在玩家所属实体线程上触发异步传送，可在 Folia 环境中安全调用。
     *
     * @param pl 玩家对象。
     * @param cause 传送原因。
     * @return 传送结果 Future；没有死亡记录、目标世界不存在或玩家已离线时完成为 false。
     */
    fun teleportLatestBack(pl: Player, cause: TeleportCause = TeleportCause.PLUGIN): CompletableFuture<Boolean> {
        val back = when (val result = application.listBacks(pl.uniqueId)) {
            is BackQueryResult.Success -> result.backs.firstOrNull()?.toBack()
            BackQueryResult.InfrastructureFailure -> null
        } ?: return CompletableFuture.completedFuture(false)

        return teleportBack(pl, back, cause)
    }

    private fun BackEntry.toBack(): DTPBack {
        return DTPBack(
            time,
            Location(resolveWorld(location.world), location.x, location.y, location.z)
        )
    }
}

/**
 * 获取玩家死亡返回点列表。
 *
 * @return 按创建时间倒序排列的死亡返回点坐标和时间戳列表。
 */
fun Player.getTeleportBackList(): List<DTPBack> {
    return BackService.getBackRecordList(this)
}

/**
 * 删除指定死亡返回点。
 *
 * @param time 死亡记录创建时间戳。
 * @return 数据库删除是否成功。
 */
fun Player.deleteTeleportBack(time: Long): Boolean {
    return BackService.removeBackRecord(this, time)
}

/**
 * 前往指定死亡返回点。
 *
 * @param time 死亡记录创建时间戳。
 * @return 传送结果 Future；未找到记录、目标世界不存在或玩家已离线时完成为 false。
 */
fun Player.teleportBack(time: Long): CompletableFuture<Boolean> {
    return BackService.teleportBack(this, time)
}

/**
 * 前往指定死亡返回点。
 *
 * @param back 死亡返回点展示模型。
 * @return 传送结果 Future；目标世界不存在或玩家已离线时完成为 false。
 */
fun Player.teleportBack(back: DTPBack): CompletableFuture<Boolean> {
    return BackService.teleportBack(this, back)
}

/**
 * 前往最近一次死亡返回点。
 *
 * @return 传送结果 Future；没有死亡记录、目标世界不存在或玩家已离线时完成为 false。
 */
fun Player.teleportLatestBack(): CompletableFuture<Boolean> {
    return BackService.teleportLatestBack(this)
}
