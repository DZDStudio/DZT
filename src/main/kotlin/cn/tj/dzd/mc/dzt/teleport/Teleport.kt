package cn.tj.dzd.mc.dzt.teleport

import cn.tj.dzd.mc.dzt.Floodgate.getFloodgatePlayer
import cn.tj.dzd.mc.dzt.teleport.be.openBackBEMenu
import cn.tj.dzd.mc.dzt.teleport.be.openHomeBEMenu
import cn.tj.dzd.mc.dzt.teleport.be.openTPABEMenu
import cn.tj.dzd.mc.dzt.teleport.je.openBackJEMenu
import cn.tj.dzd.mc.dzt.teleport.je.openHomeJEMenu
import cn.tj.dzd.mc.dzt.teleport.je.openTPAJEMenu
import org.bukkit.Bukkit.getPlayer
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.simpleCommand
import taboolib.common.util.isPlayer
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import taboolib.platform.util.runTask


@Awake(LifeCycle.ENABLE)
fun registerCommands() {
    simpleCommand("dtp") { sender, _ ->
        if (sender.isPlayer()) {
            getPlayer(sender.name)?.openDTPMenu()
        }
    }
}

/**
 * 打开 DTP 菜单
 */
fun Player.openDTPMenu() {
    this.runTask({
        val fpl = getFloodgatePlayer()
        if (fpl == null) {
            openMenu<Chest>("传送") {
                rows(3)

                set(11, buildItem(XMaterial.PLAYER_HEAD) {
                    name = "&6玩家"
                    colored()
                }) {
                    openTPAJEMenu(this@openDTPMenu)
                }

                set(13, buildItem(XMaterial.BOOK) {
                    name = "&6传送点"
                    colored()
                }) {
                    openHomeJEMenu(this@openDTPMenu)
                }

                set(15, buildItem(XMaterial.RED_BED) {
                    name = "&6死亡点"
                    colored()
                }) {
                    openBackJEMenu(this@openDTPMenu)
                }
            }
        } else {
            fpl.sendForm(SimpleForm.builder()
                .title("传送")
                .button("玩家", FormImage.Type.PATH, "textures/ui/warning_alex.png")
                .button("传送点", FormImage.Type.PATH, "textures/ui/icon_recipe_item.png")
                .button("死亡点", FormImage.Type.PATH, "textures/ui/warning_sad_steve.png")
                .validResultHandler({
                    when (it.clickedButtonId()) {
                        0 -> {
                            openTPABEMenu(this@openDTPMenu)
                        }
                        1 -> {
                            openHomeBEMenu(this@openDTPMenu)
                        }
                        2 -> {
                            openBackBEMenu(this@openDTPMenu)
                        }
                    }
                })
            )
        }
    })
}