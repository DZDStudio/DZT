package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.util.avatarUrl
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem

object TPA {
    /**
     * 打开玩家传送请求 UI。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     */
    fun openTPAUI(player: Player) {
        val targets = Bukkit.getOnlinePlayers()
            .filter { it.uniqueId != player.uniqueId }
            .sortedBy { it.name.lowercase() }

        if (player.isBePlayer()) {
            openBedrock(player, targets)
        } else {
            player.foliaRun {
                openJava(this, targets)
            }
        }
    }

    private fun openJava(player: Player, targets: List<Player>) {
        player.openMenu<PageableChest<Player>>("§l§6玩家传送") {
            rows(6)
            hidePlayerInventory()

            map(
                "R###M####",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<#>###"
            )

            onClick(lock = true) {}
            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set('M', XMaterial.YELLOW_STAINED_GLASS_PANE) { name = "§l§6玩家传送" }
            set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回传送菜单" }) {
                Teleport.run { player.openTeleport() }
            }

            slotsBy('@')
            elements { targets }
            onGenerate { _, target, _, _ ->
                buildItem(XMaterial.PLAYER_HEAD) {
                    name = "§6${target.name}"
                    lore += "§7点击发送传送请求"
                    skullOwner = target.name
                }
            }
            onClick { _, target ->
                sendRequest(player, target)
                player.foliaCloseInventory()
            }

            setPreviousPage(48) { _, hasPrevious ->
                buildItem(if (hasPrevious) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasPrevious) "§a上一页" else "§7已经是第一页"
                }
            }
            setNextPage(50) { _, hasNext ->
                buildItem(if (hasNext) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasNext) "§a下一页" else "§7已经是最后一页"
                }
            }
        }
    }

    private fun openBedrock(player: Player, targets: List<Player>) {
        val form = SimpleForm.builder()
            .title("§l§6玩家传送")
            .content(if (targets.isEmpty()) "当前没有其他在线玩家。" else "请选择要传送到的玩家。")
            .button("返回传送菜单", FormImage.Type.PATH, "textures/ui/box_ride.png")

        targets.forEach { target ->
            form.button(target.name, FormImage.Type.URL, target.avatarUrl())
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            if (clicked == 0) {
                Teleport.run { player.openTeleport() }
                return@validResultHandler
            }

            val target = targets.getOrNull(clicked - 1) ?: return@validResultHandler
            sendRequest(player, target)
        }
        player.sendForm(form)
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
            hidePlayerInventory()

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
