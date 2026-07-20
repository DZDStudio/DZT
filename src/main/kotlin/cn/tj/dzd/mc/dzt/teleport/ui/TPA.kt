package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelection
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectUI
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
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

    private fun sendRequest(requester: Player, target: OnlinePlayerSelection) {
        requester.foliaRun {
            val requesterSnapshot = TpaRequesterSnapshot(uniqueId, name)
            sendRequestToTarget(requester, target, requesterSnapshot)
        }
    }

    private fun sendRequestToTarget(
        requester: Player,
        target: OnlinePlayerSelection,
        requesterSnapshot: TpaRequesterSnapshot,
    ) {
        target.withOnlinePlayer {
            requester.foliaRun {
                sendTeleportSuccess("已向 ${target.name} 发送传送请求。")
            }

            if (target.isBedrock) {
                openBedrockConfirm(requester, this, requesterSnapshot, target)
            } else {
                openJavaConfirm(requester, this, requesterSnapshot, target)
            }
        }.thenAccept { available ->
            if (!available) {
                runForOnlinePlayer(requesterSnapshot.uuid) {
                    sendTeleportError("玩家已离线。")
                }
            }
        }
    }

    private fun openJavaConfirm(
        requester: Player,
        target: Player,
        requesterSnapshot: TpaRequesterSnapshot,
        targetSnapshot: OnlinePlayerSelection,
    ) {
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
                name = "§6${requesterSnapshot.name} §f请求传送至您的位置"
                skullOwner = requesterSnapshot.name
            })
            set('Y', buildItem(XMaterial.DIAMOND) {
                name = "§a同意"
                lore += "§7允许 ${requesterSnapshot.name} 传送到您身边"
            }) {
                if (handled) {
                    target.foliaCloseInventory()
                    return@set
                }
                handled = true
                target.foliaRun {
                    acceptRequest(requester, target, requesterSnapshot, targetSnapshot)
                }
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
                target.foliaRun {
                    rejectRequest(requesterSnapshot, targetSnapshot)
                }
                target.foliaCloseInventory()
            }
            onClose {
                if (!handled) {
                    handled = true
                    target.foliaRun {
                        rejectRequest(requesterSnapshot, targetSnapshot)
                    }
                }
            }
        }
    }

    private fun openBedrockConfirm(
        requester: Player,
        target: Player,
        requesterSnapshot: TpaRequesterSnapshot,
        targetSnapshot: OnlinePlayerSelection,
    ) {
        target.sendForm(
            ModalForm.builder()
                .title("§l§6请求传送")
                .content("§6${requesterSnapshot.name} §f请求传送至您的位置。")
                .button1("同意")
                .button2("拒绝")
                .validResultHandler { response ->
                    val accepted = response.clickedButtonId() == 0
                    target.foliaRun {
                        if (accepted) {
                            acceptRequest(requester, target, requesterSnapshot, targetSnapshot)
                        } else {
                            rejectRequest(requesterSnapshot, targetSnapshot)
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    target.foliaRun {
                        rejectRequest(requesterSnapshot, targetSnapshot)
                    }
                })
        )
    }

    private fun acceptRequest(
        requester: Player,
        target: Player,
        requesterSnapshot: TpaRequesterSnapshot,
        targetSnapshot: OnlinePlayerSelection,
    ) {
        requester.foliaTeleport(target).whenComplete { success, error ->
            val teleported = error == null && success == true
            runForOnlinePlayer(requesterSnapshot.uuid) {
                if (teleported) {
                    sendTeleportSuccess("${targetSnapshot.name} 同意了您的传送请求。")
                } else {
                    sendTeleportError("传送失败，目标玩家可能已离线。")
                }
            }
            targetSnapshot.withOnlinePlayer {
                if (teleported) {
                    sendTeleportSuccess("已同意 ${requesterSnapshot.name} 的传送请求。")
                } else {
                    sendTeleportError("传送失败，${requesterSnapshot.name} 可能已离线。")
                }
            }
        }
    }

    private fun rejectRequest(requesterSnapshot: TpaRequesterSnapshot, targetSnapshot: OnlinePlayerSelection) {
        runForOnlinePlayer(requesterSnapshot.uuid) {
            sendTeleportError("${targetSnapshot.name} 拒绝了您的传送请求。")
        }
        targetSnapshot.withOnlinePlayer {
            sendTeleportError("已拒绝 ${requesterSnapshot.name} 的传送请求。")
        }
    }
}

private data class TpaRequesterSnapshot(
    val uuid: java.util.UUID,
    val name: String,
)
