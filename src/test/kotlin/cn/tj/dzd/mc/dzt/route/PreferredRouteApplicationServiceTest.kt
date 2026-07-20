package cn.tj.dzd.mc.dzt.route

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferredRouteApplicationServiceTest {

    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000004")

    @Test
    fun `query returns the stored qualification`() {
        val service = PreferredRouteApplicationService(
            FakePreferredRouteRepository(RepositoryResult.Success(true))
        )

        assertTrue(service.isPreferred(ownerId))
    }

    @Test
    fun `storage failure remains fail closed`() {
        val service = PreferredRouteApplicationService(
            FakePreferredRouteRepository(RepositoryResult.Failure)
        )

        assertFalse(service.isPreferred(ownerId))
    }
}

private class FakePreferredRouteRepository(
    private val result: RepositoryResult<Boolean>,
) : PreferredRouteRepository {
    override fun contains(ownerId: UUID): RepositoryResult<Boolean> = result
}
