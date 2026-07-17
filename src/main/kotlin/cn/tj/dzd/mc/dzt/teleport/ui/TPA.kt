package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectUI
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.ModalForm
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem

object TPA {
    /**
     * 打开玩家传送请求 UI。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     */
    fun openTPAUI(player: Player) {
        OnlinePlayerSelectUI.open(
            player = player,
            title = "§l§6玩家传送",
            description = "请选择要传送到的玩家。",
            selectLore = "§7点击发送传送请求",
            backLabel = "§l§e返回传送菜单",
            onBack = {
                Teleport.run { openTeleport() }
            },
            onSelect = { target ->
                sendRequest(this, target)
            }
        )
    }

    private fun sendRequest(requester: Player, target: Player) {
        if (!requester.isOnline || !target.isOnline) {
            requester.sendTeleportError("玩家已离线。")
            return
        }

        requester.sendTeleportSuccess("已向 ${target.name} 发送传送请求。")
        target.foliaRun {
            if (isBePlayer()) {
                openBedrockConfirm(requester, this)
            } else {
                openJavaConfirm(requester, this)
            }
        }
    }

    private fun openJavaConfirm(requester: Player, target: Player) {
        var handled = false
        target.openMenu<Chest>("§l§6请求传送") {
            rows(3)
            virtualize()

            map(
                "####M####",
                "#  Y N  #",
                "#########"
            )

            onClick(lock = true) {}
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " })
            set('M', buildItem(XMaterial.PLAYER_HEAD) {
                name = "§6${requester.name} §f请求传送至您的位置"
                skullOwner = requester.name
            })
            set('Y', buildItem(XMaterial.DIAMOND) {
                name = "§a同意"
                lore += "§7允许 ${requester.name} 传送到您身边"
            }) {
                if (handled) {
                    target.foliaCloseInventory()
                    return@set
                }
                handled = true
                acceptRequest(requester, target)
                target.foliaCloseInventory()
            }
            set('N', buildItem(XMaterial.REDSTONE) {
                name = "§c拒绝"
                lore += "§7拒绝这次传送请求"
            }) {
                if (handled) {
                    target.foliaCloseInventory()
                    return@set
                }
                handled = true
                rejectRequest(requester, target)
                target.foliaCloseInventory()
            }
            onClose {
                if (!handled) {
                    handled = true
                    rejectRequest(requester, target)
                }
            }
        }
    }

    private fun openBedrockConfirm(requester: Player, target: Player) {
        target.sendForm(
            ModalForm.builder()
                .title("§l§6请求传送")
                .content("§6${requester.name} §f请求传送至您的位置。")
                .button1("同意")
                .button2("拒绝")
                .validResultHandler { response ->
                    if (response.clickedButtonId() == 0) {
                        acceptRequest(requester, target)
                    } else {
                        rejectRequest(requester, target)
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    rejectRequest(requester, target)
                })
        )
    }

    private fun acceptRequest(requester: Player, target: Player) {
        requester.foliaTeleport(target).thenAccept { success ->
            if (success) {
                requester.sendTeleportSuccess("${target.name} 同意了您的传送请求。")
                target.sendTeleportSuccess("已同意 ${requester.name} 的传送请求。")
            } else {
                requester.sendTeleportError("传送失败，目标玩家可能已离线。")
                target.sendTeleportError("传送失败，${requester.name} 可能已离线。")
            }
        }
    }

    private fun rejectRequest(requester: Player, target: Player) {
        requester.sendTeleportError("${target.name} 拒绝了您的传送请求。")
        target.sendTeleportError("已拒绝 ${requester.name} 的传送请求。")
    }
}
