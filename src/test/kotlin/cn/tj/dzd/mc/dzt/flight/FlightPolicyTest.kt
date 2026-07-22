package cn.tj.dzd.mc.dzt.flight

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightPolicyTest {

    @Test
    fun `plugin manages allowFlight in survival and adventure`() {
        assertTrue(FlightPolicy.managesAllowFlight(FlightGameMode.SURVIVAL))
        assertTrue(FlightPolicy.managesAllowFlight(FlightGameMode.ADVENTURE))
    }

    @Test
    fun `plugin preserves native allowFlight in creative and spectator`() {
        assertFalse(FlightPolicy.managesAllowFlight(FlightGameMode.CREATIVE))
        assertFalse(FlightPolicy.managesAllowFlight(FlightGameMode.SPECTATOR))
    }

    @Test
    fun `enabled flying player is charged in survival and adventure`() {
        assertTrue(
            FlightPolicy.shouldChargeForFlightCheck(
                enabled = true,
                actuallyFlying = true,
                gameMode = FlightGameMode.SURVIVAL,
            ),
        )
        assertTrue(
            FlightPolicy.shouldChargeForFlightCheck(
                enabled = true,
                actuallyFlying = true,
                gameMode = FlightGameMode.ADVENTURE,
            ),
        )
    }

    @Test
    fun `creative and spectator flying are never charged`() {
        assertFalse(
            FlightPolicy.shouldChargeForFlightCheck(
                enabled = true,
                actuallyFlying = true,
                gameMode = FlightGameMode.CREATIVE,
            ),
        )
        assertFalse(
            FlightPolicy.shouldChargeForFlightCheck(
                enabled = true,
                actuallyFlying = true,
                gameMode = FlightGameMode.SPECTATOR,
            ),
        )
    }

    @Test
    fun `disabled or grounded players are not charged`() {
        assertFalse(
            FlightPolicy.shouldChargeForFlightCheck(
                enabled = false,
                actuallyFlying = true,
                gameMode = FlightGameMode.SURVIVAL,
            ),
        )
        assertFalse(
            FlightPolicy.shouldChargeForFlightCheck(
                enabled = true,
                actuallyFlying = false,
                gameMode = FlightGameMode.ADVENTURE,
            ),
        )
    }
}
