package cn.tj.dzd.mc.dzt.teleport.back

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackApplicationServiceTest {

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val location = StoredLocation("minecraft:world", 0.0, 64.0, 0.0)

    @Test
    fun `record keeps only the newest configured entries`() {
        val repository = FakeBackRepository()
        val service = BackApplicationService(repository, maximumBacks = 2)

        assertEquals(BackMutationResult.SUCCESS, service.recordBack(ownerId, 100, location))
        assertEquals(BackMutationResult.SUCCESS, service.recordBack(ownerId, 300, location))
        assertEquals(BackMutationResult.SUCCESS, service.recordBack(ownerId, 200, location))

        assertEquals(listOf(300L, 200L), repository.backs(ownerId).map(BackEntry::time).sortedDescending())
    }

    @Test
    fun `list always returns newest entries first`() {
        val repository = FakeBackRepository().apply {
            insert(ownerId, BackEntry(20, location))
            insert(ownerId, BackEntry(10, location))
            insert(ownerId, BackEntry(30, location))
        }
        val service = BackApplicationService(repository)

        val result = assertIs<BackQueryResult.Success>(service.listBacks(ownerId))

        assertEquals(listOf(30L, 20L, 10L), result.backs.map(BackEntry::time))
    }

    @Test
    fun `read failure is not reported as an empty list`() {
        val repository = FakeBackRepository().apply { failReads = true }
        val service = BackApplicationService(repository)

        assertEquals(BackQueryResult.InfrastructureFailure, service.listBacks(ownerId))
        assertEquals(
            BackMutationResult.INFRASTRUCTURE_FAILURE,
            service.recordBack(ownerId, 100, location),
        )
    }
}

private class FakeBackRepository : BackRepository {
    private val records = mutableMapOf<UUID, MutableList<BackEntry>>()
    var failReads: Boolean = false

    fun backs(ownerId: UUID): List<BackEntry> = records[ownerId].orEmpty().toList()

    override fun findAll(ownerId: UUID): RepositoryResult<List<BackEntry>> {
        return if (failReads) RepositoryResult.Failure else RepositoryResult.Success(backs(ownerId))
    }

    override fun insert(ownerId: UUID, back: BackEntry): RepositoryResult<Unit> {
        records.getOrPut(ownerId, ::mutableListOf).add(back)
        return RepositoryResult.Success(Unit)
    }

    override fun delete(ownerId: UUID, time: Long): RepositoryResult<Unit> {
        records[ownerId]?.removeIf { it.time == time }
        return RepositoryResult.Success(Unit)
    }
}
