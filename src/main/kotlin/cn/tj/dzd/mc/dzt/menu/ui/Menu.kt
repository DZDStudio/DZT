package cn.tj.dzd.mc.dzt.menu.ui

import cn.tj.dzd.mc.dzt.teleport.ui.Teleport.openTeleport
import cn.tj.dzd.mc.dzt.util.TextLogo
import cn.tj.dzd.mc.dzt.util.foliaPerformCommand
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.common.function.throttle
import taboolib.common.platform.event.SubscribeEvent
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import java.util.UUID

object Menu {

    private const val OPEN_MENU_THROTTLE_DELAY = 500L

    private val openMainMenu = throttle<UUID, Player>(OPEN_MENU_THROTTLE_DELAY) { _, player ->
        player.openMenuDirectly()
    }

    /**
     * 打开主菜单。
     *
     * 同一玩家在 [OPEN_MENU_THROTTLE_DELAY] 毫秒内重复调用时，仅第一次调用会真正打开菜单。
     */
    fun Player.openMenu() {
        openMainMenu(uniqueId, this)
    }

    private fun Player.openMenuDirectly() {
        if (isBePlayer())
            be(this)
        else
            je(this)
    }

    /**
     * 玩家离线时清理主菜单节流记录，避免 UUID 记录长期滞留。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        openMainMenu.removeKey(event.player.uniqueId)
    }

    fun je(pl: Player) {
        pl.openMenu<Chest>(TextLogo) {
            rows(5)

            virtualize()

            map(
                "####I####",
                "#       #",
                "#   T   #",
                "#       #",
                "#########"
            )
            set('#', buildItem(XMaterial.LIGHT_GRAY_STAINED_GLASS_PANE) {
                name = " "
            })

            set('I', buildItem(XMaterial.SAND) {
                name = TextLogo
                lore += "主菜单"
            })

            set('T', buildItem(XMaterial.RED_BED) {
                name = "传送"
                lore += "传送菜单"
            }) {
                pl.openTeleport()
            }
        }
    }
    fun be(pl: Player) {
        val fm = SimpleForm.builder()
            .title(TextLogo)
            .button("传送", FormImage.Type.PATH, "textures/ui/csb_purchase_warning.png")
            .button("成就", FormImage.Type.PATH, "textures/ui/achievements_pause_menu_icon.png")
            .validResultHandler { res ->
                val id = res.clickedButtonId()

                when (id) {
                    0 -> pl.openTeleport()
                    1 -> pl.foliaPerformCommand("geyser advancements")
                }
            }
        pl.sendForm(fm)
    }
}
