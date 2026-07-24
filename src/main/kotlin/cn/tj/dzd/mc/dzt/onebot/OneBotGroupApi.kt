package cn.tj.dzd.mc.dzt.onebot

import java.util.concurrent.CompletableFuture

private const val PLAYER_EXCHANGE_GROUP_ID = 747_121_127L
private const val MANAGEMENT_GROUP_ID = 636_252_315L

/**
 * OneBot 群消息发送成功后的回执。
 *
 * @property groupId 已发送到的 QQ 群号。
 * @property messageId OneBot 返回的消息 ID。
 */
data class OneBotGroupMessageReceipt(
    val groupId: Long,
    val messageId: Int,
)

/**
 * DZT OneBot 固定 QQ 群消息对外 API。
 *
 * 方法均为非阻塞调用，可从 Folia 的实体、区域或异步线程发起；不要对返回的
 * [CompletableFuture] 在 Folia 实体或区域线程上调用 `join` 或 `get`。
 *
 * OneBot 连接参数复用 `config.yml` 的 `onebot.group-admission` 节点，
 * 但 `group-admission.enable` 仅控制玩家准入，不影响本 API 发消息。
 */
object OneBotGroupApi {

    private val messagingService = OneBotGroupMessagingService(SimbotOneBotGroupMessageSender)

    /**
     * 向玩家交流群（QQ：747121127）发送一条纯文本消息。
     *
     * 文本会以 `auto_escape=true` 发送，不会将调用方内容中的 CQ 码解释为 OneBot 消息段。
     * 消息为空、OneBot 连接配置无效、请求超时或 OneBot 拒绝请求时，返回的 Future 会异常完成。
     *
     * @param message 要发送的非空纯文本。
     * @return 成功时承载目标群号与 OneBot 消息 ID 的异步回执。
     */
    @JvmStatic
    fun sendPlayerGroupMessage(message: String): CompletableFuture<OneBotGroupMessageReceipt> {
        return send(message, PLAYER_EXCHANGE_GROUP_ID)
    }

    /**
     * 向管理群（QQ：636252315）发送一条纯文本消息。
     *
     * 文本会以 `auto_escape=true` 发送，不会将调用方内容中的 CQ 码解释为 OneBot 消息段。
     * 消息为空、OneBot 连接配置无效、请求超时或 OneBot 拒绝请求时，返回的 Future 会异常完成。
     *
     * @param message 要发送的非空纯文本。
     * @return 成功时承载目标群号与 OneBot 消息 ID 的异步回执。
     */
    @JvmStatic
    fun sendManagementGroupMessage(message: String): CompletableFuture<OneBotGroupMessageReceipt> {
        return send(message, MANAGEMENT_GROUP_ID)
    }

    private fun send(message: String, groupId: Long): CompletableFuture<OneBotGroupMessageReceipt> {
        val settings = runCatching(QqGroupAdmissionSettings::fromConfig)
            .getOrElse { error -> return failedFuture(error) }
        return messagingService.send(settings, groupId, message)
    }
}

/** OneBot 群消息发送所需的内部请求参数。 */
internal data class OneBotGroupMessageRequest(
    val groupId: Long,
    val message: String,
    val apiServer: String,
    val accessToken: String?,
    val timeoutMillis: Long,
)

/**
 * 发送 OneBot 群纯文本消息的端口。
 *
 * 实现负责 HTTP/Simbot 通信；上层 API 不暴露 Simbot 的消息或响应类型。
 */
internal interface OneBotGroupMessageSender {

    /**
     * 异步发送一条纯文本群消息。
     *
     * 调用方可能取消返回的 Future；实现必须把取消传递给底层 I/O。
     *
     * @param request 目标群、消息内容及 OneBot 连接参数。
     * @return 成功时承载消息回执；请求或 OneBot 返回失败时异常完成。
     */
    fun sendText(request: OneBotGroupMessageRequest): CompletableFuture<OneBotGroupMessageReceipt>
}

/** 将固定群 API 的输入校验与 OneBot 发送端口组合的内部服务。 */
internal class OneBotGroupMessagingService(
    private val sender: OneBotGroupMessageSender,
) {

    /**
     * 发起一次群消息发送。
     *
     * 本方法不读取 `settings.enabled`，使群消息能力与玩家准入开关保持独立。
     *
     * @param settings 当前 OneBot 配置快照。
     * @param groupId 目标 QQ 群号。
     * @param message 要发送的非空纯文本。
     * @return 成功时承载 OneBot 消息回执；参数、配置或请求失败时异常完成。
     */
    fun send(
        settings: QqGroupAdmissionSettings,
        groupId: Long,
        message: String,
    ): CompletableFuture<OneBotGroupMessageReceipt> {
        if (groupId <= 0) {
            return failedFuture(IllegalArgumentException("目标 QQ 群号必须大于 0"))
        }
        if (message.isBlank()) {
            return failedFuture(IllegalArgumentException("OneBot 群消息不能为空"))
        }
        settings.connectionValidationError()?.let { error ->
            return failedFuture(IllegalStateException("OneBot 连接配置无效：$error"))
        }

        val request = OneBotGroupMessageRequest(
            groupId = groupId,
            message = message,
            apiServer = settings.apiServer,
            accessToken = settings.accessToken,
            timeoutMillis = settings.timeoutMillis,
        )
        return runCatching { sender.sendText(request) }.getOrElse(::failedFuture)
    }
}

private fun <T> failedFuture(error: Throwable): CompletableFuture<T> {
    return CompletableFuture<T>().apply { completeExceptionally(error) }
}
