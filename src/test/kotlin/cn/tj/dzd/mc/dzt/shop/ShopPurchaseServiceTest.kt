package cn.tj.dzd.mc.dzt.shop

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShopPurchaseServiceTest {

    private val playerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000100")
    private val product = ShopProduct(
        id = "oak_log",
        displayName = "橡木原木",
        materialId = "minecraft:oak_log",
        bedrockIcon = "textures/blocks/log_oak.png",
        price = BigDecimal("10"),
        dailyLimit = 10,
    )
    private val catalog = ShopCatalog(
        listOf(
            ShopCategory(
                id = "wood",
                displayName = "木材",
                javaIcon = "minecraft:oak_log",
                bedrockIcon = "textures/blocks/log_oak.png",
                products = listOf(product),
            )
        )
    )
    private val date = LocalDate.of(2026, 7, 20)
    private val clock = Clock.fixed(
        Instant.parse("2026-07-20T16:00:01Z"),
        ZoneId.of("UTC"),
    )

    @Test
    fun `availability uses Beijing date and reports remaining quantity`() {
        val repository = FakeRepository().apply {
            quantities[Key(playerId, product.id, date.plusDays(1))] = 7
        }
        val service = service(repository = repository)

        val availability = service.availability(playerId, product.id).join()

        assertEquals(ShopAvailabilityStatus.AVAILABLE, availability.status)
        assertEquals(3, availability.remaining)
    }

    @Test
    fun `successful purchase reserves limit withdraws DDB and delivers items`() {
        val repository = FakeRepository()
        val wallet = FakeWallet()
        val inventory = FakeInventory()
        val service = service(repository, wallet)

        val result = service.purchase(playerId, product.id, 3, inventory).join()

        assertTrue(result.successful)
        assertEquals(ShopCheckoutStatus.SUCCESS, result.status)
        assertEquals(BigDecimal("30"), result.totalPrice)
        assertEquals(7, result.remainingDailyLimit)
        assertEquals(1, wallet.withdrawCalls)
        assertEquals(1, inventory.deliverCalls)
        assertEquals(3, repository.quantity(playerId, product.id, date.plusDays(1)))
    }

    @Test
    fun `insufficient funds release the reserved daily limit`() {
        val repository = FakeRepository()
        val wallet = FakeWallet(withdrawal = ShopWalletOperation(ShopWalletStatus.INSUFFICIENT_FUNDS))
        val inventory = FakeInventory()
        val service = service(repository, wallet)

        val result = service.purchase(playerId, product.id, 2, inventory).join()

        assertEquals(ShopCheckoutStatus.INSUFFICIENT_FUNDS, result.status)
        assertFalse(result.successful)
        assertEquals(0, repository.quantity(playerId, product.id, date.plusDays(1)))
        assertEquals(0, inventory.deliverCalls)
    }

    @Test
    fun `delivery failure refunds DDB and releases the reservation`() {
        val repository = FakeRepository()
        val wallet = FakeWallet()
        val inventory = FakeInventory(delivery = ShopInventoryOperation(ShopInventoryStatus.INVENTORY_FULL))
        val service = service(repository, wallet)

        val result = service.purchase(playerId, product.id, 2, inventory).join()

        assertEquals(ShopCheckoutStatus.DELIVERY_REFUNDED, result.status)
        assertEquals(1, wallet.refundCalls)
        assertEquals(0, repository.quantity(playerId, product.id, date.plusDays(1)))
    }

    @Test
    fun `second checkout for one player is rejected while first is in progress`() {
        val repository = FakeRepository()
        val wallet = FakeWallet()
        val capacity = CompletableFuture<ShopInventoryOperation>()
        val inventory = FakeInventory(capacityFuture = capacity)
        val service = service(repository, wallet)

        val first = service.purchase(playerId, product.id, 1, inventory)
        val second = service.purchase(playerId, product.id, 1, inventory).join()

        assertEquals(ShopCheckoutStatus.IN_PROGRESS, second.status)
        capacity.complete(ShopInventoryOperation(ShopInventoryStatus.SUCCESS))
        assertEquals(ShopCheckoutStatus.SUCCESS, first.join().status)
    }

    private fun service(
        repository: FakeRepository = FakeRepository(),
        wallet: FakeWallet = FakeWallet(),
    ): ShopPurchaseService {
        return ShopPurchaseService(
            catalog = catalog,
            purchases = repository,
            wallet = wallet,
            executor = ImmediateExecutor,
            clock = clock,
        )
    }

    private data class Key(val playerId: UUID, val productId: String, val date: LocalDate)

    private class FakeRepository : ShopPurchaseRepository {
        val quantities = mutableMapOf<Key, Int>()

        override fun purchasedQuantity(playerId: UUID, productId: String, date: LocalDate): RepositoryResult<Int> {
            return RepositoryResult.Success(quantity(playerId, productId, date))
        }

        override fun reserve(
            playerId: UUID,
            productId: String,
            date: LocalDate,
            quantity: Int,
            dailyLimit: Int,
        ): ShopLimitReservationResult {
            val key = Key(playerId, productId, date)
            val current = quantities[key] ?: 0
            if (current > dailyLimit - quantity) {
                return ShopLimitReservationResult.LimitExceeded(current)
            }
            val next = current + quantity
            quantities[key] = next
            return ShopLimitReservationResult.Reserved(next)
        }

        override fun release(
            playerId: UUID,
            productId: String,
            date: LocalDate,
            quantity: Int,
        ): RepositoryResult<Unit> {
            val key = Key(playerId, productId, date)
            val remaining = (quantities[key] ?: 0) - quantity
            if (remaining <= 0) quantities.remove(key) else quantities[key] = remaining
            return RepositoryResult.Success(Unit)
        }

        fun quantity(playerId: UUID, productId: String, date: LocalDate): Int {
            return quantities[Key(playerId, productId, date)] ?: 0
        }
    }

    private class FakeWallet(
        private val withdrawal: ShopWalletOperation = ShopWalletOperation(ShopWalletStatus.SUCCESS),
        private val refund: ShopWalletOperation = ShopWalletOperation(ShopWalletStatus.SUCCESS),
    ) : ShopWalletPort {
        var withdrawCalls = 0
        var refundCalls = 0

        override fun withdraw(playerId: UUID, amount: BigDecimal): CompletableFuture<ShopWalletOperation> {
            withdrawCalls++
            return CompletableFuture.completedFuture(withdrawal)
        }

        override fun refund(playerId: UUID, amount: BigDecimal): CompletableFuture<ShopWalletOperation> {
            refundCalls++
            return CompletableFuture.completedFuture(refund)
        }
    }

    private class FakeInventory(
        private val capacity: ShopInventoryOperation = ShopInventoryOperation(ShopInventoryStatus.SUCCESS),
        private val delivery: ShopInventoryOperation = ShopInventoryOperation(ShopInventoryStatus.SUCCESS),
        private val capacityFuture: CompletableFuture<ShopInventoryOperation>? = null,
    ) : ShopInventoryPort {
        var deliverCalls = 0

        override fun checkCapacity(product: ShopProduct, quantity: Int): CompletableFuture<ShopInventoryOperation> {
            return capacityFuture ?: CompletableFuture.completedFuture(capacity)
        }

        override fun deliver(product: ShopProduct, quantity: Int): CompletableFuture<ShopInventoryOperation> {
            deliverCalls++
            return CompletableFuture.completedFuture(delivery)
        }
    }

    private object ImmediateExecutor : ShopAsyncExecutor {
        override fun <T> supply(block: () -> T): CompletableFuture<T> {
            return try {
                CompletableFuture.completedFuture(block())
            } catch (error: Throwable) {
                CompletableFuture.failedFuture(error)
            }
        }
    }
}
