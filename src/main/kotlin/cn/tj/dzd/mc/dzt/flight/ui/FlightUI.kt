package cn.tj.dzd.mc.dzt.flight.ui

import cn.tj.dzd.mc.dzt.flight.FlightService
import cn.tj.dzd.mc.dzt.flight.FlightSettingState
import cn.tj.dzd.mc.dzt.flight.FlightToggleResult
import cn.tj.dzd.mc.dzt.ui.MainMenuNavigation
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendDZTTip
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

/** Java 版箱子菜单与基岩版表单共用的飞行设置界面。 */
object FlightUI {

    private const val BEDROCK_FLIGHT_ICON = "textures/items/elytra.png"
    private const val BEDROCK_DISABLE_ICON = "textures/ui/cancel.png"
    private const val BEDROCK_BACK_ICON = "textures/ui/box_ride.png"

    /**
     * 异步读取玩家飞行设置，并按客户端类型打开对应界面。
     *
     * @param player 需要打开飞行设置的在线玩家。
     */
    fun open(player: Player) {
        player.foliaRun {
            loadSetting(this, isBePlayer())
        }
    }

    private fun loadSetting(player: Player, bedrockPlayer: Boolean) {
        FlightService.getSetting(player).whenComplete { state, error ->
            player.foliaRun {
                if (error != null || state !is FlightSettingState.Available) {
                    sendDZTError("读取飞行设置失败。")
                    MainMenuNavigation.open(this)
                    return@foliaRun
                }

                if (bedrockPlayer) {
                    openBedrock(this, state.enabled)
                } else {
                    openJava(this, state.enabled)
                }
            }
        }
    }

    private fun openJava(player: Player, enabled: Boolean) {
        player.openMenu<Chest>("§l§b飞行设置") {
            rows(3)
            virtualize()

            map(
                "B###I####",
                "#   T   #",
                "#########",
            )

            onClick(lock = true) {}
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " })
            set('I', buildItem(XMaterial.ELYTRA) {
                name = if (enabled) "§l§a飞行已开启" else "§l§c飞行已关闭"
                lore += "§7每 5 秒扣除 §61 弟弟币"
            })
            set('T', buildItem(if (enabled) XMaterial.RED_CONCRETE else XMaterial.LIME_CONCRETE) {
                name = if (enabled) "§l§c关闭飞行" else "§l§a开启飞行"
                lore += "§e点击切换"
            }) {
                toggle(player)
            }
            set('B', buildItem(XMaterial.BARREL) {
                name = "§l§e返回主菜单"
            }) {
                MainMenuNavigation.open(player)
            }
        }
    }

    private fun openBedrock(player: Player, enabled: Boolean) {
        val status = if (enabled) "§a开启" else "§c关闭"
        val toggleText = if (enabled) "关闭飞行" else "开启飞行"
        val toggleIcon = if (enabled) BEDROCK_DISABLE_ICON else BEDROCK_FLIGHT_ICON

        player.sendForm(
            SimpleForm.builder()
                .title("§l§b飞行设置")
                .content(
                    "§7当前状态: $status\n" +
                        "§7每 5 秒扣除 §61 弟弟币§7。\n"
                )
                .button(toggleText, FormImage.Type.PATH, toggleIcon)
                .button("返回主菜单", FormImage.Type.PATH, BEDROCK_BACK_ICON)
                .validResultHandler { response ->
                    player.foliaRun {
                        when (response.clickedButtonId()) {
                            0 -> toggle(this)
                            1 -> MainMenuNavigation.open(this)
                        }
                    }
                }
        )
    }

    private fun toggle(player: Player) {
        player.foliaRun {
            foliaCloseInventory()
            FlightService.toggle(this).whenComplete { result, error ->
                player.foliaRun resultHandler@{
                    if (error != null) {
                        sendDZTError("更改飞行设置失败。")
                        open(this)
                        return@resultHandler
                    }

                    when (result) {
                        FlightToggleResult.ENABLED -> sendDZTSuccess("飞行功能已开启。")
                        FlightToggleResult.DISABLED -> sendDZTSuccess("飞行功能已关闭。")
                        FlightToggleResult.IN_PROGRESS -> sendDZTTip("飞行设置正在处理中，请稍候。")
                        FlightToggleResult.FAILED,
                        null,
                        -> sendDZTError("更改飞行设置失败。")
                    }
                    open(this)
                }
            }
        }
    }
}
