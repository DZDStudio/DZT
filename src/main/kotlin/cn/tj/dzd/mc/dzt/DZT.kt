package cn.tj.dzd.mc.dzt

import cn.tj.dzd.mc.dzt.sidebar.Sidebar
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object DZT : Plugin() {
    override fun onEnable() {
        Sidebar.start()
        info("DZD 基础插件已启用。")
    }
    
    override fun onDisable() {
        Sidebar.stop()
        info("DZD 基础插件正在关闭。")
    }
}
