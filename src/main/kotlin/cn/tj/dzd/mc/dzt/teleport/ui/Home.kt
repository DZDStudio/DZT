package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.teleport.service.DTPHome
import cn.tj.dzd.mc.dzt.teleport.service.addTeleportHome
import cn.tj.dzd.mc.dzt.teleport.service.deleteTeleportHome
import cn.tj.dzd.mc.dzt.teleport.service.getTeleportHomeList
import cn.tj.dzd.mc.dzt.teleport.service.teleportHome
import cn.tj.dzd.mc.dzt.ui.PaperDialogTextUI
import cn.tj.dzd.mc.dzt.ui.bukkitClickType
import cn.tj.dzd.mc.dzt.ui.openIconSelectBEUI
import cn.tj.dzd.mc.dzt.ui.openIconSelectUI
import cn.tj.dzd.mc.dzt.util.Icon
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaLocation
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import java.util.concurrent.CompletableFuture

object Home {

    /**
     * 打开传送点 UI。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     */
    fun openHomeUI(player: Player) {
        CompletableFuture.supplyAsync {
            player.getTeleportHomeList()
        }.whenComplete { homes, error ->
            if (error != null) {
                player.sendTeleportError("读取传送点失败: ${error.message ?: error.javaClass.simpleName}")
                return@whenComplete
            }

            if (player.isBePlayer()) {
                openBedrock(player, homes)
            } else {
                player.foliaRun {
                    openJava(this, homes)
                }
            }
        }
    }

    private fun openJava(player: Player, homes: List<DTPHome>) {
        player.openMenu<PageableChest<DTPHome>>("§l§6传送点") {
            rows(6)
            virtualize()

            map(
                "R###M###A",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<#>###"
            )

            onClick(lock = true) {}
            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set('M', XMaterial.YELLOW_STAINED_GLASS_PANE) { name = "§l§6传送点" }
            set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回传送菜单" }) {
                Teleport.run { player.openTeleport() }
            }
            set('A', buildItem(XMaterial.BOOK) {
                name = "§l§a添加传送点"
                lore += "§7记录当前位置为新的传送点"
            }) {
                openJavaCreate(player)
            }

            slotsBy('@')
            elements { homes }
            onGenerate { _, home, _, _ ->
                buildItem(home.icon.jeMaterial) {
                    name = "§e${home.name}"
                    lore += "§7图标: §e${home.icon.displayName}"
                    lore += "§7位置: §f${formatLocation(home.location)}"
                    lore += "§a左键前往"
                    lore += "§cShift + 右键删除"
                }
            }
            onClick { event, home ->
                when (event.bukkitClickType()) {
                    ClickType.LEFT -> {
                        player.teleportHome(home).thenAccept { success ->
                            if (success) {
                                player.sendTeleportSuccess("已传送至传送点[${home.name}]。")
                            } else {
                                player.sendTeleportError("传送点[${home.name}]不可用。")
                            }
                        }
                        player.foliaCloseInventory()
                    }
                    ClickType.SHIFT_RIGHT -> {
                        deleteHome(player, home.name) {
                            openHomeUI(player)
                        }
                        player.foliaCloseInventory()
                    }
                    else -> {}
                }
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

    private fun openJavaCreate(player: Player) {
        player.foliaRun {
            closeInventory()
            PaperDialogTextUI("§l§6添加传送点") { input ->
                val homeName = input.trim()
                if (homeName.isEmpty()) {
                    player.sendTeleportError("传送点名称不能为空。")
                    openHomeUI(player)
                } else {
                    player.openIconSelectUI("§l§6选择传送点图标") { icon ->
                        createHome(player, homeName, icon) {
                            openHomeUI(player)
                        }
                    }
                }
            }
        }
    }

    private fun openBedrock(player: Player, homes: List<DTPHome>) {
        val form = SimpleForm.builder()
            .title("§l§6传送点")
            .button("返回传送菜单", FormImage.Type.PATH, "textures/ui/box_ride.png")
            .button("添加传送点", FormImage.Type.PATH, "textures/ui/Add-Ons_Side-Nav_Icon_24x24.png")

        homes.forEach { home ->
            form.button(home.name, FormImage.Type.PATH, home.icon.beTexturePath)
        }

        form.validResultHandler { response ->
            when (val clicked = response.clickedButtonId()) {
                0 -> Teleport.run { player.openTeleport() }
                1 -> openBedrockCreate(player)
                else -> {
                    val home = homes.getOrNull(clicked - 2) ?: return@validResultHandler
                    openBedrockHomeAction(player, home)
                }
            }
        }
        player.sendForm(form)
    }

    private fun openBedrockCreate(player: Player) {
        player.sendForm(
            CustomForm.builder()
                .title("§l§6添加传送点")
                .input("名称", "请输入传送点名称")
                .validResultHandler { response ->
                    val homeName = response.asInput(0).orEmpty().trim()
                    if (homeName.isBlank()) {
                        player.sendTeleportError("传送点名称不能为空。")
                        openHomeUI(player)
                        return@validResultHandler
                    }

                    player.openIconSelectBEUI("§l§6选择传送点图标") { icon ->
                        createHome(player, homeName, icon) {
                            openHomeUI(player)
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    openHomeUI(player)
                })
        )
    }

    private fun openBedrockHomeAction(player: Player, home: DTPHome) {
        player.sendForm(
            SimpleForm.builder()
                .title("§l§6${home.name}")
                .content("§7位置: §f${formatLocation(home.location)}\n§7图标: §e${home.icon.displayName}")
                .button("前往", FormImage.Type.PATH, home.icon.beTexturePath)
                .button("§c删除", FormImage.Type.PATH, "textures/ui/trash.png")
                .button("返回", FormImage.Type.PATH, "textures/ui/box_ride.png")
                .validResultHandler { response ->
                    when (response.clickedButtonId()) {
                        0 -> {
                            player.teleportHome(home).thenAccept { success ->
                                if (success) {
                                    player.sendTeleportSuccess("已传送至传送点[${home.name}]。")
                                } else {
                                    player.sendTeleportError("传送点[${home.name}]不可用。")
                                }
                            }
                        }
                        1 -> openBedrockDeleteConfirm(player, home)
                        2 -> openHomeUI(player)
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    openHomeUI(player)
                })
        )
    }

    private fun openBedrockDeleteConfirm(player: Player, home: DTPHome) {
        player.sendForm(
            ModalForm.builder()
                .title("§l§c删除传送点")
                .content("确认删除传送点[${home.name}]？")
                .button1("确认删除")
                .button2("返回")
                .validResultHandler { response ->
                    if (response.clickedButtonId() == 0) {
                        deleteHome(player, home.name) {
                            openHomeUI(player)
                        }
                    } else {
                        openBedrockHomeAction(player, home)
                    }
                }
        )
    }

    private fun createHome(player: Player, name: String, icon: Icon, after: () -> Unit) {
        if (name.isBlank()) {
            player.sendTeleportError("传送点名称不能为空。")
            after()
            return
        }

        player.foliaLocation().whenComplete { location, locationError ->
            if (locationError != null || location == null) {
                player.sendTeleportError("无法读取当前位置。")
                after()
                return@whenComplete
            }

            CompletableFuture.supplyAsync {
                try {
                    player.addTeleportHome(name, location, icon)
                } catch (ex: Exception) {
                    throw IllegalArgumentException(ex.message ?: "新增传送点失败", ex)
                }
            }.whenComplete { success, error ->
                if (error != null) {
                    player.sendTeleportError(error.message ?: "新增传送点失败。")
                } else if (success) {
                    player.sendTeleportSuccess("已添加传送点[$name]。")
                } else {
                    player.sendTeleportError("新增传送点[$name]失败。")
                }
                after()
            }
        }
    }

    private fun deleteHome(player: Player, name: String, after: () -> Unit) {
        CompletableFuture.supplyAsync {
            player.deleteTeleportHome(name)
        }.whenComplete { success, error ->
            if (error != null) {
                player.sendTeleportError(error.message ?: "删除传送点失败。")
            } else if (success) {
                player.sendTeleportSuccess("已删除传送点[$name]。")
            } else {
                player.sendTeleportError("删除传送点[$name]失败。")
            }
            after()
        }
    }
}

internal fun formatLocation(location: Location): String {
    val world = location.world?.name ?: "未知世界"
    return "$world ${location.blockX}, ${location.blockY}, ${location.blockZ}"
}

internal fun Player.sendTeleportSuccess(message: String) {
    sendDZTSuccess(message)
}

internal fun Player.sendTeleportError(message: String) {
    sendDZTError(message)
}
