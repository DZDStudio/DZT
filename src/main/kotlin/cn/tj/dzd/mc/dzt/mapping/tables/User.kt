package cn.tj.dzd.mc.dzt.mapping.tables

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.mapping.DatabaseManager
import cn.tj.dzd.mc.dzt.mapping.dataSource
import cn.tj.dzd.mc.dzt.mapping.host
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

        // 电子邮箱
        add("email") {
            type(ColumnTypeSQL.VARCHAR, 32)
        }

        // 称号
        add("title") {
            type(ColumnTypeSQL.JSON)
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
        table.insert(dataSource, "name", "title", "reg_time", "money") {
            value(name, "{\"use_title\":\"\",\"titles\":[]}", currentTime, 0)
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

    /**
     * 获取玩家邮箱
     * @param uid 玩家唯一标识符
     */
    fun getEmail(uid: Number): String? {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("email")
            limit(1)
            where("uid" eq uid)
        }.firstOrNull {
            getString("email")
        }
    }

    /**
     * 设置玩家邮箱
     * @param uid 玩家唯一标识符
     */
    fun setEmail(uid: Number, email: String) {
        DatabaseManager.checkDatabaseStatus()
        table.update(dataSource) {
            set("email", email)
            where("uid" eq uid)
        }
    }

    /**
     * 获取玩家余额
     * @param uid 玩家唯一标识符
     */
    fun getMoney(uid: Number): Int? {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            rows("money")
            limit(1)
            where("uid" eq uid)
        }.firstOrNull {
            getInt("money")
        }
    }

    /**
     * 设置玩家余额
     * @param uid 玩家唯一标识符
     * @param money 金额
     */
    fun setMoney(uid: Number, money: Int) {
        DatabaseManager.checkDatabaseStatus()
        table.update(dataSource) {
            set("money", money)
            where("uid" eq uid)
        }
    }

    /**
     * 玩家转账
     * @param from 转账玩家
     * @param to 收款玩家
     * @param money 金额
     */
    fun pay(from: Number, to: Number, money: Int): Boolean {
        val result = table.transaction(dataSource) {
            // 检查转出方余额是否足够
            val balance = select {
                rows("money")
                where("uid" eq from)
            }.firstOrNull {
                getInt("money")
            } ?: 0

            if (balance < money) {
                error("余额不足")  // 抛出异常，触发回滚
            }

            update {
                set("money", balance - money)
                where("uid" eq from)
            }

            update {
                set("money", balance + money)
                where("uid" eq to)
            }
        }

        return result.isSuccess
    }
}

/**
 * 获取昵称
 */
fun DZDPlayer.getNickName(): String {
    return UserMapping.getNickName(uid) ?: pl.name
}

/**
 * 获取电子邮箱
 */
fun DZDPlayer.getEmail(): String? {
    return UserMapping.getEmail(uid)
}

/**
 * 设置邮箱
 * @param email 邮箱
 */
fun DZDPlayer.setEmail(email: String) {
    UserMapping.setEmail(uid, email)
}