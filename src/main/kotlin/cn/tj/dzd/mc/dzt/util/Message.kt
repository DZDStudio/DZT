package cn.tj.dzd.mc.dzt.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * DZT 玩家消息类型。
 *
 * 用于控制消息主体颜色，消息前缀会由 [dztMessage] 统一追加。
 */
enum class DZTMessageType(val bodyColor: NamedTextColor) {
    /** 普通消息，主体为白色。 */
    NORMAL(NamedTextColor.WHITE),

    /** 提示消息，主体为黄色。 */
    TIP(NamedTextColor.YELLOW),

    /** 成功消息，主体为绿色。 */
    SUCCESS(NamedTextColor.GREEN),

    /** 失败或错误消息，主体为红色。 */
    ERROR(NamedTextColor.RED)
}

private val DZT_COIN_YELLOW = NamedTextColor.GOLD
private val DZT_PREFIX = Component.empty()
    .append(Component.text("[", DZT_COIN_YELLOW))
    .append(Component.text("DZT"))
    .append(Component.text("]", DZT_COIN_YELLOW))
    .append(Component.space())
    .decoration(TextDecoration.BOLD, true)

/**
 * 构建统一的 DZT 玩家消息组件。
 *
 * @param message 不含前缀的消息主体。
 * @param type 消息类型，用于决定主体颜色。
 * @return 已追加 `[DZT] ` 前缀、整体加粗并完成配色的 Adventure 组件。
 */
fun dztMessage(message: String, type: DZTMessageType = DZTMessageType.NORMAL): Component {
    return DZT_PREFIX.append(
        Component.text(message, type.bodyColor)
            .decoration(TextDecoration.BOLD, true)
    )
}

/**
 * 构建统一的 DZT 玩家消息组件。
 *
 * 该重载用于需要富文本主体的场景；主体根组件会按 [type] 设置颜色并加粗。
 *
 * @param body 不含前缀的消息主体组件。
 * @param type 消息类型，用于决定主体颜色。
 * @return 已追加 `[DZT] ` 前缀、整体加粗并完成配色的 Adventure 组件。
 */
fun dztMessage(body: Component, type: DZTMessageType = DZTMessageType.NORMAL): Component {
    return DZT_PREFIX.append(
        body.color(type.bodyColor)
            .decoration(TextDecoration.BOLD, true)
    )
}

/**
 * Folia 兼容地发送统一格式的 DZT 玩家消息。
 *
 * @param message 不含前缀的消息主体。
 * @param type 消息类型，用于决定主体颜色。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.sendDZTMessage(
    message: String,
    type: DZTMessageType = DZTMessageType.NORMAL,
): CompletableFuture<Boolean> {
    return foliaRun {
        sendMessage(dztMessage(message, type))
    }
}

/**
 * Folia 兼容地发送统一格式的 DZT 富文本玩家消息。
 *
 * @param message 不含前缀的消息主体组件。
 * @param type 消息类型，用于决定主体颜色。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.sendDZTMessage(
    message: Component,
    type: DZTMessageType = DZTMessageType.NORMAL,
): CompletableFuture<Boolean> {
    return foliaRun {
        sendMessage(dztMessage(message, type))
    }
}

/**
 * Folia 兼容地发送普通 DZT 玩家消息。
 *
 * @param message 不含前缀的消息主体。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.sendDZTNormal(message: String): CompletableFuture<Boolean> {
    return sendDZTMessage(message, DZTMessageType.NORMAL)
}

/**
 * Folia 兼容地发送提示 DZT 玩家消息。
 *
 * @param message 不含前缀的消息主体。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.sendDZTTip(message: String): CompletableFuture<Boolean> {
    return sendDZTMessage(message, DZTMessageType.TIP)
}

/**
 * Folia 兼容地发送成功 DZT 玩家消息。
 *
 * @param message 不含前缀的消息主体。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.sendDZTSuccess(message: String): CompletableFuture<Boolean> {
    return sendDZTMessage(message, DZTMessageType.SUCCESS)
}

/**
 * Folia 兼容地发送失败或错误 DZT 玩家消息。
 *
 * @param message 不含前缀的消息主体。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.sendDZTError(message: String): CompletableFuture<Boolean> {
    return sendDZTMessage(message, DZTMessageType.ERROR)
}
