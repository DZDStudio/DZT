package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.teleport.table.HomeRecord
import cn.tj.dzd.mc.dzt.teleport.table.homeRecordMapper
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
    private const val MAX_HOME_RECORDS = 16
    private const val MAX_HOME_NAME_LENGTH = 16
    private const val MAX_WORLD_NAME_LENGTH = 32
    val DEFAULT_HOME_ICON = Icon.RED_BED

    /**
     * 新建玩家的家。
     *
     * 使用玩家 UUID 与家名称作为业务定位条件。名称已存在、数量达到上限或参数不合法时会抛出异常；
     * 数据库操作失败时返回 false。
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
        val normalizedName = normalizeHomeName(name)
        val worldName = getWorldName(location)
        val homeRecords = findHomeRecords(uuid) ?: return false

        require(homeRecords.none { it.name == normalizedName }) { "家名称[$normalizedName]已存在" }
        check(homeRecords.size < MAX_HOME_RECORDS) { "最多只能设置 $MAX_HOME_RECORDS 个家" }

        return DatabaseGuard.execute("新增家", false) {
            homeRecordMapper.insert(
                HomeRecord(
                    uuid,
                    normalizedName,
                    icon,
                    worldName,
                    location.x,
                    location.y,
                    location.z
                )
            )
            true
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
        val normalizedName = normalizeHomeName(name)

        return DatabaseGuard.execute("删除家") {
            homeRecordMapper.deleteWhere {
                "uuid" eq uuid.toString()
                "name" eq normalizedName
            }
        }
    }

    /**
     * 获取玩家的家列表。
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
     * @param uuid 玩家 UUID。
     * @return 家列表。数据库读取失败时返回空列表。
     */
    fun getHomeList(uuid: UUID): List<DTPHome> {
        return findHomeRecords(uuid)
            ?.map { it.toHome() }
            ?: emptyList()
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
        val normalizedName = normalizeHomeName(name)
        val home = findHomeRecords(pl.uniqueId)
            ?.firstOrNull { it.name == normalizedName }
            ?.toHome()
            ?: return CompletableFuture.completedFuture(false)

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

    private fun findHomeRecords(uuid: UUID): List<HomeRecord>? {
        return DatabaseGuard.execute<List<HomeRecord>?>("获取家列表", null) {
            homeRecordMapper.findAll {
                "uuid" eq uuid.toString()
            }
        }
    }

    private fun normalizeHomeName(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "家名称不能为空" }
        require(normalizedName.length <= MAX_HOME_NAME_LENGTH) {
            "家名称长度不能超过 $MAX_HOME_NAME_LENGTH 个字符"
        }
        return normalizedName
    }

    private fun getWorldName(location: Location): String {
        val worldName = requireNotNull(location.world) { "家坐标缺少世界" }.name
        require(worldName.length <= MAX_WORLD_NAME_LENGTH) {
            "世界名称长度不能超过 $MAX_WORLD_NAME_LENGTH 个字符"
        }
        return worldName
    }

    private fun HomeRecord.toHome(): DTPHome {
        return DTPHome(
            name,
            icon,
            Location(resolveWorld(world), x, y, z)
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
