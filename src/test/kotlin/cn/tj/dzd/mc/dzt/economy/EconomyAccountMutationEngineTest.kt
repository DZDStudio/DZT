package cn.tj.dzd.mc.dzt.economy

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EconomyAccountMutationEngineTest {

    private val playerId = UUID.fromString("00000000-0000-0000-0000-000000000020")
    private val receiverId = UUID.fromString("00000000-0000-0000-0000-000000000021")
    private val amount = BigDecimal("25")
    private val engine = EconomyTransferEngine()

    @Test
    fun `invalid amount and missing account do not invoke a mutation`() {
        val account = ScriptedEconomyAccount()

        assertEquals(
            EconomyAccountMutationStatus.INVALID_AMOUNT,
            engine.withdraw(playerId, BigDecimal.ZERO, account).status,
        )
        assertEquals(
            EconomyAccountMutationStatus.ACCOUNT_UNAVAILABLE,
            engine.withdraw(playerId, amount, null).status,
        )
        assertEquals(0, account.withdrawCalls)
    }

    @Test
    fun `withdrawal preserves insufficient-funds status and balance`() {
        val account = ScriptedEconomyAccount(
            withdrawal = EconomyAccountOperation(
                EconomyAccountOperationStatus.INSUFFICIENT_FUNDS,
                BigDecimal("10"),
            )
        )

        val result = engine.withdraw(playerId, amount, account)

        assertEquals(EconomyAccountMutationStatus.INSUFFICIENT_FUNDS, result.status)
        assertEquals(BigDecimal("10"), result.balance)
        assertFalse(result.successful)
        assertEquals(1, account.withdrawCalls)
    }

    @Test
    fun `unsupported currency is checked before a withdrawal`() {
        val account = ScriptedEconomyAccount(supportsCurrency = false)

        val result = engine.withdraw(playerId, amount, account)

        assertEquals(EconomyAccountMutationStatus.CURRENCY_NOT_SUPPORTED, result.status)
        assertEquals(0, account.withdrawCalls)
    }

    @Test
    fun `deposit reports its own failure status`() {
        val account = ScriptedEconomyAccount(
            deposits = mutableListOf(
                EconomyAccountOperation(EconomyAccountOperationStatus.FAILED, BigDecimal("75"))
            )
        )

        val result = engine.deposit(playerId, amount, account)

        assertEquals(EconomyAccountMutationStatus.DEPOSIT_FAILED, result.status)
        assertEquals(BigDecimal("75"), result.balance)
        assertFalse(result.successful)
        assertEquals(1, account.depositCalls)
    }

    @Test
    fun `withdrawal and transfer serialize mutations for the same player`() {
        val sender = BlockingEconomyAccount()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val withdrawal = executor.submit<EconomyAccountMutationResult> {
                engine.withdraw(playerId, amount, sender)
            }
            assertTrue(sender.firstWithdrawalEntered.await(1, TimeUnit.SECONDS))

            val transfer = executor.submit<EconomyTransferResult> {
                engine.transfer(playerId, receiverId, amount, sender, ScriptedEconomyAccount())
            }
            assertFalse(
                sender.secondWithdrawalEntered.await(200, TimeUnit.MILLISECONDS),
                "The transfer must wait for the existing single-account withdrawal lock.",
            )

            sender.releaseFirstWithdrawal.countDown()

            assertEquals(EconomyAccountMutationStatus.SUCCESS, withdrawal.get(1, TimeUnit.SECONDS).status)
            assertEquals(EconomyTransferStatus.SUCCESS, transfer.get(1, TimeUnit.SECONDS).status)
            assertEquals(2, sender.withdrawCalls)
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
        }
    }
}

private class ScriptedEconomyAccount(
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

private class BlockingEconomyAccount : EconomyAccountPort {

    val firstWithdrawalEntered = CountDownLatch(1)
    val secondWithdrawalEntered = CountDownLatch(1)
    val releaseFirstWithdrawal = CountDownLatch(1)

    private val withdrawalCount = AtomicInteger()

    val withdrawCalls: Int
        get() = withdrawalCount.get()

    override fun supportsCurrency(): Boolean = true

    override fun withdraw(amount: BigDecimal): EconomyAccountOperation {
        when (withdrawalCount.incrementAndGet()) {
            1 -> {
                firstWithdrawalEntered.countDown()
                check(releaseFirstWithdrawal.await(1, TimeUnit.SECONDS))
            }

            2 -> secondWithdrawalEntered.countDown()
        }
        return EconomyAccountOperation(EconomyAccountOperationStatus.SUCCESS)
    }

    override fun deposit(amount: BigDecimal): EconomyAccountOperation {
        return EconomyAccountOperation(EconomyAccountOperationStatus.SUCCESS)
    }
}
