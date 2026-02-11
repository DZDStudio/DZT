package cn.tj.dzd.mc.dzt.teleport

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.menu.be.openMainBEMenu
import cn.tj.dzd.mc.dzt.menu.je.openMainJEMenu
import cn.tj.dzd.mc.dzt.teleport.be.openBackBEMenu
import cn.tj.dzd.mc.dzt.teleport.be.openHomeBEMenu
import cn.tj.dzd.mc.dzt.teleport.be.openTPABEMenu
import cn.tj.dzd.mc.dzt.teleport.je.openBackJEMenu
import cn.tj.dzd.mc.dzt.teleport.je.openHomeJEMenu
import cn.tj.dzd.mc.dzt.teleport.je.openTPAJEMenu
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

fun openTeleportJEMenu(dp: DZDPlayer) {
    dp.pl.openMenu<Chest>("§l§6传送菜单") {
        rows(5)

        map(
            "R###M####",
            "#       #",
            "# P H B #",
            "#       #",
            "#########"
        )

        onClick(lock = true) {}
        set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
        set('M', XMaterial.YELLOW_STAINED_GLASS_PANE) { name = "§l§6传送菜单" }
        set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回上一页" }) { openMainJEMenu(dp) }

        set('P', buildItem(XMaterial.PLAYER_HEAD) { name = "§l§6玩家" }) { openTPAJEMenu(dp) }
        set('H', buildItem(XMaterial.BOOK) { name = "§l§6传送点" }) { openHomeJEMenu(dp) }
        set('B', buildItem(XMaterial.RED_BED) { name = "§l§6死亡点" }) { openBackJEMenu(dp) }
    }
}

fun openTeleportBEMenu(dp: DZDPlayer) {
    dp.sendForm(SimpleForm.builder()
        .title("§l§6传送菜单")
        .button("返回上一页", FormImage.Type.PATH, "textures/ui/box_ride.png")
        .button("玩家", FormImage.Type.PATH, "textures/ui/warning_alex.png")
        .button("传送点", FormImage.Type.PATH, "textures/ui/icon_recipe_item.png")
        .button("死亡点", FormImage.Type.PATH, "textures/ui/warning_sad_steve.png")
        .validResultHandler({
            when (it.clickedButtonId()) {
                0 -> { openMainBEMenu(dp) }
                1 -> { openTPABEMenu(dp) }
                2 -> { openHomeBEMenu(dp) }
                3 -> { openBackBEMenu(dp) }
            }
        })
    )
}