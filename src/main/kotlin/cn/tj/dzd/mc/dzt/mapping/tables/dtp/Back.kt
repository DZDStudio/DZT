package cn.tj.dzd.mc.dzt.mapping.tables.dtp

import cn.tj.dzd.mc.dzt.mapping.DatabaseManager
import cn.tj.dzd.mc.dzt.mapping.tables.UserMapping
import cn.tj.dzd.mc.dzt.mapping.dataSource
import cn.tj.dzd.mc.dzt.mapping.getUID
import cn.tj.dzd.mc.dzt.mapping.host
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DTPBackMapping {
    val table = Table("dtp_back", host) {
        // 用户 ID
        add("uid") {
            type(ColumnTypeSQL.INT, 5) {
                options(
                    ColumnOptionSQL.UNSIGNED,  // 无符号（只存储非负数）
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // 时间
        add("time") {
            type(ColumnTypeSQL.TIMESTAMP) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // 世界
        add("world") {
            type(ColumnTypeSQL.VARCHAR, 16) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // X 坐标
        add("x") {
            type(ColumnTypeSQL.INT, 8) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // Y 坐标
        add("y") {
            type(ColumnTypeSQL.INT, 8) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // Z 坐标
        add("z") {
            type(ColumnTypeSQL.INT, 8) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        index(
            name = "idx_uid_time",
            columns = listOf("uid", "time"),
            unique = true,
            checkExists = true
        )
    }

    @Awake(LifeCycle.ENABLE)
    fun initialize() {
        DatabaseManager.checkDatabaseStatus()
        table.createTable(dataSource)
    }

    /**
     * 添加玩家返回点
     * @param uid 用户 ID
     * @param world 世界
     * @param x X 坐标
     * @param y Y 坐标
     * @param z Z 坐标
     */
    fun addBack(uid: Number, world: String, x: Double, y: Double, z: Double) {
        DatabaseManager.checkDatabaseStatus()
        val currentTime = Instant.ofEpochMilli(System.currentTimeMillis())
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        table.insert(dataSource, "uid", "time", "world", "x", "y", "z") {
            value(uid, currentTime, world, x.toInt(), y.toInt(), z.toInt())
        }
    }

    /**
     * 获取玩家返回点
     * @param uid 用户 ID
     * @return 玩家返回点
     * @return List<DTPBack> 玩家返回点列表
     */
    fun getBack(uid: Number): List<DTPBack> {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("time", "world", "x", "y", "z")
            where("uid" eq uid)
        }.map {
            DTPBack(
                getString("time"),
                Location(
                    Bukkit.getWorld(getString("world")),
                    getInt("x").toDouble(),
                    getInt("y").toDouble(),
                    getInt("z").toDouble()
                )
            )
        }
    }

    /**
     * 删除玩家返回点
     * @param id 返回点 ID
     */
    fun deleteBack(uid: Number, time: String) {
        DatabaseManager.checkDatabaseStatus()
        table.delete(dataSource) {
            where("uid" eq uid)
            where("time" eq time)
        }
    }
}

class DTPBack {
    val time: String
    val location: Location

    constructor(time: String, location: Location) {
        this.time = time
        this.location = location
    }
}

/**
 * 获取玩家的死亡点列表
 */
fun Player.getDTPBackList(): List<DTPBack> {
    return DTPBackMapping.getBack(getUID()).reversed()
}

/**
 * 删除指定的死亡点
 */
fun Player.deleteDTPBack(time: String) {
    DTPBackMapping.deleteBack(getUID(), time)
}

private object BackMappingListener {
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val pl = event.getEntity()
        val location = pl.location
        UserMapping.getUID(pl.name)?.let { location.world?.let { it1 -> DTPBackMapping.addBack(it, it1.name, location.x, location.y, location.z) } }
    }
}