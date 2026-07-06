package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.BackRecord
import cn.tj.dzd.mc.dzt.data.table.backRecordMapper
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
    private const val MAX_BACK_RECORDS = 32

    @SubscribeEvent
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val pl = event.entity

        addBackRecord(pl, pl.location)
    }

    /**
     * 新增死亡返回点。
     *
     * @param pl 玩家对象。
     * @param location 死亡返回点位置对象。
     * @return 数据库写入是否成功。
     */
    fun addBackRecord(pl: Player, location: Location): Boolean {
        val time = System.currentTimeMillis()

        val inserted = DatabaseGuard.execute("新增死亡返回点", false) {
            backRecordMapper.insert(
                BackRecord(
                    pl.uniqueId,
                    time,
                    location.world?.key.toString(),
                    location.x,
                    location.y,
                    location.z
                )
            )
            true
        }
        if (!inserted) {
            return false
        }

        findBackRecords(pl.uniqueId)
            .drop(MAX_BACK_RECORDS)
            .forEach { removeBackRecord(pl, it.time) }

        return true
    }

    /**
     * 删除指定死亡返回点。
     *
     * @param pl 玩家对象。
     * @param time 死亡记录创建时间戳。
     * @return 数据库删除是否成功。
     */
    fun removeBackRecord(pl: Player, time: Long): Boolean {
        return DatabaseGuard.execute("删除死亡返回点") {
            backRecordMapper.deleteWhere {
                "uuid" eq pl.uniqueId.toString()
                "time" eq time
            }
        }
    }

    /**
     * 获取玩家死亡返回点列表。
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
     * @param uuid 玩家 UUID。
     * @return 按创建时间倒序排列的死亡返回点坐标和时间戳列表。
     */
    fun getBackRecordList(uuid: UUID): List<DTPBack> {
        return findBackRecords(uuid).map { it.toBack() }
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
        val back = findBackRecords(pl.uniqueId)
            .firstOrNull { it.time == time }
            ?.toBack()
            ?: return CompletableFuture.completedFuture(false)

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
        val back = findBackRecords(pl.uniqueId)
            .firstOrNull()
            ?.toBack()
            ?: return CompletableFuture.completedFuture(false)

        return teleportBack(pl, back, cause)
    }

    private fun findBackRecords(uuid: UUID): List<BackRecord> {
        return DatabaseGuard.execute("获取死亡返回点列表", emptyList()) {
            backRecordMapper.findAll {
                "uuid" eq uuid.toString()
            }.sortedByDescending { it.time }
        }
    }

    private fun BackRecord.toBack(): DTPBack {
        return DTPBack(
            time,
            Location(resolveWorld(world), x, y, z)
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
