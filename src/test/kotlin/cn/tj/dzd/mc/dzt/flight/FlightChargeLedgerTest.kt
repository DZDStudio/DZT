package cn.tj.dzd.mc.dzt.flight

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlightChargeLedgerTest {

    @Test
    fun `every sampled flight check remains pending until polled`() {
        val playerId = UUID.randomUUID()
        val ledger = FlightChargeLedger()

        repeat(3) { ledger.enqueue(playerId) }

        assertTrue(ledger.poll(playerId))
        assertTrue(ledger.poll(playerId))
        assertTrue(ledger.hasPending(playerId))
        assertTrue(ledger.poll(playerId))
        assertFalse(ledger.hasPending(playerId))
        assertFalse(ledger.poll(playerId))
    }

    @Test
    fun `clearing one player does not discard another players charges`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val ledger = FlightChargeLedger()
        ledger.enqueue(first)
        ledger.enqueue(second)

        ledger.clear(first)

        assertFalse(ledger.hasPending(first))
        assertTrue(ledger.poll(second))
    }
}
