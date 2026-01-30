package cn.tj.dzd.mc.dzt

import cn.tj.dzd.mc.dzt.mapping.DatabaseManager
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object DZT : Plugin() {
    override fun onEnable() {
        info("DZD 基础插件已启用。")
        
        // 检查数据库状态，如果数据库不在线则会自动关闭服务器
        if (DatabaseManager.checkDatabaseStatus()) {
            info("数据库连接正常。")
        }
    }
    
    override fun onDisable() {
        info("DZD 基础插件正在关闭。")
        DatabaseManager.shutdown()
    }
}