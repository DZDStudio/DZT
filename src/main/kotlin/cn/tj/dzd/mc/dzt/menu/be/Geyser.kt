package cn.tj.dzd.mc.dzt.menu.be

import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import org.geysermc.floodgate.api.player.FloodgatePlayer

fun openGeyserBEMenu(pl: Player, fpl: FloodgatePlayer) {
    fpl.sendForm(SimpleForm.builder()
        .title("§6互通菜单")
        .button("返回上一页", FormImage.Type.PATH, "textures/ui/box_ride.png")
        .button("查看进度", FormImage.Type.PATH, "textures/ui/achievements_pause_menu_icon.png")
        .button("交互主副手", FormImage.Type.PATH, "textures/ui/anvil_icon.png")
        .validResultHandler({
            when (it.clickedButtonId()) {
                0 -> { openMainBEMenu(pl, fpl) }
                1 -> { pl.performCommand("geyser advancements") }
                2 -> { pl.performCommand("geyser offhand") }
            }
        })
    )
}