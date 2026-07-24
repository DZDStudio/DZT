package cn.tj.dzd.mc.dzt.onebot

import love.forte.simbot.annotations.Api4J
import love.forte.simbot.common.id.LongID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.api.OneBotMessageOutgoing
import love.forte.simbot.component.onebot.v11.core.api.SendGroupMsgApi
import love.forte.simbot.component.onebot.v11.core.api.SendMsgResult
import love.forte.simbot.component.onebot.v11.core.api.requestDataAsync
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 使用 Simbot OneBot 11 typed API 发送 QQ 群纯文本消息的适配器。
 *
 * 每次发送都显式启用 OneBot `auto_escape`，防止调用方传入的文本被解释为 CQ 码。
 */
internal object SimbotOneBotGroupMessageSender : OneBotGroupMessageSender {

    /**
     * 发送指定的 OneBot 群纯文本消息。
     *
     * @param request 目标群、消息及连接参数。
     * @return 成功时承载 DZT 自有消息回执；超时、HTTP 或 OneBot 错误时异常完成。
     */
    @OptIn(Api4J::class)
    override fun sendText(request: OneBotGroupMessageRequest): CompletableFuture<OneBotGroupMessageReceipt> {
        val source = runCatching {
            SendGroupMsgApi.create(
                groupId = request.groupId.ID,
                message = OneBotMessageOutgoing.create(request.message),
                autoEscape = true,
            ).requestDataAsync(
                client = SimbotOneBotHttpClient.client,
                host = request.apiServer,
                accessToken = request.accessToken,
            )
        }.getOrElse { error -> CompletableFuture.failedFuture<SendMsgResult>(error) }

        return awaitReceipt(source, request.groupId, request.timeoutMillis)
    }

    /**
     * 将 Simbot 发送结果投影为 DZT 回执，并在截止时间到达时取消底层 HTTP 协程。
     *
     * @param source Simbot 的原始发送请求。
     * @param groupId 目标群号。
     * @param timeoutMillis 最长等待时间。
     * @return DZT 消息回执 Future。
     */
    private fun awaitReceipt(
        source: CompletableFuture<out SendMsgResult>,
        groupId: Long,
        timeoutMillis: Long,
    ): CompletableFuture<OneBotGroupMessageReceipt> {
        val result = CompletableFuture<OneBotGroupMessageReceipt>()
        val deadline = CompletableFuture<Unit>()
        val completed = AtomicBoolean(false)

        deadline.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .whenComplete { _, error ->
                if (error is TimeoutException && completed.compareAndSet(false, true)) {
                    source.cancel(true)
                    result.completeExceptionally(
                        TimeoutException("OneBot 群消息请求超过 ${timeoutMillis}ms 未完成"),
                    )
                }
            }

        source.whenComplete { response, error ->
            if (!completed.compareAndSet(false, true)) {
                return@whenComplete
            }

            deadline.cancel(false)
            if (error != null) {
                result.completeExceptionally(error)
                return@whenComplete
            }
            if (response == null) {
                result.completeExceptionally(IllegalStateException("OneBot 未返回群消息回执"))
                return@whenComplete
            }

            result.complete(
                OneBotGroupMessageReceipt(
                    groupId = groupId,
                    messageId = response.messageId.value,
                ),
            )
        }

        result.whenComplete { _, _ ->
            if (result.isCancelled && completed.compareAndSet(false, true)) {
                deadline.cancel(false)
                source.cancel(true)
            }
        }
        return result
    }
}
