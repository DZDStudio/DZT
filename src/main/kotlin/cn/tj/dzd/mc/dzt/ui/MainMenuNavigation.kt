package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import org.bukkit.entity.Player

/**
 * 功能 UI 返回主菜单的导航端口。
 *
 * 功能 UI 依赖此端口而不直接依赖 Menu 实现，避免主菜单与各功能 UI 之间形成循环依赖。
 */
object MainMenuNavigation {

    @Volatile
    private var opener: (Player) -> Unit = { player -> player.foliaCloseInventory() }

    /**
     * 注册主菜单打开实现。
     *
     * @param handler 接收玩家并打开主菜单的回调。
     */
    fun register(handler: (Player) -> Unit) {
        opener = handler
    }

    /**
     * 为玩家打开主菜单。
     *
     * Menu 尚未注册时会 Folia 兼容地关闭当前界面。
     *
     * @param player 目标玩家。
     */
    fun open(player: Player) {
        player.foliaRun {
            opener(this)
        }
    }
}
