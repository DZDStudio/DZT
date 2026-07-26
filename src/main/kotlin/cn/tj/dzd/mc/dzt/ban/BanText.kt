package cn.tj.dzd.mc.dzt.ban

import net.kyori.adventure.text.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 玩家封禁消息与时间的统一格式化工具。 */
internal object BanText {

    /**
     * 将 Unix 毫秒时间戳格式化为北京时间。
     *
     * @param epochMillis Unix 毫秒时间戳。
     * @return 用于管理命令与封禁提示的可读时间。
     */
    fun formatTime(epochMillis: Long): String {
        return BEIJING_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis))
    }

    /**
     * 构建玩家登录时看到的封禁拒绝信息。
     *
     * 原因以纯文本 Component 写入，避免管理员输入的格式字符影响拒绝消息结构。
     *
     * @param ban 当前有效封禁记录。
     * @return 登录拒绝消息。
     */
    fun loginDeniedMessage(ban: PlayerBan): Component {
        return Component.text(
            "你已被封禁。\n原因：${ban.reason}\n预计解封时间：${formatTime(ban.unbanAt)}（北京时间）"
        )
    }

    /** 数据库状态不可用时的登录拒绝信息。 */
    fun storageUnavailableMessage(): Component {
        return Component.text("封禁状态暂时无法验证，请稍后重试或联系管理员。")
    }

    /**
     * 构建发送到玩家 QQ 交流群的封禁公示。
     *
     * @param playerName 被封禁玩家的显示名称；空白名称会回退为 UUID。
     * @param ban 已成功写入的封禁记录。
     * @param hours 本次封禁时使用的小时数，可为小数。
     * @return 不含 CQ 码的纯文本公示。
     */
    fun playerGroupAnnouncement(playerName: String, ban: PlayerBan, hours: BigDecimal): String {
        val label = playerName
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
            .ifBlank { ban.playerId.toString() }
        val displayHours = hours.stripTrailingZeros().toPlainString()
        return when {
            ban.type.blocksServerEntry -> {
                "[封禁公示] 玩家${label}因${ban.reason}封禁${displayHours}小时。"
            }

            ban.type.blocksFlight -> {
                "[飞行封禁公示] 玩家${label}因${ban.reason}禁止飞行${displayHours}小时。"
            }

            else -> {
                "[封禁公示] 玩家${label}因${ban.reason}受到${ban.type.value}类型封禁${displayHours}小时。"
            }
        }
    }

    private val BEIJING_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("Asia/Shanghai"))
}
