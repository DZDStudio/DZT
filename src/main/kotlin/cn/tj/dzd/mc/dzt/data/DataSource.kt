package cn.tj.dzd.mc.dzt.data

import taboolib.expansion.db
import taboolib.module.configuration.Config
import taboolib.module.configuration.Configuration

@Config(value = "config.yml", autoReload = true)
lateinit var config: Configuration

/**
 * 数据库配置源。
 *
 * 读取 config.yml 的 database 节点；当 database.enable 为 false 时使用本地 SQLite 文件。
 */
val DataSource: Any
    get() {
        check(::config.isInitialized) { "config.yml 尚未加载，无法创建数据库连接。" }
        return db("config.yml", "database", "data.db")
    }
