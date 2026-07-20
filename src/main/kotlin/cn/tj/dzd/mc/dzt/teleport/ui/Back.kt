package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.teleport.ui.Teleport.openTeleport
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.teleport.service.BackService
import cn.tj.dzd.mc.dzt.teleport.service.DTPBack
import cn.tj.dzd.mc.dzt.teleport.service.teleportBack
import cn.tj.dzd.mc.dzt.ui.bukkitClickType
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

object Back {
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * 打开死亡点 UI。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     */
    fun openBackUI(player: Player) {
        player.foliaRun {
            loadBacks(uniqueId, isBePlayer())
        }
    }

    private fun loadBacks(ownerId: UUID, bedrockPlayer: Boolean) {
        DztAsyncExecutor.supply {
            BackService.getBackEntryList(ownerId)
        }.whenComplete { entries, error ->
            runForOnlinePlayer(ownerId) {
                if (error != null) {
                    sendTeleportError("读取死亡点失败: ${error.message ?: error.javaClass.simpleName}")
                    return@runForOnlinePlayer
                }

                val backs = try {
                    entries.orEmpty().map(BackService::toDTPBack)
                } catch (conversionError: Exception) {
                    sendTeleportError("读取死亡点失败: ${conversionError.message ?: conversionError.javaClass.simpleName}")
                    return@runForOnlinePlayer
                }

                if (bedrockPlayer) {
                    openBedrock(this, backs)
                } else {
                    openJava(this, backs)
                }
            }
        }
    }

    private fun openJava(player: Player, backs: List<DTPBack>) {
        player.openMenu<PageableChest<DTPBack>>("§l§6死亡点") {
            rows(6)
            virtualize()

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
            set('M', XMaterial.YELLOW_STAINED_GLASS_PANE) { name = "§l§6死亡点" }
            set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回传送菜单" }) {
                player.openTeleport()
            }

            slotsBy('@')
            elements { backs }
            onGenerate { _, back, _, _ ->
                buildItem(XMaterial.RECOVERY_COMPASS) {
                    name = "§c死亡记录: ${formatTime(back.time)}"
                    lore += "§7位置: §f${formatLocation(back.location)}"
                    lore += "§a左键前往"
                    lore += "§cShift + 右键删除"
                }
            }
            onClick { event, back ->
                val clickType = event.bukkitClickType()
                player.foliaRun {
                    when (clickType) {
                        ClickType.LEFT -> {
                            teleportBack(back).whenComplete { success, error ->
                                player.foliaRun {
                                    if (error != null || success != true) {
                                        sendTeleportError("死亡点[${formatTime(back.time)}]不可用。")
                                    } else {
                                        sendTeleportSuccess("已传送至死亡点[${formatTime(back.time)}]。")
                                    }
                                }
                            }
                            closeInventory()
                        }

                        ClickType.SHIFT_RIGHT -> {
                            deleteBack(this, back) {
                                openBackUI(this)
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

    private fun openBedrock(player: Player, backs: List<DTPBack>) {
        val form = SimpleForm.builder()
            .title("§l§6死亡点")
            .content(if (backs.isEmpty()) "暂无死亡点记录。" else "请选择要操作的死亡点。")
            .button("返回传送菜单", FormImage.Type.PATH, "textures/ui/box_ride.png")

        backs.forEach { back ->
            form.button(
                "${formatTime(back.time)}\n${formatLocation(back.location)}",
                FormImage.Type.PATH,
                "textures/ui/warning_sad_steve.png"
            )
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            player.foliaRun {
                if (clicked == 0) {
                    openTeleport()
                    return@foliaRun
                }

                val back = backs.getOrNull(clicked - 1) ?: return@foliaRun
                openBedrockBackAction(this, back)
            }
        }
        player.sendForm(form)
    }

    private fun openBedrockBackAction(player: Player, back: DTPBack) {
        player.foliaRun {
            sendForm(
                ModalForm.builder()
                    .title("§l§6死亡点")
                    .content("§7时间: §f${formatTime(back.time)}\n§7位置: §f${formatLocation(back.location)}")
                    .button1("前往")
                    .button2("删除")
                    .validResultHandler { response ->
                        val clicked = response.clickedButtonId()
                        player.foliaRun {
                            if (clicked == 0) {
                                teleportBack(back).whenComplete { success, error ->
                                    player.foliaRun {
                                        if (error != null || success != true) {
                                            sendTeleportError("死亡点[${formatTime(back.time)}]不可用。")
                                        } else {
                                            sendTeleportSuccess("已传送至死亡点[${formatTime(back.time)}]。")
                                        }
                                    }
                                }
                            } else {
                                openBedrockDeleteConfirm(this, back)
                            }
                        }
                    }
                    .closedOrInvalidResultHandler(Runnable {
                        player.foliaRun {
                            openBackUI(this)
                        }
                    })
            )
        }
    }

    private fun openBedrockDeleteConfirm(player: Player, back: DTPBack) {
        player.foliaRun {
            sendForm(
                ModalForm.builder()
                    .title("§l§c删除死亡点")
                    .content("确认删除死亡点[${formatTime(back.time)}]？")
                    .button1("确认删除")
                    .button2("返回")
                    .validResultHandler { response ->
                        val clicked = response.clickedButtonId()
                        player.foliaRun {
                            if (clicked == 0) {
                                deleteBack(this, back) {
                                    openBackUI(this)
                                }
                            } else {
                                openBedrockBackAction(this, back)
                            }
                        }
                    }
            )
        }
    }

    private fun deleteBack(player: Player, back: DTPBack, after: Player.() -> Unit) {
        player.foliaRun {
            val ownerId = uniqueId
            val backTime = back.time
            DztAsyncExecutor.supply {
                BackService.removeBackRecord(ownerId, backTime)
            }.whenComplete { success, error ->
                runForOnlinePlayer(ownerId) {
                    if (error != null) {
                        sendTeleportError(error.message ?: "删除死亡点失败。")
                    } else if (success == true) {
                        sendTeleportSuccess("已删除死亡点[${formatTime(backTime)}]。")
                    } else {
                        sendTeleportError("删除死亡点[${formatTime(backTime)}]失败。")
                    }
                    after()
                }
            }
        }
    }

    private fun formatTime(time: Long): String {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(time))
    }
}
