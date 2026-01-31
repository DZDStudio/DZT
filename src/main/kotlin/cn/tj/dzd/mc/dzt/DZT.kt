package cn.tj.dzd.mc.dzt

import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object DZT : Plugin() {
    override fun onEnable() {
        info("DZD 基础插件已启用。")
    }
    
    override fun onDisable() {
        info("DZD 基础插件正在关闭。")
    }
}