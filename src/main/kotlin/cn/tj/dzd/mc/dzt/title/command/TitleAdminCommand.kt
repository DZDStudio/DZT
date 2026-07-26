package cn.tj.dzd.mc.dzt.title.command

import cn.tj.dzd.mc.dzt.ban.BanApi
import cn.tj.dzd.mc.dzt.ban.BanHistoryResult
import cn.tj.dzd.mc.dzt.ban.BanIssueResult
import cn.tj.dzd.mc.dzt.ban.BanService
import cn.tj.dzd.mc.dzt.ban.BanText
import cn.tj.dzd.mc.dzt.ban.BanType
import cn.tj.dzd.mc.dzt.ban.PlayerBan
import cn.tj.dzd.mc.dzt.ban.UnbanResult
import cn.tj.dzd.mc.dzt.onebot.OneBotGroupApi
import cn.tj.dzd.mc.dzt.onebot.OneBotGroupMessageReceipt
import cn.tj.dzd.mc.dzt.title.PlayerTitle
import cn.tj.dzd.mc.dzt.title.TitleApi
import cn.tj.dzd.mc.dzt.title.TitleGrantResult
import cn.tj.dzd.mc.dzt.title.TitleRevokeResult
import cn.tj.dzd.mc.dzt.util.bukkitPlayerOrNull
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.flight.FlightService
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandContext
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.warning
import taboolib.platform.util.onlinePlayers
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * DZT 管理命令。
 *
 * 命令头默认仅向 OP 授权，每个执行器还会再次检查 [ProxyCommandSender.isOp]，
 * 避免普通玩家被权限插件授予节点后绕过“仅 OP”限制。
 */
@CommandHeader(
    name = "dzt",
    aliases = ["dztadmin"],
    description = "DZT 管理命令",
    usage = "/dzt <title|ban|unban|banhistory|onebot>",
    permission = "dzt.admin",
    // TabooLib 注册命令时会把该字段作为纯文本 Component 构造，不能使用 § 颜色码。
    permissionMessage = "仅服务器 OP 可使用 DZT 管理命令。",
    permissionDefault = PermissionDefault.OP,
    newParser = true,
)
object TitleAdminCommand {

    private val beijingTimeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"))

    private data class CommandTarget(val uuid: UUID, val label: String)

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (sender.requireOp()) {
                sender.sendHelp()
            }
        }
    }

    @CommandBody(aliases = ["titles"], description = "管理玩家称号")
    val title = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (sender.requireOp()) {
                sender.sendHelp()
            }
        }

        literal("give", "grant", description = "授予玩家称号") {
            dynamic("player") {
                suggestionUncheck<ProxyCommandSender> { _, _ -> onlinePlayerNames() }
                dynamic("id") {
                    dynamic("displayName") {
                        execute<ProxyCommandSender> { sender, context, _ ->
                            executeGrant(sender, context, "")
                        }
                        dynamic("description", optional = true) {
                            execute<ProxyCommandSender> { sender, context, argument ->
                                executeGrant(sender, context, argument)
                            }
                        }
                    }
                }
            }
        }

        literal("remove", "revoke", "take", description = "移除玩家称号") {
            dynamic("player") {
                suggestionUncheck<ProxyCommandSender> { _, _ -> onlinePlayerNames() }
                dynamic("id") {
                    execute<ProxyCommandSender> { sender, context, _ ->
                        executeRevoke(sender, context)
                    }
                }
            }
        }

        literal("list", description = "查看玩家已拥有称号") {
            dynamic("player") {
                suggestionUncheck<ProxyCommandSender> { _, _ -> onlinePlayerNames() }
                execute<ProxyCommandSender> { sender, context, _ ->
                    executeList(sender, context)
                }
            }
        }
    }

    @CommandBody(description = "按小时封禁玩家，可指定封禁类型")
    val ban = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ -> onlinePlayerNames() }
            dynamic("hours") {
                execute<ProxyCommandSender> { sender, context, _ ->
                    executeBan(sender, context, "", BanType.BAN)
                }
                literal("type", "scope", description = "指定封禁类型") {
                    dynamic("banType") {
                        suggestionUncheck<ProxyCommandSender> { _, _ -> defaultBanTypeSuggestions() }
                        execute<ProxyCommandSender> { sender, context, _ ->
                            val type = sender.resolveBanType(context["banType"]) ?: return@execute
                            executeBan(sender, context, "", type)
                        }
                        dynamic("reason", optional = true) {
                            execute<ProxyCommandSender> { sender, context, argument ->
                                val type = sender.resolveBanType(context["banType"]) ?: return@execute
                                executeBan(sender, context, argument, type)
                            }
                        }
                    }
                }
                dynamic("reason", optional = true) {
                    execute<ProxyCommandSender> { sender, context, argument ->
                        executeBan(sender, context, argument, BanType.BAN)
                    }
                }
            }
        }
    }

    @CommandBody(description = "解除玩家指定类型的当前封禁")
    val unban = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ -> onlinePlayerNames() }
            execute<ProxyCommandSender> { sender, context, _ ->
                executeUnban(sender, context, BanType.BAN)
            }
            dynamic("banType", optional = true) {
                suggestionUncheck<ProxyCommandSender> { _, _ -> defaultBanTypeSuggestions() }
                execute<ProxyCommandSender> { sender, context, _ ->
                    val type = sender.resolveBanType(context["banType"]) ?: return@execute
                    executeUnban(sender, context, type)
                }
            }
        }
    }

    @CommandBody(aliases = ["banrecords", "banlog"], description = "查询玩家封禁历史")
    val banhistory = subCommand {
        dynamic("player") {
            suggestionUncheck<ProxyCommandSender> { _, _ -> onlinePlayerNames() }
            execute<ProxyCommandSender> { sender, context, _ ->
                executeBanHistory(sender, context, 1)
            }
            dynamic("page", optional = true) {
                execute<ProxyCommandSender> { sender, context, argument ->
                    val page = argument.toIntOrNull()
                    if (page == null || page <= 0) {
                        sender.sendLines("§c页码必须是大于 0 的整数。")
                        return@execute
                    }
                    executeBanHistory(sender, context, page)
                }
            }
        }
    }

    /**
     * 向 DZT 固定 QQ 群发送纯文本消息。
     *
     * 用法：`/dzt onebot player <消息>` 向玩家交流群发送，
     * `/dzt onebot management <消息>` 向管理群发送。末尾消息参数会接收所有剩余文本。
     */
    @CommandBody(aliases = ["qq"], description = "发送 OneBot QQ 群消息")
    val onebot = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            if (sender.requireOp()) {
                sender.sendOneBotHelp()
            }
        }

        literal("player", "players", "community", description = "向玩家交流群发送消息") {
            dynamic("message") {
                execute<ProxyCommandSender> { sender, _, message ->
                    sender.sendOneBotGroupMessage(
                        message = message,
                        groupLabel = "玩家交流群",
                        send = OneBotGroupApi::sendPlayerGroupMessage,
                    )
                }
            }
        }

        literal("management", "admin", "manage", description = "向管理群发送消息") {
            dynamic("message") {
                execute<ProxyCommandSender> { sender, _, message ->
                    sender.sendOneBotGroupMessage(
                        message = message,
                        groupLabel = "管理群",
                        send = OneBotGroupApi::sendManagementGroupMessage,
                    )
                }
            }
        }
    }

    private fun executeGrant(
        sender: ProxyCommandSender,
        context: CommandContext<ProxyCommandSender>,
        description: String,
    ) {
        if (!sender.requireOp()) {
            return
        }
        val target = sender.resolveTarget(context["player"]) ?: return
        val titleId = context["id"]
        val displayName = context["displayName"].translateLegacyColorCodes()
        val normalizedDescription = description.translateLegacyColorCodes()

        sender.sendLines("§e正在向 ${target.label} 授予称号……")
        TitleApi.grantTitle(target.uuid, titleId, displayName, normalizedDescription).whenComplete { result, error ->
            if (error != null) {
                sender.sendLines("§c授予称号失败：${error.readableMessage()}")
                return@whenComplete
            }

            when (result) {
                TitleGrantResult.GRANTED -> sender.sendLines(
                    "§a已向 ${target.label} 授予称号 §f$titleId§a。"
                )
                TitleGrantResult.ALREADY_OWNED -> sender.sendLines(
                    "§e${target.label} 已拥有称号 §f$titleId§e。"
                )
                TitleGrantResult.FAILED,
                null -> sender.sendLines("§c向 ${target.label} 授予称号失败。")
            }
        }
    }

    private fun executeRevoke(
        sender: ProxyCommandSender,
        context: CommandContext<ProxyCommandSender>,
    ) {
        if (!sender.requireOp()) {
            return
        }
        val target = sender.resolveTarget(context["player"]) ?: return
        val titleId = context["id"]

        sender.sendLines("§e正在移除 ${target.label} 的称号……")
        TitleApi.revokeTitle(target.uuid, titleId).whenComplete { result, error ->
            if (error != null) {
                sender.sendLines("§c移除称号失败：${error.readableMessage()}")
                return@whenComplete
            }

            when (result) {
                TitleRevokeResult.REVOKED -> sender.sendLines(
                    "§a已移除 ${target.label} 的称号 §f$titleId§a。"
                )
                TitleRevokeResult.NOT_OWNED -> sender.sendLines(
                    "§e${target.label} 未拥有称号 §f$titleId§e。"
                )
                TitleRevokeResult.FAILED,
                null -> sender.sendLines("§c移除 ${target.label} 的称号失败。")
            }
        }
    }

    private fun executeBan(
        sender: ProxyCommandSender,
        context: CommandContext<ProxyCommandSender>,
        reason: String,
        type: BanType,
    ) {
        if (!sender.requireOp()) {
            return
        }
        val target = sender.resolveTarget(context["player"]) ?: return
        val hours = runCatching { BigDecimal(context["hours"].trim()) }.getOrNull()
        if (hours == null || hours.signum() <= 0) {
            sender.sendLines("§c封禁时长必须是大于 0 的小时数，可使用小数。")
            return
        }
        val displayHours = hours.stripTrailingZeros().toPlainString()

        sender.sendLines("§e正在以 ${type.value} 类型封禁 ${target.label}……")
        BanApi.ban(target.uuid, hours, reason, type).whenComplete { result, error ->
            if (error != null) {
                sender.sendLines("§c封禁 ${target.label} 失败：${error.readableMessage()}")
                return@whenComplete
            }

            when (result) {
                is BanIssueResult.Banned -> {
                    sender.sendLines(
                        "§a已对 ${target.label} 执行 ${type.value} 类型封禁 ${displayHours} 小时，预计解封时间：" +
                            "§f${BanText.formatTime(result.record.unbanAt)} §a北京时间。"
                    )
                    if (result.record.type.blocksServerEntry) {
                        BanService.disconnectOnlinePlayer(result.record)
                    }
                    if (result.record.type.blocksFlight) {
                        FlightService.applyFlightBan(result.record)
                    }
                    publishBanAnnouncement(target.label, result.record, hours)
                }

                BanIssueResult.Failed,
                null -> sender.sendLines("§c封禁 ${target.label} 失败。")
            }
        }
    }

    /**
     * 向玩家交流群异步发送封禁公示。
     *
     * OneBot 是外部依赖，发送失败只写入控制台告警，不能影响已经持久化的封禁结果。
     *
     * @param playerName 公示中展示的玩家名称。
     * @param ban 已成功写入的封禁记录。
     * @param hours 本次封禁使用的小时数。
     */
    private fun publishBanAnnouncement(playerName: String, ban: PlayerBan, hours: BigDecimal) {
        val message = BanText.playerGroupAnnouncement(playerName, ban, hours)
        val future = runCatching {
            OneBotGroupApi.sendPlayerGroupMessage(message)
        }.getOrElse { error ->
            warning("封禁公示发送失败（玩家：$playerName）：${error.readableMessage()}")
            return
        }
        future.whenComplete { _, error ->
            if (error != null) {
                warning("封禁公示发送失败（玩家：$playerName）：${error.readableMessage()}")
            }
        }
    }

    private fun executeUnban(
        sender: ProxyCommandSender,
        context: CommandContext<ProxyCommandSender>,
        type: BanType,
    ) {
        if (!sender.requireOp()) {
            return
        }
        val target = sender.resolveTarget(context["player"]) ?: return

        sender.sendLines("§e正在解除 ${target.label} 的 ${type.value} 类型封禁……")
        BanApi.unban(target.uuid, type).whenComplete { result, error ->
            if (error != null) {
                sender.sendLines("§c解除 ${target.label} 的封禁失败：${error.readableMessage()}")
                return@whenComplete
            }

            when (result) {
                UnbanResult.UNBANNED -> {
                    if (type.blocksFlight) {
                        FlightService.clearFlightBan(target.uuid)
                    }
                    sender.sendLines("§a已解除 ${target.label} 的 ${type.value} 类型封禁，历史记录已保留。")
                }

                UnbanResult.NOT_BANNED -> sender.sendLines("§e${target.label} 当前没有 ${type.value} 类型封禁。")
                UnbanResult.FAILED,
                null -> sender.sendLines("§c解除 ${target.label} 的 ${type.value} 类型封禁失败。")
            }
        }
    }

    private fun executeBanHistory(
        sender: ProxyCommandSender,
        context: CommandContext<ProxyCommandSender>,
        requestedPage: Int,
    ) {
        if (!sender.requireOp()) {
            return
        }
        val target = sender.resolveTarget(context["player"]) ?: return

        BanApi.getBanHistory(target.uuid).whenComplete { result, error ->
            if (error != null) {
                sender.sendLines("§c读取 ${target.label} 的封禁历史失败：${error.readableMessage()}")
                return@whenComplete
            }
            when (result) {
                is BanHistoryResult.Available -> sender.sendBanHistory(target, result.records, requestedPage)
                BanHistoryResult.Unavailable,
                null -> sender.sendLines("§c读取 ${target.label} 的封禁历史失败。")
            }
        }
    }

    /**
     * 调用固定 QQ 群 API 并将异步执行结果回显给命令发送者。
     *
     * 此方法绝不等待 Future 完成；完成回调中的 [sendLines] 会将玩家反馈调度到其 Folia
     * 实体线程，控制台反馈则直接发送。
     *
     * @param message 要发送的纯文本消息。
     * @param groupLabel 命令反馈使用的目标群名称。
     * @param send 对应固定群的异步发送 API。
     */
    private fun ProxyCommandSender.sendOneBotGroupMessage(
        message: String,
        groupLabel: String,
        send: (String) -> CompletableFuture<OneBotGroupMessageReceipt>,
    ) {
        if (!requireOp()) {
            return
        }
        if (message.isBlank()) {
            sendLines("§c消息不能为空。")
            return
        }

        sendLines("§e正在向${groupLabel}发送消息……")
        val future = runCatching { send(message) }.getOrElse { error ->
            sendLines("§c发送至${groupLabel}失败：${error.readableMessage()}")
            return
        }
        future.whenComplete { receipt, error ->
            if (error != null) {
                sendLines("§c发送至${groupLabel}失败：${error.readableMessage()}")
                return@whenComplete
            }
            if (receipt == null) {
                sendLines("§c发送至${groupLabel}失败：OneBot 未返回消息回执。")
                return@whenComplete
            }
            sendLines("§a消息已发送至${groupLabel}（消息 ID：${receipt.messageId}）。")
        }
    }

    private fun executeList(
        sender: ProxyCommandSender,
        context: CommandContext<ProxyCommandSender>,
    ) {
        if (!sender.requireOp()) {
            return
        }
        val target = sender.resolveTarget(context["player"]) ?: return

        TitleApi.getOwnedTitles(target.uuid).whenComplete { titles, error ->
            if (error != null) {
                sender.sendLines("§c读取称号失败：${error.readableMessage()}")
                return@whenComplete
            }
            if (titles.isNullOrEmpty()) {
                sender.sendLines("§e${target.label} 尚未拥有称号。")
                return@whenComplete
            }

            sender.sendLines(
                buildList {
                    add("§6${target.label} 的称号（${titles.size}）：")
                    titles.forEach { title ->
                        add(title.toCommandLine())
                    }
                }
            )
        }
    }

    private fun ProxyCommandSender.requireOp(): Boolean {
        if (isOp) {
            return true
        }
        sendLines("§c仅服务器 OP 可使用 DZT 管理命令。")
        return false
    }

    /** 解析命令中的封禁类型，并向命令发送者说明格式限制。 */
    private fun ProxyCommandSender.resolveBanType(input: String): BanType? {
        return BanType.fromCommand(input) ?: run {
            sendLines("§c封禁类型必须为 1-16 位小写字母、数字、下划线或连字符，且以字母开头。")
            null
        }
    }

    private fun ProxyCommandSender.resolveTarget(input: String): CommandTarget? {
        val normalized = input.trim()
        val uuid = runCatching { UUID.fromString(normalized) }.getOrNull()
        if (uuid != null) {
            val onlineName = onlinePlayers.firstOrNull { it.uniqueId == uuid }?.name
            return CommandTarget(uuid, onlineName ?: uuid.toString())
        }

        val player = onlinePlayers.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        if (player == null) {
            sendLines("§c未找到在线玩家[$normalized]；管理离线玩家请使用 UUID。")
            return null
        }
        return CommandTarget(player.uniqueId, player.name)
    }

    private fun ProxyCommandSender.sendHelp() {
        sendLines(
            "§6DZT 管理命令：",
            "§e/dzt title give <玩家名/UUID> <ID> \"§f<显示名>§e\" [介绍]",
            "§e/dzt title remove <玩家名/UUID> <ID>",
            "§e/dzt title list <玩家名/UUID>",
            "§e/dzt ban <玩家名/UUID> <小时数，可为小数> [原因] §7- 默认 ban 类型",
            "§e/dzt ban <玩家名/UUID> <小时数，可为小数> type <类型> [原因]",
            "§e/dzt unban <玩家名/UUID> [类型] §7- 默认解除 ban 类型",
            "§e/dzt banhistory <玩家名/UUID> [页码]",
            "§e/dzt onebot player <消息>",
            "§e/dzt onebot management <消息>",
            "§8含空格的显示名或原因请使用英文引号包围；称号颜色代码可使用 &6 形式。",
        )
    }

    private fun ProxyCommandSender.sendOneBotHelp() {
        sendLines(
            "§6OneBot QQ 群消息命令：",
            "§e/dzt onebot player <消息> §7- 发送至玩家交流群",
            "§e/dzt onebot management <消息> §7- 发送至管理群",
            "§8可使用 /dzt qq 作为 onebot 的别名；消息可直接包含空格。",
        )
    }

    private fun ProxyCommandSender.sendBanHistory(
        target: CommandTarget,
        records: List<PlayerBan>,
        requestedPage: Int,
    ) {
        if (records.isEmpty()) {
            sendLines("§e${target.label} 暂无封禁历史。")
            return
        }

        val pageCount = (records.size + BAN_HISTORY_PAGE_SIZE - 1) / BAN_HISTORY_PAGE_SIZE
        if (requestedPage > pageCount) {
            sendLines("§e${target.label} 的封禁历史只有 $pageCount 页。")
            return
        }

        val fromIndex = (requestedPage - 1) * BAN_HISTORY_PAGE_SIZE
        val currentTimeMillis = System.currentTimeMillis()
        val lines = buildList {
            add("§6${target.label} 的封禁历史（第 $requestedPage/$pageCount 页，共 ${records.size} 条）：")
            records.subList(fromIndex, minOf(fromIndex + BAN_HISTORY_PAGE_SIZE, records.size)).forEach { record ->
                add(record.toHistoryLine(currentTimeMillis))
            }
        }
        sendLines(lines)
    }

    private fun ProxyCommandSender.sendLines(vararg lines: String) {
        sendLines(lines.asList())
    }

    private fun ProxyCommandSender.sendLines(lines: List<String>) {
        val player = bukkitPlayerOrNull()
        if (player != null) {
            player.foliaRun {
                lines.forEach { line -> sendMessage(line) }
            }
        } else {
            lines.forEach(::sendMessage)
        }
    }

    private fun PlayerTitle.toCommandLine(): String {
        val equippedMarker = if (equipped) "§a[佩戴] " else "§7[未佩戴] "
        val descriptionText = description
            .replace('\n', ' ')
            .ifBlank { "暂无介绍" }
        val time = beijingTimeFormatter.format(Instant.ofEpochMilli(grantedAt))
        return "$equippedMarker§f$id §7- §r$displayName §8| $descriptionText | $time 北京时间"
    }

    private fun PlayerBan.toHistoryLine(currentTimeMillis: Long): String {
        val status = when {
            isEffectiveAt(currentTimeMillis) -> "§c生效中"
            !active && releasedAt > 0L -> "§a已提前解除：${BanText.formatTime(releasedAt)}"
            !active -> "§a已提前解除"
            else -> "§7已自然到期"
        }
        return "§8#${recordId.toString().take(8)} §7类型：§f${type.value} §7封禁：${BanText.formatTime(bannedAt)} " +
            "§7预计解封：${BanText.formatTime(unbanAt)} §7状态：$status §8原因：§f$reason"
    }

    private fun Throwable.readableMessage(): String {
        var current = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current.message ?: current.javaClass.simpleName
    }

    private fun onlinePlayerNames(): List<String> {
        return onlinePlayers.map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun defaultBanTypeSuggestions(): List<String> = listOf(BanType.BAN.value, BanType.FLY.value)

    private fun String.translateLegacyColorCodes(): String {
        val chars = toCharArray()
        for (index in 0 until chars.lastIndex) {
            if (chars[index] == '&' && chars[index + 1].lowercaseChar() in "0123456789abcdefklmnorx") {
                chars[index] = '§'
            }
        }
        return chars.concatToString()
    }

    private const val BAN_HISTORY_PAGE_SIZE = 10
}
