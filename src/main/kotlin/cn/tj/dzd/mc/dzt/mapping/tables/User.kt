package cn.tj.dzd.mc.dzt.mapping.tables

import cn.tj.dzd.mc.dzt.mapping.DatabaseManager
import cn.tj.dzd.mc.dzt.mapping.dataSource
import cn.tj.dzd.mc.dzt.mapping.getUID
import cn.tj.dzd.mc.dzt.mapping.host
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object UserMapping {
    val table = Table("user", host) {
        // 用户 ID
        add("uid") {
            type(ColumnTypeSQL.INT, 5) {
                options(
                    ColumnOptionSQL.UNSIGNED,  // 无符号（只存储非负数）
                    ColumnOptionSQL.AUTO_INCREMENT,  // 自动递增
                    ColumnOptionSQL.PRIMARY_KEY,  // 主键
                )
            }
        }

        // 用户名-游戏名
        add("name") {
            type(ColumnTypeSQL.VARCHAR, 16) {
                options(
                    ColumnOptionSQL.UNIQUE_KEY,  // 唯一索引
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // 昵称
        add("nickname") {
            type(ColumnTypeSQL.VARCHAR, 16) {
                options(
                    ColumnOptionSQL.UNIQUE_KEY,  // 唯一索引
                )
            }
        }

        // 注册时间
        add("reg_time") {
            type(ColumnTypeSQL.TIMESTAMP) {
                options(
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }

        // 余额
        add("money") {
            type(ColumnTypeSQL.INT, 12) {
                options(
                    ColumnOptionSQL.UNSIGNED,  // 无符号（只存储非负数）
                    ColumnOptionSQL.NOTNULL,  // 不能为空
                )
            }
        }
    }

    @Awake(LifeCycle.ENABLE)
    fun initialize() {
        DatabaseManager.checkDatabaseStatus()
        table.createTable(dataSource)
    }

    /**
     * 添加用户
     * @param name 玩家名称
     */
    fun addUser(name: String) {
        DatabaseManager.checkDatabaseStatus()
        if (table.select(dataSource) {
                rows("uid")
                limit(1)
                where("name" eq name)
        }.firstOrNull {
                getInt("uid")
        } != null) {
            return
        }

        val currentTime = Instant.ofEpochMilli(System.currentTimeMillis())
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        table.insert(dataSource, "name", "reg_time", "money") {
            value(name, currentTime, 0)
        }
    }

    /**
     * 获取玩家唯一标识符
     * @param name 玩家名称
     */
    fun getUID(name: String): Number? {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("uid")
            limit(1)
            where("name" eq name)
        }.firstOrNull {
            getInt("uid")
        }
    }

    /**
     * 获取游戏名称
     * @param uid 玩家唯一标识符
     */
    fun getName(uid: Number): String? {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("name")
            limit(1)
            where("uid" eq uid)
        }.firstOrNull {
            getString("name")
        }
    }

    /**
     * 获取玩家昵称
     * @param uid 玩家唯一标识符
     */
    fun getNickName(uid: Number): String? {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("nickname")
            limit(1)
            where("uid" eq uid)
        }.firstOrNull {
            getString("nickname")
        }
    }
}

/**
 * 获取玩家昵称
 */
fun Player.getNickName(): String? {
    return UserMapping.getNickName(getUID())
}