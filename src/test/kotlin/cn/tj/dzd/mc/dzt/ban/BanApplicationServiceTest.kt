package cn.tj.dzd.mc.dzt.ban

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BanApplicationServiceTest {

    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val firstRecordId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val secondRecordId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val thirdRecordId = UUID.fromString("00000000-0000-0000-0000-000000000203")

    @Test
    fun `ban stores a normalized reason and an hourly expiry`() {
        val repository = FakeBanRepository()
        val service = BanApplicationService(repository, { 1_000L }) { firstRecordId }

        val result = assertIs<BanIssueResult.Banned>(
            service.ban(playerId, 2L, "  破坏\r\n建筑  ")
        )

        assertEquals(
            PlayerBan(
                recordId = firstRecordId,
                playerId = playerId,
                bannedAt = 1_000L,
                unbanAt = 7_201_000L,
                reason = "破坏 建筑",
                active = true,
                releasedAt = 0L,
            ),
            result.record,
        )
        assertEquals(listOf(result.record), repository.records())
    }

    @Test
    fun `decimal hour durations are converted precisely to milliseconds`() {
        val repository = FakeBanRepository()
        val service = BanApplicationService(repository, { 1_000L }) { firstRecordId }

        val result = assertIs<BanIssueResult.Banned>(
            service.ban(playerId, BigDecimal("0.1"), "测试")
        )

        assertEquals(361_000L, result.record.unbanAt)
    }

    @Test
    fun `a later ban preserves the previous record and replaces its enforcement`() {
        val repository = FakeBanRepository()
        var now = 1_000L
        val recordIds = ArrayDeque(listOf(firstRecordId, secondRecordId))
        val service = BanApplicationService(repository, { now }) { recordIds.removeFirst() }

        service.ban(playerId, 10L, "第一次")
        now = 2_000L
        val replacement = assertIs<BanIssueResult.Banned>(service.ban(playerId, 1L, "第二次"))

        val history = assertIs<BanHistoryResult.Available>(service.getHistory(playerId)).records
        assertEquals(listOf(secondRecordId, firstRecordId), history.map(PlayerBan::recordId))
        assertEquals(2_000L, history[1].releasedAt)
        assertEquals(false, history[1].active)
        assertEquals(36_001_000L, history[1].unbanAt)
        assertEquals(secondRecordId, replacement.record.recordId)
        assertEquals(BanLookupResult.Active(replacement.record), service.getActiveBan(playerId))
    }

    @Test
    fun `different ban types coexist and only replace their own type`() {
        val repository = FakeBanRepository()
        var now = 1_000L
        val recordIds = ArrayDeque(listOf(firstRecordId, secondRecordId, thirdRecordId))
        val service = BanApplicationService(repository, { now }) { recordIds.removeFirst() }

        val serverBan = assertIs<BanIssueResult.Banned>(
            service.ban(playerId, 10L, "服务器封禁", BanType.BAN)
        ).record
        now = 2_000L
        val firstFlyBan = assertIs<BanIssueResult.Banned>(
            service.ban(playerId, 10L, "飞行封禁", BanType.FLY)
        ).record
        now = 3_000L
        val replacementFlyBan = assertIs<BanIssueResult.Banned>(
            service.ban(playerId, 1L, "新的飞行封禁", BanType.FLY)
        ).record

        assertEquals(BanLookupResult.Active(serverBan), service.getActiveBan(playerId, BanType.BAN))
        assertEquals(BanLookupResult.Active(replacementFlyBan), service.getActiveBan(playerId, BanType.FLY))

        val records = assertIs<BanHistoryResult.Available>(service.getHistory(playerId)).records
        val originalFlyRecord = records.single { it.recordId == firstFlyBan.recordId }
        assertEquals(false, originalFlyRecord.active)
        assertEquals(3_000L, originalFlyRecord.releasedAt)
        assertEquals(true, serverBan.active)
    }

    @Test
    fun `manual unban retains the scheduled expiry in history`() {
        val repository = FakeBanRepository()
        var now = 1_000L
        val service = BanApplicationService(repository, { now }) { firstRecordId }

        service.ban(playerId, 3L, "测试")
        now = 2_000L

        assertEquals(UnbanResult.UNBANNED, service.unban(playerId))
        assertEquals(BanLookupResult.NotBanned, service.getActiveBan(playerId))

        val record = assertIs<BanHistoryResult.Available>(service.getHistory(playerId)).records.single()
        assertEquals(false, record.active)
        assertEquals(2_000L, record.releasedAt)
        assertEquals(10_801_000L, record.unbanAt)
        assertEquals("测试", record.reason)
    }

    @Test
    fun `unban only releases the requested ban type`() {
        val repository = FakeBanRepository()
        var now = 1_000L
        val recordIds = ArrayDeque(listOf(firstRecordId, secondRecordId))
        val service = BanApplicationService(repository, { now }) { recordIds.removeFirst() }

        val serverBan = assertIs<BanIssueResult.Banned>(
            service.ban(playerId, 3L, "服务器封禁", BanType.BAN)
        ).record
        now = 2_000L
        service.ban(playerId, 3L, "飞行封禁", BanType.FLY)

        now = 3_000L
        assertEquals(UnbanResult.UNBANNED, service.unban(playerId, BanType.FLY))
        assertEquals(BanLookupResult.Active(serverBan), service.getActiveBan(playerId, BanType.BAN))
        assertEquals(BanLookupResult.NotBanned, service.getActiveBan(playerId, BanType.FLY))
    }

    @Test
    fun `expired bans no longer block login but remain queryable`() {
        val repository = FakeBanRepository()
        var now = 1_000L
        val service = BanApplicationService(repository, { now }) { firstRecordId }

        service.ban(playerId, 1L, "测试")
        now = 3_601_000L

        assertEquals(BanLookupResult.NotBanned, service.getActiveBan(playerId))
        val record = assertIs<BanHistoryResult.Available>(service.getHistory(playerId)).records.single()
        assertEquals(true, record.active)
        assertEquals(0L, record.releasedAt)
    }

    @Test
    fun `storage failures and invalid inputs are surfaced explicitly`() {
        val repository = FakeBanRepository().apply { fail = true }
        val service = BanApplicationService(repository, { 1_000L }) { firstRecordId }

        assertEquals(BanIssueResult.Failed, service.ban(playerId, 1L, "测试"))
        assertEquals(UnbanResult.FAILED, service.unban(playerId))
        assertEquals(BanLookupResult.Unavailable, service.getActiveBan(playerId))
        assertEquals(BanHistoryResult.Unavailable, service.getHistory(playerId))
        assertFailsWith<IllegalArgumentException> { service.ban(playerId, 0L, "测试") }
        assertFailsWith<IllegalArgumentException> { service.ban(playerId, 1L, "x".repeat(257)) }
    }
}

private class FakeBanRepository : BanRepository {

    private val storedRecords = mutableListOf<PlayerBan>()
    var fail: Boolean = false

    fun records(): List<PlayerBan> = storedRecords.toList()

    override fun createReplacingActive(record: PlayerBan): RepositoryResult<Unit> {
        if (fail) {
            return RepositoryResult.Failure
        }
        storedRecords.replaceAll { existing ->
            if (existing.type == record.type && existing.isEffectiveAt(record.bannedAt)) {
                existing.copy(active = false, releasedAt = record.bannedAt)
            } else {
                existing
            }
        }
        storedRecords += record
        return RepositoryResult.Success(Unit)
    }

    override fun releaseActive(
        playerId: UUID,
        type: BanType,
        releasedAt: Long,
    ): RepositoryResult<Boolean> {
        if (fail) {
            return RepositoryResult.Failure
        }
        var released = false
        storedRecords.replaceAll { existing ->
            if (
                existing.playerId == playerId &&
                existing.type == type &&
                existing.isEffectiveAt(releasedAt)
            ) {
                released = true
                existing.copy(active = false, releasedAt = releasedAt)
            } else {
                existing
            }
        }
        return RepositoryResult.Success(released)
    }

    override fun findActive(
        playerId: UUID,
        type: BanType,
        currentTimeMillis: Long,
    ): RepositoryResult<PlayerBan?> {
        if (fail) {
            return RepositoryResult.Failure
        }
        return RepositoryResult.Success(
            storedRecords
                .asSequence()
                .filter {
                    it.playerId == playerId &&
                        it.type == type &&
                        it.isEffectiveAt(currentTimeMillis)
                }
                .maxWithOrNull(compareBy<PlayerBan>(PlayerBan::bannedAt).thenBy { it.recordId.toString() })
        )
    }

    override fun findHistory(playerId: UUID): RepositoryResult<List<PlayerBan>> {
        return if (fail) {
            RepositoryResult.Failure
        } else {
            RepositoryResult.Success(storedRecords.filter { it.playerId == playerId })
        }
    }
}
