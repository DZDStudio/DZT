package cn.tj.dzd.mc.dzt.mapping.tables

import cn.tj.dzd.mc.dzt.mapping.DatabaseManager
import cn.tj.dzd.mc.dzt.mapping.dataSource
import cn.tj.dzd.mc.dzt.mapping.host
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table

object SettingMapping {
    val table = Table("setting", host) {
        // 名称
        add("key") {
            type(ColumnTypeSQL.VARCHAR, 64) {
                options(
                    ColumnOptionSQL.PRIMARY_KEY,  // 主键
                )
            }
        }

        // 布尔
        add("bool") {
            type(ColumnTypeSQL.BOOLEAN)
        }

        // 数值
        add("int") {
            type(ColumnTypeSQL.INT, 32)
        }

        // 文本
        add("text") {
            type(ColumnTypeSQL.VARCHAR, 255)
        }
    }

    @Awake(LifeCycle.ENABLE)
    fun initialize() {
        DatabaseManager.checkDatabaseStatus()
        table.createTable(dataSource)
    }

    /**
     * 获取设置
     * @param key 设置名称
     */
    fun getSetting(key: String): Setting? {
        DatabaseManager.checkDatabaseStatus()
        return table.select(dataSource) {
            where("key" eq key)
        }.firstOrNull {
            Setting(
                getString("key"),
                getBoolean("bool"),
                getInt("int"),
                getString("text")
            )
        }
    }
}

data class Setting(
    val key: String,
    val bool: Boolean,
    val int: Int,
    val text: String
)