package cn.tj.dzd.mc.dzt.mapping.tables.dtp

import cn.tj.dzd.mc.dzt.mapping.DatabaseManager
import cn.tj.dzd.mc.dzt.mapping.dataSource
import cn.tj.dzd.mc.dzt.mapping.getUID
import cn.tj.dzd.mc.dzt.mapping.host
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table

object DTPHomeMapping {
    val table = Table("dtp_home", host) {
        // 用户 ID
        add("uid") {
            type(ColumnTypeSQL.INT, 5) {
                options(
                    ColumnOptionSQL.UNSIGNED,  // 无符号（只存储非负数）
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // 名称
        add("name") {
            type(ColumnTypeSQL.VARCHAR, 16) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // 世界
        add("world") {
            type(ColumnTypeSQL.VARCHAR, 8) {
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
            name = "idx_uid_name",
            columns = listOf("uid", "name"),
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
     * 添加家
     * @param uid 唯一标识符
     * @param name 名称
     * @param location 坐标
     */
    fun addHome(uid: Number, name: String, location: Location) {
        DatabaseManager.checkDatabaseStatus()
        // 判断是否过长或者过短
        if (name.length !in 1..8) {
            throw Exception("名称长度必须在 1 到 8 个字符之间")
        }

        // 判断是否有重名
        if (getHome(uid).any { it.name == name }) {
            throw Exception("名称重复")
        }

        table.insert(dataSource, "uid", "name", "world", "x", "y", "z") {
            value(uid, name, location.world?.name, location.x.toInt(), location.y.toInt(), location.z.toInt())
        }
    }

    /**
     * 获取家列表
     * @param uid 唯一标识符
     * @return 家
     */
    fun getHome(uid: Number): List<DTPHome> {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("name", "world", "x", "y", "z")
            where("uid" eq uid)
        }.map {
            DTPHome(
                getString("name"),
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
     * 删除家
     * @param uid 玩家唯一标识符
     * @param name 家名称
     */
    fun deleteHome(uid: Number, name: String) {
        DatabaseManager.checkDatabaseStatus()
        table.delete(dataSource) {
            where("uid" eq uid)
            where("name" eq name)
        }
    }
}

class DTPHome {
    val name: String
    val location: Location

    constructor(name: String, location: Location) {
        this.name = name
        this.location = location
    }
}

/**
 * 添加家
 * @param name 家名称
 */
fun Player.addDTPHome(name: String, location: Location) {
    DTPHomeMapping.addHome(getUID(), name, location)
}

/**
 * 获取家列表
 */
fun Player.getDTPHomeList(): List<DTPHome> {
    return DTPHomeMapping.getHome(getUID())
}

/**
 * 删除指定的家
 */
fun Player.deleteDTPHome(name: String) {
    DTPHomeMapping.deleteHome(getUID(), name)
}