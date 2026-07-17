package cn.tj.dzd.mc.dzt.menu.ui

import cn.tj.dzd.mc.dzt.teleport.ui.Teleport.openTeleport
import cn.tj.dzd.mc.dzt.economy.ui.TransferUI
import cn.tj.dzd.mc.dzt.util.TextLogo
import cn.tj.dzd.mc.dzt.util.foliaPerformCommand
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerQuitEvent
import org.geysermc.cumulus.form.ModalForm
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
                "#  T E  #",
                "#   S   #",
                "#########"
            )
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) {
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

            set('E', buildItem(XMaterial.EMERALD) {
                name = "转账"
                lore += "向在线玩家转账"
            }) {
                TransferUI.openTransferUI(pl)
            }

            set('S', buildItem(XMaterial.WITHER_SKELETON_SKULL) {
                name = "§l§c自杀"
                lore += "§7结束当前生命"
            }) {
                openJavaSuicideConfirmation(pl)
            }
        }
    }

    fun be(pl: Player) {
        val fm = SimpleForm.builder()
            .title(TextLogo)
            .button("传送", FormImage.Type.PATH, "textures/ui/csb_purchase_warning.png")
            .button("转账", FormImage.Type.PATH, "textures/items/emerald.png")
            .button("成就", FormImage.Type.PATH, "textures/ui/achievements_pause_menu_icon.png")
            .button("自杀", FormImage.Type.PATH, "textures/ui/warning_sad_steve.png")
            .validResultHandler { res ->
                val id = res.clickedButtonId()

                when (id) {
                    0 -> pl.openTeleport()
                    1 -> TransferUI.openTransferUI(pl)
                    2 -> pl.foliaPerformCommand("geyser advancements")
                    3 -> openBedrockSuicideConfirmation(pl)
                }
            }
        pl.sendForm(fm)
    }

    private fun openJavaSuicideConfirmation(player: Player) {
        player.openMenu<Chest>("§l§c确认自杀") {
            rows(3)
            virtualize()

            map(
                "#########",
                "#  Y N  #",
                "#########"
            )

            onClick(lock = true) {}
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " })
            set('Y', buildItem(XMaterial.REDSTONE_BLOCK) {
                name = "§l§c确认自杀"
                lore += "§7点击后立即死亡"
            }) {
                player.suicide()
            }
            set('N', buildItem(XMaterial.LIME_WOOL) {
                name = "§l§a取消"
                lore += "§7返回主菜单"
            }) {
                player.openMenu()
            }
        }
    }

    private fun openBedrockSuicideConfirmation(player: Player) {
        player.sendForm(
            ModalForm.builder()
                .title("§l§c确认自杀")
                .content("确认结束当前生命？")
                .button1("确认自杀")
                .button2("取消")
                .validResultHandler { response ->
                    if (response.clickedButtonId() == 0) {
                        player.suicide()
                    } else {
                        player.openMenu()
                    }
                }
        )
    }

    /**
     * 在玩家所属实体线程结束玩家当前生命。
     */
    private fun Player.suicide() {
        foliaRun {
            if (!isDead) {
                health = 0.0
            }
        }
    }
}
