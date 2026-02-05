package cn.tj.dzd.mc.dzt.menu.be

import cn.tj.dzd.mc.dzt.teleport.openTeleportBEMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import org.geysermc.floodgate.api.player.FloodgatePlayer

fun openMainBEMenu(pl: Player, fpl: FloodgatePlayer) {
    fpl.sendForm(SimpleForm.builder()
        .title("§l§bD§cZ§bD§dG§ea§bm§ce §6主菜单")
        .button("传送菜单", FormImage.Type.PATH, "textures/ui/mashup_world.png")
        .button("互通菜单", FormImage.Type.PATH, "textures/ui/classrooms_icon.png")
        .validResultHandler({
            when (it.clickedButtonId()) {
                0 -> { openTeleportBEMenu(pl, fpl) }
                1 -> { openGeyserBEMenu(pl, fpl) }
            }
        })
    )
}