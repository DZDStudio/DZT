package cn.tj.dzd.mc.dzt.flight

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightSettingStateGuardTest {

    @Test
    fun `new operation invalidates an older read result`() {
        val playerId = UUID.randomUUID()
        val guard = FlightSettingStateGuard()
        val oldRead = guard.beginRead(playerId)

        val mutation = guard.beginMutation(playerId)

        assertFalse(guard.isCurrentRead(playerId, oldRead))
        assertTrue(guard.isCurrentMutation(playerId, mutation))
    }

    @Test
    fun `later read does not invalidate the latest successful mutation`() {
        val playerId = UUID.randomUUID()
        val guard = FlightSettingStateGuard()
        val mutation = guard.beginMutation(playerId)

        val read = guard.beginRead(playerId)

        assertTrue(guard.isCurrentMutation(playerId, mutation))
        assertTrue(guard.isCurrentRead(playerId, read))
    }

    @Test
    fun `older completion cannot clear a newer forced disable`() {
        val playerId = UUID.randomUUID()
        val guard = FlightSettingStateGuard()
        val oldDisable = guard.forceDisable(playerId)
        val newDisable = guard.forceDisable(playerId)

        assertFalse(guard.clearForcedDisable(playerId, oldDisable))
        assertTrue(guard.isForcedDisabled(playerId))
        assertTrue(guard.clearForcedDisable(playerId, newDisable))
        assertFalse(guard.isForcedDisabled(playerId))
    }
}
