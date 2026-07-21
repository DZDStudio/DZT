package cn.tj.dzd.mc.dzt.commission.ui

import cn.tj.dzd.mc.dzt.commission.CommissionClaimStatus
import cn.tj.dzd.mc.dzt.commission.CommissionClaimState
import cn.tj.dzd.mc.dzt.commission.CommissionDashboard
import cn.tj.dzd.mc.dzt.commission.CommissionDifficulty
import cn.tj.dzd.mc.dzt.commission.CommissionItemSubmissionCoordinator
import cn.tj.dzd.mc.dzt.commission.CommissionItemSubmissionResult
import cn.tj.dzd.mc.dzt.commission.CommissionItemSubmissionStatus
import cn.tj.dzd.mc.dzt.commission.CommissionObjectiveType
import cn.tj.dzd.mc.dzt.commission.CommissionService
import cn.tj.dzd.mc.dzt.commission.CommissionView
import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.ui.MainMenuNavigation
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendDZTTip
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.buildItem
import java.util.Locale

/** Java 箱子菜单与基岩表单共用的每日委托界面。 */
object CommissionUI {

    private const val TITLE = "§l§6每日委托"
    private const val BACK_ICON = "textures/ui/box_ride.png"

    /**
     * 打开当前玩家的每日委托界面。
     *
     * @param player 要展示界面的在线玩家。
     */
    fun open(player: Player) {
        player.foliaRun {
            loadDashboard(this)
        }
    }

    private fun loadDashboard(player: Player) {
        val playerId = player.uniqueId
        CommissionService.dashboard(playerId).whenComplete { dashboard, error ->
            runForOnlinePlayer(playerId) {
                if (error != null || dashboard == null) {
                    sendDZTError("读取每日委托失败，请稍后重试。")
                    return@runForOnlinePlayer
                }
                if (isBePlayer()) {
                    openBedrock(this, dashboard)
                } else {
                    openJava(this, dashboard)
                }
            }
        }
    }

    private fun openJava(player: Player, dashboard: CommissionDashboard) {
        player.openMenu<Chest>(TITLE) {
            rows(6)
            virtualize()

            map(
                "R###M####",
                "#       #",
                "# A B C #",
                "#  D E  #",
                "#       #",
                "#########",
            )

            onClick(lock = true) {}
            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set('M', XMaterial.WRITABLE_BOOK) {
                name = TITLE
                lore += "§7每日刷新：2 个简单、2 个普通、1 个困难委托"
                lore += "§7日期：§e${dashboard.date}"
            }
            set('R', buildItem(XMaterial.BARREL) {
                name = "§l§e返回主菜单"
            }) {
                MainMenuNavigation.open(player)
            }

            val slots = charArrayOf('A', 'B', 'C', 'D', 'E')
            dashboard.commissions.forEachIndexed { index, commission ->
                set(slots[index], commission.javaItem()) {
                    activate(player, commission)
                }
            }
        }
    }

    private fun openBedrock(player: Player, dashboard: CommissionDashboard) {
        val form = SimpleForm.builder()
            .title(TITLE)
            .content("今日已刷新 5 个委托，完成后可领取弟弟币奖励。")
            .button("返回主菜单", FormImage.Type.PATH, BACK_ICON)

        dashboard.commissions.forEach { commission ->
            form.button(
                commission.bedrockLabel(),
                FormImage.Type.PATH,
                commission.definition.bedrockIcon,
            )
        }

        form.validResultHandler { response ->
            player.foliaRun {
                val clicked = response.clickedButtonId()
                if (clicked == 0) {
                    MainMenuNavigation.open(this)
                    return@foliaRun
                }
                dashboard.commissions.getOrNull(clicked - 1)?.let { commission ->
                    activate(this, commission)
                }
            }
        }
        player.sendForm(form)
    }

    private fun activate(player: Player, commission: CommissionView) {
        when {
            commission.claimState == CommissionClaimState.CLAIMED -> {
                player.sendDZTTip("该委托的奖励已经领取。")
            }

            commission.claimState == CommissionClaimState.CLAIMING -> {
                player.sendDZTTip("该委托奖励正在结算，请稍后再查看。")
            }

            commission.completed -> startClaim(player, commission)
            commission.definition.objectiveType == CommissionObjectiveType.SUBMIT_ITEM -> startItemSubmission(player, commission)
            else -> player.sendDZTTip("请继续完成击杀目标：${commission.progress}/${commission.definition.targetAmount}。")
        }
    }

    private fun startItemSubmission(player: Player, commission: CommissionView) {
        player.withJavaInventoryClosed {
            sendDZTTip("正在上交“${commission.definition.displayName}”所需物品……")
            val playerId = uniqueId
            CommissionItemSubmissionCoordinator.submit(this, commission.definition.id).whenComplete { result, error ->
                runForOnlinePlayer(playerId) {
                    if (error != null || result == null) {
                        sendDZTError("上交物品时发生异常，已停止本次操作。")
                    } else {
                        sendSubmissionResult(result)
                    }
                    open(this)
                }
            }
        }
    }

    private fun startClaim(player: Player, commission: CommissionView) {
        player.withJavaInventoryClosed {
            sendDZTTip("正在结算“${commission.definition.displayName}”的奖励……")
            val playerId = uniqueId
            CommissionService.claim(playerId, commission.definition.id).whenComplete { result, error ->
                runForOnlinePlayer(playerId) {
                    if (error != null || result == null) {
                        sendDZTError("领取委托奖励时发生异常，请稍后重试。")
                    } else {
                        sendClaimResult(result.status, commission)
                    }
                    open(this)
                }
            }
        }
    }

    private fun Player.withJavaInventoryClosed(action: Player.() -> Unit) {
        if (isBePlayer()) {
            action()
            return
        }
        foliaCloseInventory().whenComplete { _, _ ->
            foliaRun(action)
        }
    }

    private fun Player.sendSubmissionResult(result: CommissionItemSubmissionResult) {
        when (result.status) {
            CommissionItemSubmissionStatus.SUCCESS -> {
                val progress = result.currentProgress ?: 0
                val target = result.targetAmount ?: progress
                val completion = if (progress >= target) " 委托已完成，可领取奖励。" else ""
                sendDZTSuccess("已上交 ${result.submittedAmount} 个目标物品，进度 $progress/$target。$completion")
            }

            CommissionItemSubmissionStatus.BUSY -> sendDZTTip("该委托正在处理上交请求。")
            CommissionItemSubmissionStatus.COMMISSION_NOT_FOUND -> sendDZTError("该委托已不在今日列表中。")
            CommissionItemSubmissionStatus.NOT_ITEM_SUBMISSION -> sendDZTError("该委托不是上交物品类型。")
            CommissionItemSubmissionStatus.ALREADY_COMPLETED -> sendDZTTip("该委托已经完成，请直接领取奖励。")
            CommissionItemSubmissionStatus.NO_MATCHING_ITEM -> sendDZTError("背包中没有可上交的目标物品。")
            CommissionItemSubmissionStatus.EXPIRED -> sendDZTError("今日委托已经刷新，扣除的物品已返还。")
            CommissionItemSubmissionStatus.STORAGE_FAILURE -> sendDZTError("进度写入失败，扣除的物品已返还。")
            CommissionItemSubmissionStatus.RESTORE_QUEUED -> {
                sendDZTTip("物品已登记为待返还；背包有空间后重新登录即可继续领取。")
            }
            CommissionItemSubmissionStatus.RESTORE_FAILURE -> sendDZTError("物品返还异常，已记录到控制台，请联系管理员。")
            CommissionItemSubmissionStatus.PLAYER_UNAVAILABLE -> sendDZTError("玩家状态不可用，无法完成上交。")
        }
    }

    private fun Player.sendClaimResult(status: CommissionClaimStatus, commission: CommissionView) {
        when (status) {
            CommissionClaimStatus.SUCCESS -> {
                sendDZTSuccess(
                    "已领取“${commission.definition.displayName}”奖励：" +
                        "${ServiceEconomy.formatAmount(commission.definition.reward)} DDB。"
                )
            }

            CommissionClaimStatus.COMMISSION_NOT_FOUND -> sendDZTError("该委托已不在今日列表中。")
            CommissionClaimStatus.NOT_COMPLETED -> sendDZTTip("委托尚未完成，暂时不能领取奖励。")
            CommissionClaimStatus.EXPIRED -> sendDZTError("今日委托已经刷新，无法领取上一日的奖励。")
            CommissionClaimStatus.ALREADY_CLAIMED -> sendDZTTip("该委托的奖励已经领取。")
            CommissionClaimStatus.CLAIM_IN_PROGRESS -> sendDZTTip("该委托奖励正在结算，请稍后查看。")
            CommissionClaimStatus.REWARD_FAILED -> sendDZTError("奖励入账失败，未消耗领取资格，请稍后重试。")
            CommissionClaimStatus.RECORD_FAILURE -> sendDZTError("奖励结算状态异常，已记录到控制台，请联系管理员。")
        }
    }

    private fun CommissionView.javaItem(): ItemStack {
        return buildItem(resolveXMaterial(definition.javaIcon)) {
            name = "§l${difficultyColor(definition.difficulty)}${definition.displayName}"
            lore += "§7难度：${difficultyColor(definition.difficulty)}${definition.difficulty.displayName}"
            definition.description.forEach { line -> lore += "§7$line" }
            lore += ""
            lore += "§f目标：§e${objectiveText()}"
            lore += "§f进度：§b$progress§7/§b${definition.targetAmount}"
            lore += "§f奖励：§6${ServiceEconomy.formatAmount(definition.reward)} DDB"
            lore += ""
            lore += actionText()
        }
    }

    private fun CommissionView.bedrockLabel(): String {
        return buildString {
            append(definition.displayName)
            append('\n')
            append("§7${definition.difficulty.displayName} | ${objectiveText()} | $progress/${definition.targetAmount}")
            append('\n')
            append("§6${ServiceEconomy.formatAmount(definition.reward)} DDB §7| ")
            append(actionText())
        }
    }

    private fun CommissionView.objectiveText(): String {
        val target = definition.targetId.substringAfter(':')
        return when (definition.objectiveType) {
            CommissionObjectiveType.SUBMIT_ITEM -> "上交 ${definition.targetAmount} 个 $target"
            CommissionObjectiveType.KILL_ENTITY -> "击杀 ${definition.targetAmount} 个 $target"
        }
    }

    private fun CommissionView.actionText(): String {
        return when {
            claimState == CommissionClaimState.CLAIMED -> "§8奖励已领取"
            claimState == CommissionClaimState.CLAIMING -> "§e奖励结算中"
            completed -> "§a点击领取奖励"
            definition.objectiveType == CommissionObjectiveType.SUBMIT_ITEM -> "§e点击上交背包中的目标物品"
            else -> "§7继续完成击杀目标"
        }
    }

    private fun difficultyColor(difficulty: CommissionDifficulty): String {
        return when (difficulty) {
            CommissionDifficulty.SIMPLE -> "§a"
            CommissionDifficulty.NORMAL -> "§e"
            CommissionDifficulty.HARD -> "§c"
        }
    }

    private fun resolveXMaterial(materialId: String): XMaterial {
        val name = materialId.substringAfter(':', materialId).uppercase(Locale.ROOT)
        return XMaterial.matchXMaterial(name).orElse(XMaterial.BARRIER)
    }
}
