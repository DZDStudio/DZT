package cn.tj.dzd.mc.dzt.chat

import cn.tj.dzd.mc.dzt.title.TitleService
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.networkPing
import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 全服聊天格式增强。
 *
 * 格式：`[time] JE/BE | 42ms <[称号]DisplayName> message`。
 */
object ChatEnhancement {

    private val beijingZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        val player = event.player
        val time = LocalTime.now(beijingZone).format(timeFormatter)
        val client = if (player.isBePlayer()) "BE" else "JE"
        val ping = player.networkPing().coerceAtLeast(0)
        val title = TitleService.getCachedEquippedTitle(player.uniqueId)
        val titleComponent = title?.let {
            Component.text("[", NamedTextColor.YELLOW)
                .append(legacySerializer.deserialize(it.displayName).colorIfAbsent(NamedTextColor.YELLOW))
                .append(Component.text("]", NamedTextColor.YELLOW))
        } ?: Component.empty()

        event.renderer(
            ChatRenderer.viewerUnaware { _, sourceDisplayName, message ->
                Component.empty()
                    .append(Component.text("[$time]", NamedTextColor.GRAY))
                    .append(Component.space())
                    .append(
                        Component.text(
                            client,
                            if (client == "BE") NamedTextColor.LIGHT_PURPLE else NamedTextColor.GREEN
                        )
                    )
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text("${ping}ms", NamedTextColor.GREEN))
                    .append(Component.text(" <", NamedTextColor.GOLD))
                    .append(titleComponent)
                    .append(sourceDisplayName.colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text("> ", NamedTextColor.GOLD))
                    .append(message.colorIfAbsent(NamedTextColor.WHITE))
            }
        )
    }
}
