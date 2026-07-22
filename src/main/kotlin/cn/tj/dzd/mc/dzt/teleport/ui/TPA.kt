package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelection
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectUI
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectSecondaryAction
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
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
            description = "请选择一名在线玩家并发起传送操作。",
            selectLore = "§7点击请求传送至该玩家",
            backLabel = "§l§e返回传送菜单",
            secondaryAction = OnlinePlayerSelectSecondaryAction(
                javaLore = "§eShift + 右键邀请该玩家传送过来",
                bedrockPrimaryLabel = "请求传送至该玩家",
                bedrockPrimaryIcon = "textures/items/ender_pearl.png",
                bedrockSecondaryLabel = "邀请该玩家传送过来",
                bedrockSecondaryIcon = "textures/ui/warning_alex.png",
                onSelect = { target ->
                    sendRequest(this, target, TpaRequestType.INVITE_TARGET)
                },
            ),
            onBack = {
                Teleport.run { openTeleport() }
            },
            onSelect = { target ->
                sendRequest(this, target, TpaRequestType.GO_TO_TARGET)
            }
        )
    }

    private fun sendRequest(
        requester: Player,
        target: OnlinePlayerSelection,
        requestType: TpaRequestType,
    ) {
        requester.foliaRun {
            val requesterSnapshot = TpaRequesterSnapshot(uniqueId, name)
            sendRequestToTarget(requester, target, requesterSnapshot, requestType)
        }
    }

    private fun sendRequestToTarget(
        requester: Player,
        target: OnlinePlayerSelection,
        requesterSnapshot: TpaRequesterSnapshot,
        requestType: TpaRequestType,
    ) {
        target.withOnlinePlayer {
            requester.foliaRun {
                sendTeleportSuccess(requestType.sentMessage(target.name))
            }

            if (target.isBedrock) {
                openBedrockConfirm(requester, this, requesterSnapshot, target, requestType)
            } else {
                openJavaConfirm(requester, this, requesterSnapshot, target, requestType)
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
        requestType: TpaRequestType,
    ) {
        var handled = false
        target.openMenu<Chest>(requestType.title) {
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
                name = "§6${requesterSnapshot.name} §f${requestType.prompt}"
                skullOwner = requesterSnapshot.name
            })
            set('Y', buildItem(XMaterial.DIAMOND) {
                name = "§a同意"
                lore += "§7${requestType.acceptLore(requesterSnapshot.name)}"
            }) {
                if (handled) {
                    target.foliaCloseInventory()
                    return@set
                }
                handled = true
                target.foliaRun {
                    acceptRequest(requester, target, requesterSnapshot, targetSnapshot, requestType)
                }
                target.foliaCloseInventory()
            }
            set('N', buildItem(XMaterial.REDSTONE) {
                name = "§c拒绝"
                lore += "§7${requestType.rejectLore}"
            }) {
                if (handled) {
                    target.foliaCloseInventory()
                    return@set
                }
                handled = true
                target.foliaRun {
                    rejectRequest(requesterSnapshot, targetSnapshot, requestType)
                }
                target.foliaCloseInventory()
            }
            onClose {
                if (!handled) {
                    handled = true
                    target.foliaRun {
                        rejectRequest(requesterSnapshot, targetSnapshot, requestType)
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
        requestType: TpaRequestType,
    ) {
        target.sendForm(
            SimpleForm.builder()
                .title(requestType.title)
                .content("§6${requesterSnapshot.name} §f${requestType.prompt}。")
                .button("同意", FormImage.Type.PATH, "textures/items/diamond.png")
                .button("拒绝", FormImage.Type.PATH, "textures/items/redstone_dust.png")
                .validResultHandler { response ->
                    val accepted = response.clickedButtonId() == 0
                    target.foliaRun {
                        if (accepted) {
                            acceptRequest(requester, target, requesterSnapshot, targetSnapshot, requestType)
                        } else {
                            rejectRequest(requesterSnapshot, targetSnapshot, requestType)
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    target.foliaRun {
                        rejectRequest(requesterSnapshot, targetSnapshot, requestType)
                    }
                })
        )
    }

    private fun acceptRequest(
        requester: Player,
        target: Player,
        requesterSnapshot: TpaRequesterSnapshot,
        targetSnapshot: OnlinePlayerSelection,
        requestType: TpaRequestType,
    ) {
        val teleport = when (requestType) {
            TpaRequestType.GO_TO_TARGET -> requester.foliaTeleport(target)
            TpaRequestType.INVITE_TARGET -> target.foliaTeleport(requester)
        }
        teleport.whenComplete { success, error ->
            val teleported = error == null && success == true
            runForOnlinePlayer(requesterSnapshot.uuid) {
                if (teleported) {
                    sendTeleportSuccess(requestType.acceptedRequesterMessage(targetSnapshot.name))
                } else {
                    sendTeleportError("传送失败，目标玩家可能已离线。")
                }
            }
            targetSnapshot.withOnlinePlayer {
                if (teleported) {
                    sendTeleportSuccess(requestType.acceptedTargetMessage(requesterSnapshot.name))
                } else {
                    sendTeleportError("传送失败，${requesterSnapshot.name} 可能已离线。")
                }
            }
        }
    }

    private fun rejectRequest(
        requesterSnapshot: TpaRequesterSnapshot,
        targetSnapshot: OnlinePlayerSelection,
        requestType: TpaRequestType,
    ) {
        runForOnlinePlayer(requesterSnapshot.uuid) {
            sendTeleportError(requestType.rejectedRequesterMessage(targetSnapshot.name))
        }
        targetSnapshot.withOnlinePlayer {
            sendTeleportError(requestType.rejectedTargetMessage(requesterSnapshot.name))
        }
    }
}

private enum class TpaRequestType(
    val title: String,
    val prompt: String,
    val rejectLore: String,
) {
    GO_TO_TARGET("§l§6请求传送", "请求传送至您的位置", "拒绝这次传送请求"),
    INVITE_TARGET("§l§6传送邀请", "邀请您传送至其位置", "拒绝这次传送邀请"),
    ;

    fun sentMessage(targetName: String): String {
        return when (this) {
            GO_TO_TARGET -> "已向 $targetName 发送传送请求。"
            INVITE_TARGET -> "已向 $targetName 发送传送邀请。"
        }
    }

    fun acceptLore(requesterName: String): String {
        return when (this) {
            GO_TO_TARGET -> "允许 $requesterName 传送到您身边"
            INVITE_TARGET -> "传送到 $requesterName 身边"
        }
    }

    fun acceptedRequesterMessage(targetName: String): String {
        return when (this) {
            GO_TO_TARGET -> "$targetName 同意了您的传送请求。"
            INVITE_TARGET -> "$targetName 接受了您的传送邀请。"
        }
    }

    fun acceptedTargetMessage(requesterName: String): String {
        return when (this) {
            GO_TO_TARGET -> "已同意 $requesterName 的传送请求。"
            INVITE_TARGET -> "已接受 $requesterName 的传送邀请。"
        }
    }

    fun rejectedRequesterMessage(targetName: String): String {
        return when (this) {
            GO_TO_TARGET -> "$targetName 拒绝了您的传送请求。"
            INVITE_TARGET -> "$targetName 拒绝了您的传送邀请。"
        }
    }

    fun rejectedTargetMessage(requesterName: String): String {
        return when (this) {
            GO_TO_TARGET -> "已拒绝 $requesterName 的传送请求。"
            INVITE_TARGET -> "已拒绝 $requesterName 的传送邀请。"
        }
    }
}

private data class TpaRequesterSnapshot(
    val uuid: java.util.UUID,
    val name: String,
)
