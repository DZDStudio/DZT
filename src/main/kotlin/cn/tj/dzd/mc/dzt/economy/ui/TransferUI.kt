package cn.tj.dzd.mc.dzt.economy.ui

import cn.tj.dzd.mc.dzt.economy.EconomyTransferResult
import cn.tj.dzd.mc.dzt.economy.EconomyTransferStatus
import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.menu.ui.Menu.openMenu
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectUI
import cn.tj.dzd.mc.dzt.ui.PaperDialogTextUI
import cn.tj.dzd.mc.dzt.util.DEFAULT_FOLIA_TELEPORT_SOUND
import cn.tj.dzd.mc.dzt.util.foliaPlaySound
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendDZTTip
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.ModalForm
import taboolib.common.platform.function.severe
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 基于 ServiceIO 的跨端玩家转账 UI。
 */
object TransferUI {

    private val transfersInProgress = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * 打开转账目标选择界面。
     *
     * @param player 发起转账的玩家。
     */
    fun openTransferUI(player: Player) {
        OnlinePlayerSelectUI.open(
            player = player,
            title = "§l§6玩家转账",
            description = "请选择收款玩家。",
            emptyMessage = "当前没有可转账的在线玩家。",
            selectLore = "§7点击向该玩家转账",
            backLabel = "§l§e返回主菜单",
            onBack = {
                openMenu()
            },
            onSelect = { target ->
                openAmountInput(this, TransferTarget(target.uniqueId, target.name))
            },
        )
    }

    private fun openAmountInput(player: Player, target: TransferTarget) {
        if (!target.isOnline()) {
            player.sendDZTError("收款玩家已离线。")
            openTransferUI(player)
            return
        }

        if (player.isBePlayer()) {
            openBedrockAmountInput(player, target)
        } else {
            PaperDialogTextUI.open(
                player = player,
                title = "§l§6向 ${target.name} 转账",
                inputLabel = "金额",
            ) { input ->
                player.foliaRun {
                    handleAmountInput(this, target, input)
                }
            }
        }
    }

    private fun openBedrockAmountInput(player: Player, target: TransferTarget) {
        player.sendForm(
            CustomForm.builder()
                .title("§l§6向 ${target.name} 转账")
                .input("金额", "请输入转账金额")
                .validResultHandler { response ->
                    val input = response.asInput(0).orEmpty()
                    player.foliaRun {
                        handleAmountInput(this, target, input)
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    player.foliaRun {
                        openTransferUI(this)
                    }
                })
        )
    }

    private fun handleAmountInput(player: Player, target: TransferTarget, input: String) {
        val amount = ServiceEconomy.parseAmount(input)
        if (amount == null) {
            player.sendDZTError("请输入大于 0 的有效金额。")
            openAmountInput(player, target)
            return
        }

        if (!target.isOnline()) {
            player.sendDZTError("收款玩家已离线。")
            openTransferUI(player)
            return
        }

        if (player.isBePlayer()) {
            openBedrockConfirmation(player, target, amount)
        } else {
            openJavaConfirmation(player, target, amount)
        }
    }

    private fun openJavaConfirmation(player: Player, target: TransferTarget, amount: BigDecimal) {
        var handled = false
        val formattedAmount = ServiceEconomy.formatAmount(amount)

        player.openMenu<Chest>("§l§6确认转账") {
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
                name = "§6${target.name}"
                lore += "§7转账金额: §6$formattedAmount DDB"
                skullOwner = target.name
            })
            set('Y', buildItem(XMaterial.EMERALD_BLOCK) {
                name = "§a确认转账"
                lore += "§7点击后立即处理"
            }) {
                if (handled) {
                    return@set
                }
                handled = true
                player.closeInventory()
                executeTransfer(player, target, amount)
            }
            set('N', buildItem(XMaterial.REDSTONE_BLOCK) {
                name = "§c取消"
                lore += "§7返回玩家选择"
            }) {
                if (handled) {
                    return@set
                }
                handled = true
                player.closeInventory()
                openTransferUI(player)
            }
        }
    }

    private fun openBedrockConfirmation(player: Player, target: TransferTarget, amount: BigDecimal) {
        val formattedAmount = ServiceEconomy.formatAmount(amount)
        player.sendForm(
            ModalForm.builder()
                .title("§l§6确认转账")
                .content("向 §6${target.name} §f转账 §6$formattedAmount DDB§f？")
                .button1("确认转账")
                .button2("取消")
                .validResultHandler { response ->
                    player.foliaRun {
                        if (response.clickedButtonId() == 0) {
                            executeTransfer(this, target, amount)
                        } else {
                            openTransferUI(this)
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    player.foliaRun {
                        openTransferUI(this)
                    }
                })
        )
    }

    private fun executeTransfer(player: Player, target: TransferTarget, amount: BigDecimal) {
        if (!target.isOnline()) {
            player.sendDZTError("收款玩家已离线。")
            openTransferUI(player)
            return
        }
        if (!transfersInProgress.add(player.uniqueId)) {
            player.sendDZTError("已有一笔转账正在处理中，请稍后再试。")
            return
        }

        player.sendDZTTip("正在处理向 ${target.name} 的转账……")
        ServiceEconomy.transfer(player.uniqueId, target.uuid, amount).whenComplete { result, error ->
            transfersInProgress.remove(player.uniqueId)
            if (error != null) {
                severe(
                    "ServiceIO 转账调用异常。",
                    "转出玩家 UUID: ${player.uniqueId}",
                    "接收玩家 UUID: ${target.uuid}",
                    error.stackTraceToString(),
                )
                player.sendDZTError("转账失败，经济服务发生异常。")
                return@whenComplete
            }

            notifyTransferResult(player, target, result)
        }
    }

    private fun notifyTransferResult(
        player: Player,
        target: TransferTarget,
        result: EconomyTransferResult,
    ) {
        val amount = ServiceEconomy.formatAmount(result.amount)
        when (result.status) {
            EconomyTransferStatus.SUCCESS -> {
                val balance = result.balance?.let(ServiceEconomy::formatAmount)
                val balanceText = balance?.let { "，当前余额 $it DDB" }.orEmpty()
                player.sendDZTSuccess("已向 ${target.name} 转账 $amount DDB$balanceText。")
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)

                Bukkit.getPlayer(target.uuid)?.takeIf { it.isOnline }?.let { receiver ->
                    receiver.sendDZTSuccess("收到 ${player.name} 的转账 $amount DDB。")
                    receiver.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            EconomyTransferStatus.SERVICE_UNAVAILABLE ->
                player.sendDZTError("ServiceIO 经济服务当前不可用。")

            EconomyTransferStatus.INVALID_AMOUNT ->
                player.sendDZTError("转账金额无效。")

            EconomyTransferStatus.INVALID_PRECISION ->
                player.sendDZTError("转账金额的小数位数不受当前货币支持。")

            EconomyTransferStatus.SENDER_ACCOUNT_UNAVAILABLE ->
                player.sendDZTError("无法读取您的经济账户。")

            EconomyTransferStatus.RECEIVER_ACCOUNT_UNAVAILABLE ->
                player.sendDZTError("无法读取 ${target.name} 的经济账户。")

            EconomyTransferStatus.CURRENCY_NOT_SUPPORTED ->
                player.sendDZTError("转账双方账户不支持当前默认货币。")

            EconomyTransferStatus.INSUFFICIENT_FUNDS -> {
                val balance = result.balance?.let(ServiceEconomy::formatAmount)
                val balanceText = balance?.let { "，当前余额 $it DDB" }.orEmpty()
                player.sendDZTError("余额不足$balanceText。")
            }

            EconomyTransferStatus.WITHDRAWAL_FAILED ->
                player.sendDZTError("转账扣款失败，请稍后再试。")

            EconomyTransferStatus.DEPOSIT_FAILED_REFUNDED ->
                player.sendDZTError("收款账户入账失败，本次扣款已退回。")

            EconomyTransferStatus.DEPOSIT_FAILED_REFUND_FAILED ->
                player.sendDZTError("转账与自动退款均失败，请立即联系管理员核对账户。")
        }
    }
}

private data class TransferTarget(
    val uuid: UUID,
    val name: String,
) {
    fun isOnline(): Boolean {
        return Bukkit.getPlayer(uuid)?.isOnline == true
    }
}
