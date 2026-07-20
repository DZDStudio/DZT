package cn.tj.dzd.mc.dzt.economy

import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class EconomyTransferEngineTest {

    private val senderId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val receiverId = UUID.fromString("00000000-0000-0000-0000-000000000011")
    private val amount = BigDecimal("25")
    private val engine = EconomyTransferEngine()

    @Test
    fun `same account is rejected before account operations`() {
        val account = FakeEconomyAccount()

        val result = engine.transfer(senderId, senderId, amount, account, account)

        assertEquals(EconomyTransferStatus.INVALID_AMOUNT, result.status)
        assertEquals(0, account.withdrawCalls)
    }

    @Test
    fun `missing sender and receiver remain distinguishable`() {
        assertEquals(
            EconomyTransferStatus.SENDER_ACCOUNT_UNAVAILABLE,
            engine.transfer(senderId, receiverId, amount, null, FakeEconomyAccount()).status,
        )
        assertEquals(
            EconomyTransferStatus.RECEIVER_ACCOUNT_UNAVAILABLE,
            engine.transfer(senderId, receiverId, amount, FakeEconomyAccount(), null).status,
        )
    }

    @Test
    fun `unsupported currency prevents withdrawal`() {
        val sender = FakeEconomyAccount(supportsCurrency = false)

        val result = engine.transfer(senderId, receiverId, amount, sender, FakeEconomyAccount())

        assertEquals(EconomyTransferStatus.CURRENCY_NOT_SUPPORTED, result.status)
        assertEquals(0, sender.withdrawCalls)
    }

    @Test
    fun `withdrawal failure preserves status and balance`() {
        val sender = FakeEconomyAccount(
            withdrawal = EconomyAccountOperation(
                EconomyAccountOperationStatus.INSUFFICIENT_FUNDS,
                BigDecimal("10"),
            )
        )

        val result = engine.transfer(senderId, receiverId, amount, sender, FakeEconomyAccount())

        assertEquals(EconomyTransferStatus.INSUFFICIENT_FUNDS, result.status)
        assertEquals(BigDecimal("10"), result.balance)
    }

    @Test
    fun `successful withdrawal and deposit complete the transfer`() {
        val sender = FakeEconomyAccount(
            withdrawal = EconomyAccountOperation(EconomyAccountOperationStatus.SUCCESS, BigDecimal("75"))
        )
        val receiver = FakeEconomyAccount()

        val result = engine.transfer(senderId, receiverId, amount, sender, receiver)

        assertEquals(EconomyTransferStatus.SUCCESS, result.status)
        assertEquals(BigDecimal("75"), result.balance)
        assertEquals(1, sender.withdrawCalls)
        assertEquals(1, receiver.depositCalls)
        assertEquals(0, sender.depositCalls)
    }

    @Test
    fun `failed receiver deposit triggers a successful sender refund`() {
        val sender = FakeEconomyAccount(
            deposits = mutableListOf(
                EconomyAccountOperation(EconomyAccountOperationStatus.SUCCESS, BigDecimal("100"))
            )
        )
        val receiver = FakeEconomyAccount(
            deposits = mutableListOf(EconomyAccountOperation(EconomyAccountOperationStatus.FAILED))
        )

        val result = engine.transfer(senderId, receiverId, amount, sender, receiver)

        assertEquals(EconomyTransferStatus.DEPOSIT_FAILED_REFUNDED, result.status)
        assertEquals(BigDecimal("100"), result.balance)
        assertEquals(1, sender.depositCalls)
    }

    @Test
    fun `failed receiver deposit and refund require manual reconciliation`() {
        val sender = FakeEconomyAccount(
            deposits = mutableListOf(
                EconomyAccountOperation(EconomyAccountOperationStatus.FAILED, BigDecimal("75"))
            )
        )
        val receiver = FakeEconomyAccount(
            deposits = mutableListOf(EconomyAccountOperation(EconomyAccountOperationStatus.FAILED))
        )

        val result = engine.transfer(senderId, receiverId, amount, sender, receiver)

        assertEquals(EconomyTransferStatus.DEPOSIT_FAILED_REFUND_FAILED, result.status)
        assertEquals(BigDecimal("75"), result.balance)
    }
}

private class FakeEconomyAccount(
    private val supportsCurrency: Boolean = true,
    private val withdrawal: EconomyAccountOperation = EconomyAccountOperation(EconomyAccountOperationStatus.SUCCESS),
    private val deposits: MutableList<EconomyAccountOperation> = mutableListOf(
        EconomyAccountOperation(EconomyAccountOperationStatus.SUCCESS)
    ),
) : EconomyAccountPort {
    var withdrawCalls: Int = 0
        private set
    var depositCalls: Int = 0
        private set

    override fun supportsCurrency(): Boolean = supportsCurrency

    override fun withdraw(amount: BigDecimal): EconomyAccountOperation {
        withdrawCalls++
        return withdrawal
    }

    override fun deposit(amount: BigDecimal): EconomyAccountOperation {
        depositCalls++
        return deposits.removeFirstOrNull() ?: EconomyAccountOperation(EconomyAccountOperationStatus.FAILED)
    }
}
