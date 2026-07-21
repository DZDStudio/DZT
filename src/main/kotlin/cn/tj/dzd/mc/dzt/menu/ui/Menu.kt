package cn.tj.dzd.mc.dzt.menu.ui

import cn.tj.dzd.mc.dzt.teleport.ui.Teleport.openTeleport
import cn.tj.dzd.mc.dzt.commission.ui.CommissionUI
import cn.tj.dzd.mc.dzt.economy.ui.TransferUI
import cn.tj.dzd.mc.dzt.shop.ui.ShopUI
import cn.tj.dzd.mc.dzt.title.ui.TitleUI
import cn.tj.dzd.mc.dzt.ui.MainMenuNavigation
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
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
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
     * 向功能 UI 注册主菜单导航实现。
     */
    @Awake(LifeCycle.ACTIVE)
    fun registerNavigation() {
        MainMenuNavigation.register { player ->
            with(Menu) {
                player.openMenu()
            }
        }
    }

    /**
     * 打开主菜单。
     *
     * 同一玩家在 [OPEN_MENU_THROTTLE_DELAY] 毫秒内重复调用时，仅第一次调用会真正打开菜单。
     */
    fun Player.openMenu() {
        foliaRun {
            openMainMenu(uniqueId, this)
        }
    }

    private fun Player.openMenuDirectly() {
        if (isBePlayer()) {
            openBedrockMenu(this)
        } else {
            openJavaMenu(this)
        }
    }

    /**
     * 玩家离线时清理主菜单节流记录，避免 UUID 记录长期滞留。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        openMainMenu.removeKey(event.player.uniqueId)
    }

    fun je(pl: Player) {
        pl.foliaRun {
            openJavaMenu(this)
        }
    }

    private fun openJavaMenu(pl: Player) {
        pl.openMenu<Chest>(TextLogo) {
            rows(6)

            virtualize()

            map(
                "####I####",
                "#       #",
                "# T E C #",
                "# H Q S #",
                "#       #",
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

            set('C', buildItem(XMaterial.NAME_TAG) {
                name = "称号"
                lore += "选择已拥有的称号"
            }) {
                TitleUI.open(pl)
            }

            set('H', buildItem(XMaterial.CHEST) {
                name = "商店"
                lore += "购买常用资源"
            }) {
                ShopUI.open(pl)
            }

            set('Q', buildItem(XMaterial.WRITABLE_BOOK) {
                name = "每日委托"
                lore += "完成委托领取弟弟币"
            }) {
                CommissionUI.open(pl)
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
        pl.foliaRun {
            openBedrockMenu(this)
        }
    }

    private fun openBedrockMenu(pl: Player) {
        val fm = SimpleForm.builder()
            .title(TextLogo)
            .button("传送", FormImage.Type.PATH, "textures/ui/csb_purchase_warning.png")
            .button("转账", FormImage.Type.PATH, "textures/items/emerald.png")
            .button("称号", FormImage.Type.PATH, "textures/items/name_tag.png")
            .button("商店", FormImage.Type.PATH, "textures/blocks/chest_front.png")
            .button("每日委托", FormImage.Type.PATH, "textures/items/book_normal.png")
            .button("成就", FormImage.Type.PATH, "textures/ui/achievements_pause_menu_icon.png")
            .button("自杀", FormImage.Type.PATH, "textures/ui/warning_sad_steve.png")
            .validResultHandler { res ->
                val id = res.clickedButtonId()
                pl.foliaRun {
                    when (id) {
                        0 -> openTeleport()
                        1 -> TransferUI.openTransferUI(this)
                        2 -> TitleUI.open(this)
                        3 -> ShopUI.open(this)
                        4 -> CommissionUI.open(this)
                        5 -> foliaPerformCommand("geyser advancements")
                        6 -> openBedrockSuicideConfirmation(this)
                    }
                }
            }
        pl.sendForm(fm)
    }

    private fun openJavaSuicideConfirmation(player: Player) {
        player.foliaRun {
            openJavaSuicideConfirmationOnPlayerThread(this)
        }
    }

    private fun openJavaSuicideConfirmationOnPlayerThread(player: Player) {
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
        player.foliaRun {
            sendForm(
                ModalForm.builder()
                    .title("§l§c确认自杀")
                    .content("确认结束当前生命？")
                    .button1("确认自杀")
                    .button2("取消")
                    .validResultHandler { response ->
                        val confirmed = response.clickedButtonId() == 0
                        player.foliaRun {
                            if (confirmed) {
                                suicide()
                            } else {
                                openMenu()
                            }
                        }
                    }
            )
        }
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
