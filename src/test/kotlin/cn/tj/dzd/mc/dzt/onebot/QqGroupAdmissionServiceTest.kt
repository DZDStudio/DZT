package cn.tj.dzd.mc.dzt.onebot

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QqGroupAdmissionServiceTest {

    @Test
    fun `a group card containing a Java player name permits login`() {
        val directory = FakeGroupMemberDirectory(
            CompletableFuture.completedFuture(listOf("DZT-Steve-管理员")),
        )

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(), "Steve", null)
            .join()

        assertEquals(QqGroupAdmissionResult.Allowed, result)
        assertEquals(1, directory.requestCount)
    }

    @Test
    fun `matching direction is group card contains player name`() {
        val directory = FakeGroupMemberDirectory(
            CompletableFuture.completedFuture(listOf("Steve")),
        )

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(), "SteveTheBuilder", null)
            .join()

        assertEquals(
            QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.NO_MATCHING_GROUP_CARD),
            result,
        )
    }

    @Test
    fun `Bedrock prefix is removed before a group card is checked`() {
        val directory = FakeGroupMemberDirectory(
            CompletableFuture.completedFuture(listOf("欢迎 Steve 加入 DZT")),
        )

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(), "#Steve", "#")
            .join()

        assertEquals(QqGroupAdmissionResult.Allowed, result)
    }

    @Test
    fun `Java player names are never stripped`() {
        val directory = FakeGroupMemberDirectory(
            CompletableFuture.completedFuture(listOf("teven")),
        )

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(), "Steven", null)
            .join()

        assertEquals(
            QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.NO_MATCHING_GROUP_CARD),
            result,
        )
    }

    @Test
    fun `empty effective Bedrock name is denied without querying OneBot`() {
        val directory = FakeGroupMemberDirectory(
            CompletableFuture.completedFuture(listOf("任意群名片")),
        )

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(), "#", "#")
            .join()

        assertEquals(
            QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.INVALID_PLAYER_NAME),
            result,
        )
        assertEquals(0, directory.requestCount)
    }

    @Test
    fun `empty group card and different case do not match`() {
        assertFalse(QqGroupAdmissionPolicy.hasMatchingGroupCard(listOf(""), "Steve"))
        assertFalse(QqGroupAdmissionPolicy.hasMatchingGroupCard(listOf("steve"), "Steve"))
    }

    @Test
    fun `OneBot failure is denied`() {
        val directory = FakeGroupMemberDirectory(
            CompletableFuture.failedFuture(IllegalStateException("OneBot unavailable")),
        )

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(), "Steve", null)
            .join()

        assertEquals(
            QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.ONEBOT_UNAVAILABLE),
            result,
        )
    }

    @Test
    fun `OneBot timeout is denied and cancels the pending request`() {
        val pendingResponse = CompletableFuture<List<String>>()
        val directory = FakeGroupMemberDirectory(pendingResponse)

        val result = QqGroupAdmissionService(directory)
            .verifyAsync(settings(timeoutMillis = QqGroupAdmissionSettings.MIN_TIMEOUT_MILLIS), "Steve", null)
            .join()

        assertEquals(
                QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.ONEBOT_UNAVAILABLE),
            result,
        )
        assertTrue(pendingResponse.isCancelled)
    }

    @Test
    fun `disabled and invalid configuration do not call OneBot`() {
        val disabledDirectory = FakeGroupMemberDirectory(CompletableFuture.failedFuture(IllegalStateException()))
        val disabledResult = QqGroupAdmissionService(disabledDirectory)
            .verifyAsync(settings(enabled = false), "Steve", null)
            .join()

        assertEquals(QqGroupAdmissionResult.Allowed, disabledResult)
        assertEquals(0, disabledDirectory.requestCount)

        val invalidDirectory = FakeGroupMemberDirectory(CompletableFuture.completedFuture(emptyList()))
        val invalidResult = QqGroupAdmissionService(invalidDirectory)
            .verifyAsync(settings(groupId = 0L), "Steve", null)
            .join()

        assertEquals(
            QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.INVALID_CONFIGURATION),
            invalidResult,
        )
        assertEquals(0, invalidDirectory.requestCount)
    }

    private fun settings(
        enabled: Boolean = true,
        groupId: Long = 123_456L,
        timeoutMillis: Long = 1_000L,
    ): QqGroupAdmissionSettings {
        return QqGroupAdmissionSettings(
            enabled = enabled,
            groupId = groupId,
            apiServer = "http://127.0.0.1:3000",
            accessToken = null,
            timeoutMillis = timeoutMillis,
            deniedMessage = "denied",
            unavailableMessage = "unavailable",
        )
    }
}

private class FakeGroupMemberDirectory(
    private val response: CompletableFuture<List<String>>,
) : OneBotGroupMemberDirectory {
    var requestCount: Int = 0
        private set

    override fun groupMemberCards(request: OneBotGroupMemberRequest): CompletableFuture<List<String>> {
        requestCount++
        return response
    }
}
