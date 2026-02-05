package cn.tj.dzd.mc.dzt.menu.be

import cn.tj.dzd.mc.dzt.teleport.openTeleportBEMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import org.geysermc.floodgate.api.player.FloodgatePlayer

fun openMainBEMenu(pl: Player, fpl: FloodgatePlayer) {
    fpl.sendForm(SimpleForm.builder()
        .title("§l§bD§cZ§bD§dG§ea§bm§ce §6主菜单")
        .button("§l§6传送菜单", FormImage.Type.PATH, "textures/ui/mashup_world.png")
        .validResultHandler({
            when (it.clickedButtonId()) {
                0 -> {
                    openTeleportBEMenu(pl, fpl)
                }
            }
        })
    )
}