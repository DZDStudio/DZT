package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.teleport.ui.Teleport.openTeleport
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import cn.tj.dzd.mc.dzt.teleport.service.DTPHome
import cn.tj.dzd.mc.dzt.teleport.service.HomeService
import cn.tj.dzd.mc.dzt.teleport.service.teleportHome
import cn.tj.dzd.mc.dzt.ui.PaperDialogTextUI
import cn.tj.dzd.mc.dzt.ui.bukkitClickType
import cn.tj.dzd.mc.dzt.ui.openIconSelectBEUI
import cn.tj.dzd.mc.dzt.ui.openIconSelectUI
import cn.tj.dzd.mc.dzt.util.Icon
import cn.tj.dzd.mc.dzt.util.foliaLocation
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
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
import java.util.UUID

object Home {

    /**
     * 打开传送点 UI。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     */
    fun openHomeUI(player: Player) {
        player.foliaRun {
            loadHomes(uniqueId, isBePlayer())
        }
    }

    private fun loadHomes(ownerId: UUID, bedrockPlayer: Boolean) {
        DztAsyncExecutor.supply {
            HomeService.getHomeEntryList(ownerId)
        }.whenComplete { entries, error ->
            runForOnlinePlayer(ownerId) {
                if (error != null) {
                    sendTeleportError("读取传送点失败: ${error.message ?: error.javaClass.simpleName}")
                    return@runForOnlinePlayer
                }

                val homes = try {
                    entries.orEmpty().map(HomeService::toDTPHome)
                } catch (conversionError: Exception) {
                    sendTeleportError("读取传送点失败: ${conversionError.message ?: conversionError.javaClass.simpleName}")
                    return@runForOnlinePlayer
                }

                if (bedrockPlayer) {
                    openBedrock(this, homes)
                } else {
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
                player.openTeleport()
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
                buildItem(home.icon.xMaterial) {
                    name = "§e${home.name}"
                    lore += "§7图标: §e${home.icon.displayName}"
                    lore += "§7位置: §f${formatLocation(home.location)}"
                    lore += "§a左键前往"
                    lore += "§cShift + 右键删除"
                }
            }
            onClick { event, home ->
                val clickType = event.bukkitClickType()
                player.foliaRun {
                    when (clickType) {
                        ClickType.LEFT -> {
                            teleportHome(home).whenComplete { success, error ->
                                player.foliaRun {
                                    if (error != null || success != true) {
                                        sendTeleportError("传送点[${home.name}]不可用。")
                                    } else {
                                        sendTeleportSuccess("已传送至传送点[${home.name}]。")
                                    }
                                }
                            }
                            closeInventory()
                        }

                        ClickType.SHIFT_RIGHT -> {
                            deleteHome(this, home.name) {
                                openHomeUI(this)
                            }
                            closeInventory()
                        }

                        else -> Unit
                    }
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
            PaperDialogTextUI.open(this, "§l§6添加传送点") { input ->
                foliaRun {
                    val homeName = input.trim()
                    if (homeName.isEmpty()) {
                        sendTeleportError("传送点名称不能为空。")
                        openHomeUI(this)
                    } else {
                        openIconSelectUI("§l§6选择传送点图标") { icon ->
                            createHome(this, homeName, icon) {
                                openHomeUI(this)
                            }
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
            val clicked = response.clickedButtonId()
            player.foliaRun {
                when (clicked) {
                    0 -> openTeleport()
                    1 -> openBedrockCreate(this)
                    else -> {
                        val home = homes.getOrNull(clicked - 2) ?: return@foliaRun
                        openBedrockHomeAction(this, home)
                    }
                }
            }
        }
        player.sendForm(form)
    }

    private fun openBedrockCreate(player: Player) {
        player.foliaRun {
            sendForm(
                CustomForm.builder()
                    .title("§l§6添加传送点")
                    .input("名称", "请输入传送点名称")
                    .validResultHandler { response ->
                        val homeName = response.asInput(0).orEmpty().trim()
                        player.foliaRun {
                            if (homeName.isBlank()) {
                                sendTeleportError("传送点名称不能为空。")
                                openHomeUI(this)
                                return@foliaRun
                            }

                            openIconSelectBEUI("§l§6选择传送点图标") { icon ->
                                createHome(this, homeName, icon) {
                                    openHomeUI(this)
                                }
                            }
                        }
                    }
                    .closedOrInvalidResultHandler(Runnable {
                        player.foliaRun {
                            openHomeUI(this)
                        }
                    })
            )
        }
    }

    private fun openBedrockHomeAction(player: Player, home: DTPHome) {
        player.foliaRun {
            sendForm(
                SimpleForm.builder()
                    .title("§l§6${home.name}")
                    .content("§7位置: §f${formatLocation(home.location)}\n§7图标: §e${home.icon.displayName}")
                    .button("前往", FormImage.Type.PATH, home.icon.beTexturePath)
                    .button("§c删除", FormImage.Type.PATH, "textures/ui/trash.png")
                    .button("返回", FormImage.Type.PATH, "textures/ui/box_ride.png")
                    .validResultHandler { response ->
                        val clicked = response.clickedButtonId()
                        player.foliaRun {
                            when (clicked) {
                                0 -> {
                                    teleportHome(home).whenComplete { success, error ->
                                        player.foliaRun {
                                            if (error != null || success != true) {
                                                sendTeleportError("传送点[${home.name}]不可用。")
                                            } else {
                                                sendTeleportSuccess("已传送至传送点[${home.name}]。")
                                            }
                                        }
                                    }
                                }

                                1 -> openBedrockDeleteConfirm(this, home)
                                2 -> openHomeUI(this)
                            }
                        }
                    }
                    .closedOrInvalidResultHandler(Runnable {
                        player.foliaRun {
                            openHomeUI(this)
                        }
                    })
            )
        }
    }

    private fun openBedrockDeleteConfirm(player: Player, home: DTPHome) {
        player.foliaRun {
            sendForm(
                ModalForm.builder()
                    .title("§l§c删除传送点")
                    .content("确认删除传送点[${home.name}]？")
                    .button1("确认删除")
                    .button2("返回")
                    .validResultHandler { response ->
                        val clicked = response.clickedButtonId()
                        player.foliaRun {
                            if (clicked == 0) {
                                deleteHome(this, home.name) {
                                    openHomeUI(this)
                                }
                            } else {
                                openBedrockHomeAction(this, home)
                            }
                        }
                    }
            )
        }
    }

    private fun createHome(player: Player, name: String, icon: Icon, after: Player.() -> Unit) {
        player.foliaRun {
            createHomeOnPlayerThread(this, uniqueId, name, icon, after)
        }
    }

    private fun createHomeOnPlayerThread(
        player: Player,
        ownerId: UUID,
        name: String,
        icon: Icon,
        after: Player.() -> Unit,
    ) {
        if (name.isBlank()) {
            player.sendTeleportError("传送点名称不能为空。")
            player.after()
            return
        }

        player.foliaLocation().whenComplete { location, locationError ->
            if (locationError != null || location == null) {
                runForOnlinePlayer(ownerId) {
                    sendTeleportError("无法读取当前位置。")
                    after()
                }
                return@whenComplete
            }

            runForOnlinePlayer(ownerId) {
                val storedLocation = try {
                    location.toStoredLocation()
                } catch (error: IllegalArgumentException) {
                    sendTeleportError(error.message ?: "无法读取当前位置。")
                    after()
                    return@runForOnlinePlayer
                }

                DztAsyncExecutor.supply {
                    try {
                        HomeService.addHome(ownerId, name, storedLocation, icon)
                    } catch (error: Exception) {
                        throw IllegalArgumentException(error.message ?: "新增传送点失败", error)
                    }
                }.whenComplete { success, error ->
                    runForOnlinePlayer(ownerId) {
                        if (error != null) {
                            sendTeleportError(error.message ?: "新增传送点失败。")
                        } else if (success == true) {
                            sendTeleportSuccess("已添加传送点[$name]。")
                        } else {
                            sendTeleportError("新增传送点[$name]失败。")
                        }
                        after()
                    }
                }
            }
        }
    }

    private fun deleteHome(player: Player, name: String, after: Player.() -> Unit) {
        player.foliaRun {
            val ownerId = uniqueId
            DztAsyncExecutor.supply {
                HomeService.removeHome(ownerId, name)
            }.whenComplete { success, error ->
                runForOnlinePlayer(ownerId) {
                    if (error != null) {
                        sendTeleportError(error.message ?: "删除传送点失败。")
                    } else if (success == true) {
                        sendTeleportSuccess("已删除传送点[$name]。")
                    } else {
                        sendTeleportError("删除传送点[$name]失败。")
                    }
                    after()
                }
            }
        }
    }
}

internal fun formatLocation(location: Location): String {
    val world = location.world?.name ?: "未知世界"
    return "$world ${location.blockX}, ${location.blockY}, ${location.blockZ}"
}

private fun Location.toStoredLocation(): StoredLocation {
    val worldName = requireNotNull(world) { "传送点坐标缺少世界" }.name
    return StoredLocation(worldName, x, y, z)
}

internal fun Player.sendTeleportSuccess(message: String) {
    sendDZTSuccess(message)
}

internal fun Player.sendTeleportError(message: String) {
    sendDZTError(message)
}
