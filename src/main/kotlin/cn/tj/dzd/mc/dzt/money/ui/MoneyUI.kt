package cn.tj.dzd.mc.dzt.money.ui

import cn.tj.dzd.mc.dzt.data.table.MoneyRecord
import cn.tj.dzd.mc.dzt.data.table.MoneyRecordType
import cn.tj.dzd.mc.dzt.menu.ui.Menu.openMenu
import cn.tj.dzd.mc.dzt.money.MoneyService
import cn.tj.dzd.mc.dzt.util.TextLogo
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
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
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture

object MoneyUI {
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * 打开经济流水查询界面。
     *
     * 会根据玩家客户端类型自动选择 Java 版箱子菜单或基岩版表单菜单。
     *
     * @param player 要打开界面的玩家。
     */
    fun openMoneyUI(player: Player) {
        player.foliaRun {
            val uuid = uniqueId
            val bedrockPlayer = isBePlayer()
            val target = this
            val balance = MoneyService.getBalance(this)

            CompletableFuture.supplyAsync {
                MoneyViewData(
                    balance = balance,
                    records = MoneyService.getRecords(uuid)
                )
            }.whenComplete { data, error ->
                if (error != null) {
                    target.foliaRun {
                        sendDZTError("读取经济流水失败: ${error.message ?: error.javaClass.simpleName}")
                    }
                    return@whenComplete
                }

                target.foliaRun {
                    if (bedrockPlayer) {
                        openBedrock(this, data)
                    } else {
                        openJava(this, data)
                    }
                }
            }
        }
    }

    private fun openJava(player: Player, data: MoneyViewData) {
        player.openMenu<PageableChest<MoneyRecord>>(TextLogo) {
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
            set('M', buildItem(XMaterial.EMERALD) {
                name = "§l§6经济流水"
                lore += "§7当前余额: §6${formatBalance(data.balance)} §e弟弟币"
                lore += "§7最多显示最近 ${data.records.size} 条流水"
            })
            set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回主菜单" }) {
                player.openMenu()
            }

            slotsBy('@')
            elements { data.records }
            onGenerate { _, record, _, _ ->
                buildItem(record.material) {
                    name = "${record.color}${record.type.desc}: ${record.amount} 弟弟币"
                    lore += "§7时间: §f${formatTime(record.time)}"
                    lore += "§7关联: §f${record.related ?: "无"}"
                    lore += "§7备注: §f${record.remark ?: "无"}"
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

    private fun openBedrock(player: Player, data: MoneyViewData) {
        val form = SimpleForm.builder()
            .title("§l§6经济流水")
            .content(
                buildString {
                    append("§7当前余额: §6${formatBalance(data.balance)} §e弟弟币")
                    if (data.records.isEmpty()) {
                        append("\n§7暂无流水记录。")
                    } else {
                        append("\n§7请选择要查看的流水。")
                    }
                }
            )
            .button("返回主菜单", FormImage.Type.PATH, "textures/ui/box_ride.png")

        data.records.forEach { record ->
            form.button(
                "${record.type.desc}: ${record.amount} 弟弟币\n${formatTime(record.time)}",
                FormImage.Type.PATH,
                record.bedrockIcon
            )
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            if (clicked == 0) {
                player.foliaRun {
                    openMenu()
                }
                return@validResultHandler
            }

            val record = data.records.getOrNull(clicked - 1) ?: return@validResultHandler
            player.foliaRun {
                openBedrockRecordDetail(this, data, record)
            }
        }
        player.sendForm(form)
    }

    private fun openBedrockRecordDetail(player: Player, data: MoneyViewData, record: MoneyRecord) {
        player.sendForm(
            ModalForm.builder()
                .title("§l§6流水详情")
                .content(
                    buildString {
                        append("§7类型: ${record.color}${record.type.desc}")
                        append("\n§7金额: §6${record.amount} §e弟弟币")
                        append("\n§7时间: §f${formatTime(record.time)}")
                        append("\n§7关联: §f${record.related ?: "无"}")
                        append("\n§7备注: §f${record.remark ?: "无"}")
                    }
                )
                .button1("返回流水")
                .button2("返回主菜单")
                .validResultHandler { response ->
                    player.foliaRun {
                        if (response.clickedButtonId() == 0) {
                            openBedrock(this, data)
                        } else {
                            openMenu()
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    player.foliaRun {
                        openBedrock(this, data)
                    }
                })
        )
    }

    private fun formatTime(time: Long): String {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(time))
    }

    private fun formatBalance(balance: Double): String {
        return BigDecimal.valueOf(balance).stripTrailingZeros().toPlainString()
    }

    private val MoneyRecord.color: String
        get() = when (type) {
            MoneyRecordType.IN -> "§a"
            MoneyRecordType.OUT -> "§c"
        }

    private val MoneyRecord.material: XMaterial
        get() = when (type) {
            MoneyRecordType.IN -> XMaterial.EMERALD
            MoneyRecordType.OUT -> XMaterial.REDSTONE
        }

    private val MoneyRecord.bedrockIcon: String
        get() = when (type) {
            MoneyRecordType.IN -> "textures/items/emerald.png"
            MoneyRecordType.OUT -> "textures/items/redstone_dust.png"
        }
}

private data class MoneyViewData(
    val balance: Double,
    val records: List<MoneyRecord>,
)
