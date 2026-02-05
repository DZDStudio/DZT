package cn.tj.dzd.mc.dzt.menu.je

import cn.tj.dzd.mc.dzt.teleport.openTeleportJEMenu
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

fun openMainJEMenu(pl: Player) {
    pl.closeInventory()
    pl.openMenu<Chest>("§l§bD§cZ§bD§dG§ea§bm§ce §6主菜单") {
        rows(5)

        map(
            "####M####",
            "#       #",
            "#   T   #",
            "#       #",
            "#########"
        )

        onClick(lock = true) {}
        set('M', buildItem(XMaterial.YELLOW_STAINED_GLASS_PANE){ name = "§l§bD§cZ§bD§dG§ea§bm§ce §6主菜单" })
        set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE){ name = " " })

        set('T', buildItem(XMaterial.ENDER_PEARL){ name = "§l§6传送菜单" }) { openTeleportJEMenu(pl) }
    }
}