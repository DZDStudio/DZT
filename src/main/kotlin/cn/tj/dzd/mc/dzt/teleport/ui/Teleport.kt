package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.menu.ui.Menu.openMenu
import cn.tj.dzd.mc.dzt.util.TextLogo
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

object Teleport {

    /**
     * 打开传送主菜单。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     */
    fun Player.openTeleport() {
        if (isBePlayer()) {
            openBedrock(this)
        } else {
            foliaRun {
                openJava(this)
            }
        }
    }

    private fun openBedrock(player: Player) {
        player.sendForm(
            SimpleForm.builder()
                .title("§l§6传送菜单")
                .button("返回主菜单", FormImage.Type.PATH, "textures/ui/box_ride.png")
                .button("玩家", FormImage.Type.PATH, "textures/ui/warning_alex.png")
                .button("传送点", FormImage.Type.PATH, "textures/ui/icon_recipe_item.png")
                .button("死亡点", FormImage.Type.PATH, "textures/ui/warning_sad_steve.png")
                .button("回床", FormImage.Type.PATH, "textures/items/bed_red.png")
                .validResultHandler { response ->
                    when (response.clickedButtonId()) {
                        0 -> player.openMenu()
                        1 -> TPA.openTPAUI(player)
                        2 -> Home.openHomeUI(player)
                        3 -> Back.openBackUI(player)
                        4 -> Bed.teleportBedUI(player)
                    }
                }
        )
    }

    private fun openJava(player: Player) {
        player.openMenu<Chest>(TextLogo) {
            rows(5)
            virtualize()

            map(
                "R###M####",
                "#       #",
                "# P H B #",
                "#   S   #",
                "#########"
            )

            onClick(lock = true) {}
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " })
            set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回主菜单" }) {
                player.openMenu()
            }
            set('M', buildItem(XMaterial.ENDER_PEARL) { name = "§l§6传送菜单" })

            set('P', buildItem(XMaterial.PLAYER_HEAD) {
                name = "§l§6玩家"
                lore += "§7向在线玩家发起传送请求"
            }) {
                TPA.openTPAUI(player)
            }

            set('H', buildItem(XMaterial.BOOK) {
                name = "§l§6传送点"
                lore += "§7管理并前往自己的家"
            }) {
                Home.openHomeUI(player)
            }

            set('B', buildItem(XMaterial.RECOVERY_COMPASS) {
                name = "§l§6死亡点"
                lore += "§7前往最近记录的死亡地点"
            }) {
                Back.openBackUI(player)
            }

            set('S', buildItem(XMaterial.RED_BED) {
                name = "§l§6回床"
                lore += "§7回到当前有效复活点"
            }) {
                Bed.teleportBedUI(player)
            }
        }
    }
}
