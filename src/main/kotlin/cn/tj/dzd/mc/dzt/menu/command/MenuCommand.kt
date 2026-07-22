package cn.tj.dzd.mc.dzt.menu.command

import cn.tj.dzd.mc.dzt.menu.ui.Menu
import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand

/** 用于打开 DZT 主菜单的玩家命令。 */
@CommandHeader(
    name = "cd",
    description = "打开 DZT 主菜单",
    usage = "/cd",
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
