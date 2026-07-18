package cn.tj.dzd.mc.dzt.title.command

import cn.tj.dzd.mc.dzt.title.PlayerTitle
import cn.tj.dzd.mc.dzt.title.TitleApi
import cn.tj.dzd.mc.dzt.title.TitleGrantResult
import cn.tj.dzd.mc.dzt.title.TitleRevokeResult
import cn.tj.dzd.mc.dzt.util.foliaRun
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandContext
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.onlinePlayers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 称号管理命令。
 *
 * 命令头默认仅向 OP 授权，每个执行器还会再次检查 [ProxyCommandSender.isOp]，
 * 避免普通玩家被权限插件授予节点后绕过“仅 OP”限制。
 */
@CommandHeader(
    name = "dzt",
    aliases = ["dztadmin"],
    description = "DZT 管理命令",
    usage = "/dzt title",
    permission = "dzt.title.admin",
    // TabooLib 注册命令时会把该字段作为纯文本 Component 构造，不能使用 § 颜色码。
    permissionMessage = "仅服务器 OP 可使用称号管理命令。",
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
        sendLines("§c仅服务器 OP 可使用称号管理命令。")
        return false
    }

    private fun ProxyCommandSender.resolveTarget(input: String): CommandTarget? {
        val normalized = input.trim()
        val uuid = runCatching { UUID.fromString(normalized) }.getOrNull()
        if (uuid != null) {
            val onlineName = onlinePlayers().firstOrNull { it.uniqueId == uuid }?.name
            return CommandTarget(uuid, onlineName ?: uuid.toString())
        }

        val player = onlinePlayers().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        if (player == null) {
            sendLines("§c未找到在线玩家[$normalized]；管理离线玩家请使用 UUID。")
            return null
        }
        return CommandTarget(player.uniqueId, player.name)
    }

    private fun ProxyCommandSender.sendHelp() {
        sendLines(
            "§6称号管理命令：",
            "§e/dzt title give <玩家名/UUID> <ID> \"§f<显示名>§e\" [介绍]",
            "§e/dzt title remove <玩家名/UUID> <ID>",
            "§e/dzt title list <玩家名/UUID>",
            "§8显示名包含空格时请使用英文引号包围，颜色代码可使用 &6 形式。",
        )
    }

    private fun ProxyCommandSender.sendLines(vararg lines: String) {
        sendLines(lines.asList())
    }

    private fun ProxyCommandSender.sendLines(lines: List<String>) {
        val player = castSafely<Player>()
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

    private fun Throwable.readableMessage(): String {
        var current = this
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current.message ?: current.javaClass.simpleName
    }

    private fun onlinePlayerNames(): List<String> {
        return onlinePlayers().map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun String.translateLegacyColorCodes(): String {
        val chars = toCharArray()
        for (index in 0 until chars.lastIndex) {
            if (chars[index] == '&' && chars[index + 1].lowercaseChar() in "0123456789abcdefklmnorx") {
                chars[index] = '§'
            }
        }
        return chars.concatToString()
    }
}
