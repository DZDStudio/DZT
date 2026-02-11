package cn.tj.dzd.mc.dzt.menu.be

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage

fun openGeyserBEMenu(dp: DZDPlayer) {
    dp.sendForm(SimpleForm.builder()
        .title("§6互通菜单")
        .button("返回上一页", FormImage.Type.PATH, "textures/ui/box_ride.png")
        .button("查看进度", FormImage.Type.PATH, "textures/ui/achievements_pause_menu_icon.png")
        .button("交互主副手", FormImage.Type.PATH, "textures/ui/anvil_icon.png")
        .validResultHandler({
            when (it.clickedButtonId()) {
                0 -> { openMainBEMenu(dp) }
                1 -> { dp.exCommand("geyser advancements") }
                2 -> { dp.exCommand("geyser offhand") }
            }
        })
    )
}