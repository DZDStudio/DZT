package cn.tj.dzd.mc.dzt.onebot

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OneBotGroupMessagingServiceTest {

    @Test
    fun `sends a text message with the configured OneBot connection`() {
        val receipt = OneBotGroupMessageReceipt(groupId = 747_121_127L, messageId = 123)
        val sender = RecordingSender(CompletableFuture.completedFuture(receipt))

        val result = OneBotGroupMessagingService(sender)
            .send(settings(), 747_121_127L, "服务器将在十分钟后重启")
            .join()

        assertEquals(receipt, result)
        assertEquals(
            OneBotGroupMessageRequest(
                groupId = 747_121_127L,
                message = "服务器将在十分钟后重启",
                apiServer = "http://127.0.0.1:3000",
                accessToken = "token",
                timeoutMillis = 1_000L,
            ),
            sender.request,
        )
    }

    @Test
    fun `sending remains available when group admission is disabled`() {
        val sender = RecordingSender(
            CompletableFuture.completedFuture(OneBotGroupMessageReceipt(636_252_315L, 456)),
        )

        val result = OneBotGroupMessagingService(sender)
            .send(settings(enabled = false), 636_252_315L, "管理通知")
            .join()

        assertEquals(636_252_315L, result.groupId)
        assertEquals(1, sender.requestCount)
    }

    @Test
    fun `blank message and invalid connection are rejected before I O`() {
        val blankSender = RecordingSender(CompletableFuture.completedFuture(OneBotGroupMessageReceipt(1, 1)))
        val blankFailure = assertFailsWith<CompletionException> {
            OneBotGroupMessagingService(blankSender).send(settings(), 747_121_127L, "   ").join()
        }
        assertTrue(blankFailure.cause is IllegalArgumentException)
        assertEquals(0, blankSender.requestCount)

        val invalidSender = RecordingSender(CompletableFuture.completedFuture(OneBotGroupMessageReceipt(1, 1)))
        val invalidFailure = assertFailsWith<CompletionException> {
            OneBotGroupMessagingService(invalidSender)
                .send(settings(apiServer = ""), 747_121_127L, "通知")
                .join()
        }
        assertTrue(invalidFailure.cause is IllegalStateException)
        assertEquals(0, invalidSender.requestCount)
    }

    @Test
    fun `OneBot sender failure remains available to the API caller`() {
        val error = IllegalStateException("OneBot rejected the request")
        val sender = RecordingSender(CompletableFuture.failedFuture(error))

        val failure = assertFailsWith<CompletionException> {
            OneBotGroupMessagingService(sender).send(settings(), 747_121_127L, "通知").join()
        }

        assertEquals(error, failure.cause)
    }

    private fun settings(
        enabled: Boolean = true,
        apiServer: String = "http://127.0.0.1:3000",
    ): QqGroupAdmissionSettings {
        return QqGroupAdmissionSettings(
            enabled = enabled,
            groupId = 747_121_127L,
            apiServer = apiServer,
            accessToken = "token",
            timeoutMillis = 1_000L,
            deniedMessage = "denied",
            unavailableMessage = "unavailable",
        )
    }
}

private class RecordingSender(
    private val response: CompletableFuture<OneBotGroupMessageReceipt>,
) : OneBotGroupMessageSender {
    var request: OneBotGroupMessageRequest? = null
        private set
    var requestCount: Int = 0
        private set

    override fun sendText(request: OneBotGroupMessageRequest): CompletableFuture<OneBotGroupMessageReceipt> {
        requestCount++
        this.request = request
        return response
    }
}
