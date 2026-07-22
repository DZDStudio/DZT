package cn.tj.dzd.mc.dzt.menu.command

import cn.tj.dzd.mc.dzt.menu.ui.Menu
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.PermissionDefault
import taboolib.common.platform.command.mainCommand

/** 用于打开 DZT 主菜单的玩家命令。 */
@CommandHeader(
    name = "cd",
    description = "打开 DZT 主菜单",
    usage = "/cd",
    // 留空会触发 TabooLib 含 § 格式码的默认提示，Paper 会将其报告为 Adventure 兼容警告。
    permissionMessage = "你没有权限执行此命令。",
    permissionDefault = PermissionDefault.TRUE,
    newParser = true,
)
object MenuCommand {

    /**
     * 处理 `/cd` 命令并为执行玩家打开主菜单。
     *
     * 非玩家发送者无法使用界面，因此只会收到错误提示。
     */
    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val player = sender.castSafely<Player>()
            if (player == null) {
                sender.sendMessage("§c该命令只能由玩家执行。")
                return@execute
            }

            with(Menu) {
                player.openMenu()
            }
        }
    }
}
