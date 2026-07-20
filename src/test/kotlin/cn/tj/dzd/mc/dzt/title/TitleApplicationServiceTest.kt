package cn.tj.dzd.mc.dzt.title

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TitleApplicationServiceTest {

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000003")

    @Test
    fun `grant normalizes fields and uses injected clock`() {
        val repository = FakeTitleRepository()
        val service = TitleApplicationService(repository) { 1234L }

        val result = service.grant(ownerId, " EVENT.WINNER ", "  §6冠军  ", " 一\r\n二 ")

        assertEquals(TitleGrantResult.GRANTED, result)
        assertEquals(
            PlayerTitle("event.winner", "§6冠军", "一\n二", 1234L, false),
            repository.titles(ownerId).single(),
        )
    }

    @Test
    fun `grant maps duplicate and infrastructure outcomes`() {
        val repository = FakeTitleRepository()
        val service = TitleApplicationService(repository) { 1L }

        assertEquals(TitleGrantResult.GRANTED, service.grant(ownerId, "winner", "Winner", ""))
        assertEquals(TitleGrantResult.ALREADY_OWNED, service.grant(ownerId, "winner", "Winner", ""))

        repository.fail = true
        assertEquals(TitleGrantResult.FAILED, service.grant(ownerId, "another", "Another", ""))
    }

    @Test
    fun `owned titles are sorted by acquisition time`() {
        val repository = FakeTitleRepository().apply {
            insertIfAbsent(ownerId, PlayerTitle("later", "Later", "", 20L, false))
            insertIfAbsent(ownerId, PlayerTitle("first", "First", "", 10L, false))
        }
        val service = TitleApplicationService(repository)

        assertEquals(listOf("first", "later"), service.getOwned(ownerId).map(PlayerTitle::id))
    }

    @Test
    fun `equip decisions preserve existing public result semantics`() {
        val repository = FakeTitleRepository().apply {
            insertIfAbsent(ownerId, PlayerTitle("winner", "Winner", "", 10L, false))
        }
        val service = TitleApplicationService(repository)

        assertEquals(TitleEquipResult.NOT_OWNED, service.equip(ownerId, "missing"))
        assertEquals(TitleEquipResult.EQUIPPED, service.equip(ownerId, " WINNER "))
        assertEquals(TitleEquipResult.ALREADY_EQUIPPED, service.equip(ownerId, "winner"))
        assertEquals("winner", service.getEquipped(ownerId)?.id)
        assertEquals(TitleEquipResult.UNEQUIPPED, service.unequip(ownerId))
        assertEquals(null, service.getEquipped(ownerId))
    }

    @Test
    fun `revoke distinguishes absent records and storage failures`() {
        val repository = FakeTitleRepository()
        val service = TitleApplicationService(repository)

        assertEquals(TitleRevokeResult.NOT_OWNED, service.revoke(ownerId, "missing"))
        repository.insertIfAbsent(ownerId, PlayerTitle("winner", "Winner", "", 10L, false))
        assertEquals(TitleRevokeResult.REVOKED, service.revoke(ownerId, " WINNER "))

        repository.fail = true
        assertEquals(TitleRevokeResult.FAILED, service.revoke(ownerId, "missing"))
    }
}

private class FakeTitleRepository : TitleRepository {
    private val records = mutableMapOf<UUID, MutableList<PlayerTitle>>()
    var fail: Boolean = false

    fun titles(ownerId: UUID): List<PlayerTitle> = records[ownerId].orEmpty().toList()

    override fun insertIfAbsent(ownerId: UUID, title: PlayerTitle): RepositoryResult<Boolean> {
        if (fail) return RepositoryResult.Failure
        val titles = records.getOrPut(ownerId, ::mutableListOf)
        if (titles.any { it.id == title.id }) return RepositoryResult.Success(false)
        titles += title
        return RepositoryResult.Success(true)
    }

    override fun delete(ownerId: UUID, titleId: String): RepositoryResult<Boolean> {
        if (fail) return RepositoryResult.Failure
        return RepositoryResult.Success(records[ownerId]?.removeIf { it.id == titleId } == true)
    }

    override fun findAll(ownerId: UUID): RepositoryResult<List<PlayerTitle>> {
        return if (fail) RepositoryResult.Failure else RepositoryResult.Success(titles(ownerId))
    }

    override fun findEquipped(ownerId: UUID): RepositoryResult<PlayerTitle?> {
        return if (fail) {
            RepositoryResult.Failure
        } else {
            RepositoryResult.Success(titles(ownerId).firstOrNull(PlayerTitle::equipped))
        }
    }

    override fun equip(ownerId: UUID, titleId: String): RepositoryResult<TitleEquipDecision> {
        if (fail) return RepositoryResult.Failure
        val titles = records[ownerId].orEmpty()
        val selected = titles.firstOrNull { it.id == titleId }
            ?: return RepositoryResult.Success(TitleEquipDecision.NOT_OWNED)
        if (selected.equipped) return RepositoryResult.Success(TitleEquipDecision.ALREADY_EQUIPPED)
        records[ownerId] = titles.mapTo(mutableListOf()) {
            it.copy(equipped = it.id == titleId)
        }
        return RepositoryResult.Success(TitleEquipDecision.EQUIPPED)
    }

    override fun unequip(ownerId: UUID): RepositoryResult<Unit> {
        if (fail) return RepositoryResult.Failure
        records[ownerId] = records[ownerId].orEmpty().mapTo(mutableListOf()) { it.copy(equipped = false) }
        return RepositoryResult.Success(Unit)
    }
}
