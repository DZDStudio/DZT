package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.data.repository.PersistentHomeRepository
import cn.tj.dzd.mc.dzt.teleport.home.HomeApplicationService
import cn.tj.dzd.mc.dzt.teleport.home.HomeCreateResult
import cn.tj.dzd.mc.dzt.teleport.home.HomeDeleteResult
import cn.tj.dzd.mc.dzt.teleport.home.HomeEntry
import cn.tj.dzd.mc.dzt.teleport.home.HomeQueryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import cn.tj.dzd.mc.dzt.util.Icon
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 家传送点展示模型。
 *
 * @param name 家名称。
 * @param icon 家图标。
 * @param location 家坐标。
 */
data class DTPHome(
    val name: String,
    val icon: Icon,
    val location: Location,
)

object HomeService {
    private val application = HomeApplicationService(PersistentHomeRepository)
    val DEFAULT_HOME_ICON = Icon.RED_BED

    /**
     * 新建玩家的家。
     *
     * 该重载会读取 Bukkit [Location] 的世界信息，因此必须在允许访问世界状态的线程调用。
     * 后台数据任务请先创建 [StoredLocation]，再使用同名的快照重载。
     *
     * @param pl 玩家对象。
     * @param name 家名称。
     * @param location 家坐标。
     * @param icon 家图标。
     * @return 数据库写入是否成功。
     */
    fun addHome(pl: Player, name: String, location: Location, icon: Icon = DEFAULT_HOME_ICON): Boolean {
        return addHome(pl.uniqueId, name, location, icon)
    }

    /**
     * 新建玩家的家。
     *
     * 使用玩家 UUID 与家名称作为业务定位条件。名称已存在、数量达到上限或参数不合法时会抛出异常；
     * 数据库操作失败时返回 false。
     *
     * @param uuid 玩家 UUID。
     * @param name 家名称。
     * @param location 家坐标。
     * @param icon 家图标。
     * @return 数据库写入是否成功。
     */
    fun addHome(uuid: UUID, name: String, location: Location, icon: Icon = DEFAULT_HOME_ICON): Boolean {
        return addHome(uuid, name, location.toStoredLocation(), icon)
    }

    /**
     * 使用纯坐标快照新建玩家的家。
     *
     * 此重载不读取 Bukkit 状态，可由 [cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor] 的数据库任务调用。
     *
     * @param uuid 玩家 UUID。
     * @param name 家名称。
     * @param location 不依赖 Bukkit 的位置快照。
     * @param icon 家图标。
     * @return 数据库写入是否成功。
     */
    fun addHome(uuid: UUID, name: String, location: StoredLocation, icon: Icon = DEFAULT_HOME_ICON): Boolean {
        return when (val result = application.createHome(uuid, name, location, icon.index)) {
            is HomeCreateResult.Success -> true
            is HomeCreateResult.InvalidName -> throw IllegalArgumentException(result.message)
            is HomeCreateResult.DuplicateName -> throw IllegalArgumentException("家名称[${result.name}]已存在")
            is HomeCreateResult.LimitReached -> error("最多只能设置 ${result.maximum} 个家")
            is HomeCreateResult.InvalidWorld -> throw IllegalArgumentException(result.message)
            HomeCreateResult.InfrastructureFailure -> false
        }
    }

    /**
     * 删除指定玩家的家。
     *
     * @param pl 玩家对象。
     * @param name 家名称。
     * @return 数据库删除是否成功。
     */
    fun removeHome(pl: Player, name: String): Boolean {
        return removeHome(pl.uniqueId, name)
    }

    /**
     * 删除指定玩家的家。
     *
     * 使用玩家 UUID 与家名称定位记录。
     *
     * @param uuid 玩家 UUID。
     * @param name 家名称。
     * @return 数据库删除是否成功。
     */
    fun removeHome(uuid: UUID, name: String): Boolean {
        return when (val result = application.deleteHome(uuid, name)) {
            HomeDeleteResult.Success -> true
            is HomeDeleteResult.InvalidName -> throw IllegalArgumentException(result.message)
            HomeDeleteResult.InfrastructureFailure -> false
        }
    }

    /**
     * 获取玩家的家列表。
     *
     * 该兼容接口会解析 Bukkit World；不要在后台数据任务中调用，异步流程请使用 [getHomeEntryList]。
     *
     * @param pl 玩家对象。
     * @return 家列表。数据库读取失败时返回空列表。
     */
    fun getHomeList(pl: Player): List<DTPHome> {
        return getHomeList(pl.uniqueId)
    }

    /**
     * 获取玩家的家列表。
     *
     * 该兼容接口会解析 Bukkit World；不要在后台数据任务中调用，异步流程请使用 [getHomeEntryList]。
     *
     * @param uuid 玩家 UUID。
     * @return 家列表。数据库读取失败时返回空列表。
     */
    fun getHomeList(uuid: UUID): List<DTPHome> {
        return getHomeEntryList(uuid).map(::toDTPHome)
    }

    /**
     * 读取玩家的 Home 持久化条目。
     *
     * 此接口只返回不依赖 Bukkit 的 [HomeEntry] 与 [StoredLocation]，可在 [cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor]
     * 等后台数据任务中调用。需要展示或传送时，应在玩家实体线程通过 [toDTPHome] 转换为平台展示模型。
     *
     * @param uuid 玩家 UUID。
     * @return 家条目列表。数据库读取失败时返回空列表。
     */
    fun getHomeEntryList(uuid: UUID): List<HomeEntry> {
        return when (val result = application.listHomes(uuid)) {
            is HomeQueryResult.Success -> result.homes
            HomeQueryResult.InfrastructureFailure -> emptyList()
        }
    }

    /**
     * 将持久化 Home 条目转换为 Bukkit 展示模型。
     *
     * 转换会按世界名称解析 Bukkit World，因此必须在允许访问世界状态的线程调用；玩家 UI 流程应先通过
     * [org.bukkit.entity.Player.foliaRun][cn.tj.dzd.mc.dzt.util.foliaRun] 回到玩家实体线程后再调用。
     *
     * @param entry 已从存储读取的 Home 条目。
     * @return 包含 Bukkit 坐标的展示模型。
     */
    fun toDTPHome(entry: HomeEntry): DTPHome {
        return entry.toHome()
    }

    /**
     * 前往指定玩家的家。
     *
     * 该接口会在玩家所属实体线程上触发异步传送，可在 Folia 环境中安全调用。
     *
     * @param pl 玩家对象。
     * @param name 家名称。
     * @param cause 传送原因。
     * @return 传送结果 Future；未找到家、目标世界不存在或玩家已离线时完成为 false。
     */
    fun teleportHome(pl: Player, name: String, cause: TeleportCause = TeleportCause.PLUGIN): CompletableFuture<Boolean> {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "家名称不能为空" }
        require(normalizedName.length <= HomeApplicationService.MAX_NAME_LENGTH) {
            "家名称长度不能超过 ${HomeApplicationService.MAX_NAME_LENGTH} 个字符"
        }
        val home = when (val result = application.listHomes(pl.uniqueId)) {
            is HomeQueryResult.Success -> result.homes.firstOrNull { it.name == normalizedName }?.toHome()
            HomeQueryResult.InfrastructureFailure -> null
        } ?: return CompletableFuture.completedFuture(false)

        return teleportHome(pl, home, cause)
    }

    /**
     * 前往指定玩家的家。
     *
     * 该接口会在玩家所属实体线程上触发异步传送，可在 Folia 环境中安全调用。
     *
     * @param pl 玩家对象。
     * @param home 家展示模型。
     * @param cause 传送原因。
     * @return 传送结果 Future；目标世界不存在或玩家已离线时完成为 false。
     */
    fun teleportHome(pl: Player, home: DTPHome, cause: TeleportCause = TeleportCause.PLUGIN): CompletableFuture<Boolean> {
        return pl.foliaTeleport(home.location, cause)
    }

    private fun Location.toStoredLocation(): StoredLocation {
        val worldName = requireNotNull(world) { "家坐标缺少世界" }.name
        return StoredLocation(worldName, x, y, z)
    }

    private fun HomeEntry.toHome(): DTPHome {
        val resolvedIcon = requireNotNull(Icon.entries.firstOrNull { it.index == iconIndex }) {
            "无法解析 Home 图标索引: $iconIndex"
        }
        return DTPHome(
            name,
            resolvedIcon,
            Location(resolveWorld(location.world), location.x, location.y, location.z)
        )
    }
}

/**
 * 新建玩家的家。
 *
 * @param name 家名称。
 * @param location 家坐标。
 * @param icon 家图标。
 * @return 数据库写入是否成功。
 */
fun Player.addTeleportHome(
    name: String,
    location: Location = this.location,
    icon: Icon = HomeService.DEFAULT_HOME_ICON
): Boolean {
    return HomeService.addHome(this, name, location, icon)
}

/**
 * 获取玩家的家列表。
 *
 * @return 家列表。数据库读取失败时返回空列表。
 */
fun Player.getTeleportHomeList(): List<DTPHome> {
    return HomeService.getHomeList(this)
}

/**
 * 删除指定的家。
 *
 * @param name 家名称。
 * @return 数据库删除是否成功。
 */
fun Player.deleteTeleportHome(name: String): Boolean {
    return HomeService.removeHome(this, name)
}

/**
 * 前往指定的家。
 *
 * @param name 家名称。
 * @return 传送结果 Future；未找到家、目标世界不存在或玩家已离线时完成为 false。
 */
fun Player.teleportHome(name: String): CompletableFuture<Boolean> {
    return HomeService.teleportHome(this, name)
}

/**
 * 前往指定的家。
 *
 * @param home 家展示模型。
 * @return 传送结果 Future；目标世界不存在或玩家已离线时完成为 false。
 */
fun Player.teleportHome(home: DTPHome): CompletableFuture<Boolean> {
    return HomeService.teleportHome(this, home)
}
