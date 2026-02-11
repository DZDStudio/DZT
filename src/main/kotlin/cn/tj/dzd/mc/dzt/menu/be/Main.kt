package cn.tj.dzd.mc.dzt.menu.be

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.teleport.openTeleportBEMenu
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage

fun openMainBEMenu(dp: DZDPlayer) {
    dp.sendForm(SimpleForm.builder()
        .title("§l§3D§cZ§3D§5G§ea§1m§ce §6主菜单")
        .button("传送菜单", FormImage.Type.PATH, "textures/ui/mashup_world.png")
        .button("互通菜单", FormImage.Type.PATH, "textures/ui/classrooms_icon.png")
        .validResultHandler({
            when (it.clickedButtonId()) {
                0 -> { openTeleportBEMenu(dp) }
                1 -> { openGeyserBEMenu(dp) }
            }
        })
    )
}