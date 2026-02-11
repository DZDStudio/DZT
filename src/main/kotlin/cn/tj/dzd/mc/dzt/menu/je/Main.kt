package cn.tj.dzd.mc.dzt.menu.je

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.teleport.openTeleportJEMenu
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

fun openMainJEMenu(dp: DZDPlayer) {
    dp.pl.closeInventory()
    dp.pl.openMenu<Chest>("§l§3D§cZ§3D§5G§ea§1m§ce §6主菜单") {
        rows(5)

        map(
            "####M####",
            "#       #",
            "#   T   #",
            "#       #",
            "#########"
        )

        onClick(lock = true) {}
        set('M', buildItem(XMaterial.YELLOW_STAINED_GLASS_PANE){ name = "§l§3D§cZ§3D§5G§ea§1m§ce §6主菜单" })
        set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE){ name = " " })

        set('T', buildItem(XMaterial.ENDER_PEARL){ name = "§l§6传送菜单" }) { openTeleportJEMenu(dp) }
    }
}