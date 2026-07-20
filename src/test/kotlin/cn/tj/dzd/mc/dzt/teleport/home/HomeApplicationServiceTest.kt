package cn.tj.dzd.mc.dzt.teleport.home

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HomeApplicationServiceTest {

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val location = StoredLocation("world", 12.5, 64.0, -8.25)

    @Test
    fun `create normalizes the name before persistence`() {
        val repository = FakeHomeRepository()
        val service = HomeApplicationService(repository)

        val result = service.createHome(ownerId, "  base  ", location, iconIndex = 2)

        val success = assertIs<HomeCreateResult.Success>(result)
        assertEquals("base", success.home.name)
        assertEquals(listOf("base"), repository.homes(ownerId).map { it.name })
    }

    @Test
    fun `create rejects blank and overlong names without writing`() {
        val repository = FakeHomeRepository()
        val service = HomeApplicationService(repository)

        assertIs<HomeCreateResult.InvalidName>(service.createHome(ownerId, "   ", location, 1))
        assertIs<HomeCreateResult.InvalidName>(
            service.createHome(ownerId, "a".repeat(HomeApplicationService.MAX_NAME_LENGTH + 1), location, 1)
        )
        assertTrue(repository.homes(ownerId).isEmpty())
    }

    @Test
    fun `create rejects duplicate normalized names`() {
        val repository = FakeHomeRepository()
        val service = HomeApplicationService(repository)

        assertIs<HomeCreateResult.Success>(service.createHome(ownerId, "base", location, 1))
        val duplicate = service.createHome(ownerId, " base ", location, 2)

        assertEquals(HomeCreateResult.DuplicateName("base"), duplicate)
        assertEquals(1, repository.homes(ownerId).size)
    }

    @Test
    fun `create enforces the configured owner limit`() {
        val repository = FakeHomeRepository()
        val service = HomeApplicationService(repository, maximumHomes = 2)

        assertIs<HomeCreateResult.Success>(service.createHome(ownerId, "one", location, 1))
        assertIs<HomeCreateResult.Success>(service.createHome(ownerId, "two", location, 1))

        assertEquals(HomeCreateResult.LimitReached(2), service.createHome(ownerId, "three", location, 1))
    }

    @Test
    fun `repository failures remain distinguishable from empty data`() {
        val repository = FakeHomeRepository().apply { failReads = true }
        val service = HomeApplicationService(repository)

        assertEquals(HomeQueryResult.InfrastructureFailure, service.listHomes(ownerId))
        assertEquals(
            HomeCreateResult.InfrastructureFailure,
            service.createHome(ownerId, "base", location, 1),
        )
    }

    @Test
    fun `delete normalizes names and keeps missing records idempotent`() {
        val repository = FakeHomeRepository()
        val service = HomeApplicationService(repository)
        service.createHome(ownerId, "base", location, 1)

        assertEquals(HomeDeleteResult.Success, service.deleteHome(ownerId, " base "))
        assertEquals(HomeDeleteResult.Success, service.deleteHome(ownerId, "base"))
        assertTrue(repository.homes(ownerId).isEmpty())
    }
}

private class FakeHomeRepository : HomeRepository {
    private val records = mutableMapOf<UUID, MutableList<HomeEntry>>()
    var failReads: Boolean = false

    fun homes(ownerId: UUID): List<HomeEntry> = records[ownerId].orEmpty().toList()

    override fun findAll(ownerId: UUID): RepositoryResult<List<HomeEntry>> {
        if (failReads) {
            return RepositoryResult.Failure
        }
        return RepositoryResult.Success(homes(ownerId))
    }

    override fun insert(ownerId: UUID, home: HomeEntry): RepositoryResult<Unit> {
        records.getOrPut(ownerId, ::mutableListOf).add(home)
        return RepositoryResult.Success(Unit)
    }

    override fun delete(ownerId: UUID, name: String): RepositoryResult<Unit> {
        records[ownerId]?.removeIf { it.name == name }
        return RepositoryResult.Success(Unit)
    }
}
