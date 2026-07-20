package cn.tj.dzd.mc.dzt.shop

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** 北京时间是商店每日限购的唯一边界。 */
val SHOP_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/**
 * 商店每日限购的持久化端口。
 *
 * 实现必须让 [reserve] 在单个数据库事务内完成读取、限额判断和累计写入。
 */
interface ShopPurchaseRepository {

    /**
     * 读取某玩家某商品在指定自然日已购买的物品数量。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param date 北京时间日期，格式为 `yyyy-MM-dd`。
     * @return 存储操作结果；没有记录时成功值为 0。
     */
    fun purchasedQuantity(playerId: UUID, productId: String, date: LocalDate): RepositoryResult<Int>

    /**
     * 原子预占商品每日限购额度。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param date 北京时间日期。
     * @param quantity 需要预占的正数物品数量。
     * @param dailyLimit 当前商品的每日上限。
     * @return 预占后的累计数量、超限时的已购数量，或基础设施失败状态。
     */
    fun reserve(
        playerId: UUID,
        productId: String,
        date: LocalDate,
        quantity: Int,
        dailyLimit: Int,
    ): ShopLimitReservationResult

    /**
     * 释放一次尚未完成发货的预占额度。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param date 北京时间日期。
     * @param quantity 要释放的正数物品数量。
     * @return 存储操作结果。
     */
    fun release(playerId: UUID, productId: String, date: LocalDate, quantity: Int): RepositoryResult<Unit>
}

/** 商店限额预占结果。 */
sealed interface ShopLimitReservationResult {

    /**
     * 限额预占成功。
     *
     * @property purchasedQuantity 预占后当日累计数量。
     */
    data class Reserved(val purchasedQuantity: Int) : ShopLimitReservationResult

    /**
     * 请求数量会超过商品每日上限。
     *
     * @property purchasedQuantity 当前已购数量。
     */
    data class LimitExceeded(val purchasedQuantity: Int) : ShopLimitReservationResult

    /** 数据库不可用或事务无法完成。 */
    data object InfrastructureFailure : ShopLimitReservationResult
}

/**
 * 执行商店数据库任务的异步端口。
 *
 * 生产实现使用 DZT 统一异步执行器，测试可注入即时实现。
 */
interface ShopAsyncExecutor {

    /**
     * 异步执行不访问 Bukkit 实体或世界状态的任务。
     *
     * @param block 后台任务。
     * @return 承载任务结果的 Future。
     */
    fun <T> supply(block: () -> T): CompletableFuture<T>
}

/** DZT 生产环境的商店异步执行器。 */
object DztShopAsyncExecutor : ShopAsyncExecutor {
    override fun <T> supply(block: () -> T): CompletableFuture<T> = DztAsyncExecutor.supply(block)
}

/** 商店调用货币系统后的标准化状态。 */
enum class ShopWalletStatus {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    SERVICE_UNAVAILABLE,
    ACCOUNT_UNAVAILABLE,
    CURRENCY_NOT_SUPPORTED,
    INVALID_AMOUNT,
    FAILED,
}

/**
 * 商店钱包操作结果。
 *
 * @property status 操作结果状态。
 * @property balance 操作后的余额；底层服务不可提供时为 null。
 */
data class ShopWalletOperation(
    val status: ShopWalletStatus,
    val balance: BigDecimal? = null,
) {
    /** 此次货币操作是否完成。 */
    val successful: Boolean
        get() = status == ShopWalletStatus.SUCCESS
}

/**
 * 商店使用的货币端口。
 *
 * `refund` 仅能补偿本次已成功扣款但未完成发货的购买，不能作为通用发币接口。
 */
interface ShopWalletPort {

    /**
     * 扣除玩家的 DDB。
     *
     * @param playerId 玩家 UUID。
     * @param amount 正数金额。
     * @return 异步扣款结果。
     */
    fun withdraw(playerId: UUID, amount: BigDecimal): CompletableFuture<ShopWalletOperation>

    /**
     * 退还已经扣除的 DDB。
     *
     * @param playerId 玩家 UUID。
     * @param amount 正数金额。
     * @return 异步退款结果。
     */
    fun refund(playerId: UUID, amount: BigDecimal): CompletableFuture<ShopWalletOperation>
}

/** 商店在玩家实体线程检查或发放物品的结果。 */
enum class ShopInventoryStatus {
    SUCCESS,
    INVENTORY_FULL,
    PLAYER_UNAVAILABLE,
    FAILED,
}

/**
 * 商店背包操作结果。
 *
 * @property status 操作结果状态。
 */
data class ShopInventoryOperation(val status: ShopInventoryStatus) {
    /** 操作是否成功。 */
    val successful: Boolean
        get() = status == ShopInventoryStatus.SUCCESS
}

/**
 * 商店与玩家背包交互的端口。
 *
 * 实现必须在目标玩家的 Folia 实体线程检查容量和发放物品。
 */
interface ShopInventoryPort {

    /**
     * 检查玩家背包能否完整装下本次商品。
     *
     * @param product 要购买的商品。
     * @param quantity 物品数量。
     * @return 异步容量检查结果。
     */
    fun checkCapacity(product: ShopProduct, quantity: Int): CompletableFuture<ShopInventoryOperation>

    /**
     * 发放本次商品。
     *
     * 实现必须在发放前再次检查容量，避免异步扣款期间背包变化导致部分发货。
     *
     * @param product 要发放的商品。
     * @param quantity 物品数量。
     * @return 异步发放结果。
     */
    fun deliver(product: ShopProduct, quantity: Int): CompletableFuture<ShopInventoryOperation>
}

/** 商店限额查询状态。 */
enum class ShopAvailabilityStatus {
    AVAILABLE,
    PRODUCT_NOT_FOUND,
    INFRASTRUCTURE_FAILURE,
}

/**
 * 某商品当前可购买数量的查询结果。
 *
 * @property remaining 当前北京时间自然日剩余可购买物品数量。
 */
data class ShopAvailabilityResult(
    val status: ShopAvailabilityStatus,
    val product: ShopProduct? = null,
    val remaining: Int = 0,
    val message: String,
) {
    /** 商品是否能继续购买。 */
    val available: Boolean
        get() = status == ShopAvailabilityStatus.AVAILABLE && remaining > 0
}

/** 商店报价状态。 */
enum class ShopQuoteStatus {
    AVAILABLE,
    PRODUCT_NOT_FOUND,
    INVALID_QUANTITY,
    DAILY_LIMIT_EXCEEDED,
    INFRASTRUCTURE_FAILURE,
}

/**
 * 商店购买报价结果。
 *
 * @property totalPrice 本次数量的 DDB 总价；无法报价时为 null。
 */
data class ShopQuoteResult(
    val status: ShopQuoteStatus,
    val product: ShopProduct? = null,
    val quantity: Int = 0,
    val totalPrice: BigDecimal? = null,
    val remainingDailyLimit: Int? = null,
    val message: String,
) {
    /** 报价是否可用于发起购买。 */
    val available: Boolean
        get() = status == ShopQuoteStatus.AVAILABLE
}

/** 商店结算状态。 */
enum class ShopCheckoutStatus {
    SUCCESS,
    PRODUCT_NOT_FOUND,
    INVALID_QUANTITY,
    DAILY_LIMIT_EXCEEDED,
    INVENTORY_FULL,
    PLAYER_UNAVAILABLE,
    IN_PROGRESS,
    INSUFFICIENT_FUNDS,
    PAYMENT_FAILED,
    DELIVERY_REFUNDED,
    REFUND_FAILED,
    COMPENSATION_FAILED,
    INFRASTRUCTURE_FAILURE,
}

/**
 * 一次购买结算的最终结果。
 *
 * @property message 可直接向玩家展示的中文结果。
 */
data class ShopCheckoutResult(
    val status: ShopCheckoutStatus,
    val product: ShopProduct? = null,
    val quantity: Int = 0,
    val totalPrice: BigDecimal? = null,
    val remainingDailyLimit: Int? = null,
    val balance: BigDecimal? = null,
    val message: String,
) {
    /** 本次扣款和发货是否均已完成。 */
    val successful: Boolean
        get() = status == ShopCheckoutStatus.SUCCESS
}

/**
 * 不依赖 Bukkit 的商店结算服务。
 *
 * 一个玩家同一时刻只允许进行一笔商店购买。数据库预占、DDB 扣款和背包发货跨越两个独立系统，
 * 因此在发货失败时采用退款和释放额度的补偿策略，无法提供跨系统分布式事务保证。
 */
class ShopPurchaseService(
    private val catalog: ShopCatalog,
    private val purchases: ShopPurchaseRepository,
    private val wallet: ShopWalletPort,
    private val executor: ShopAsyncExecutor,
    private val clock: Clock = Clock.system(SHOP_TIME_ZONE),
) {

    private val purchasesInProgress = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * 查询玩家对某个商品的当前每日可购买数量。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @return 异步限额查询结果。
     */
    fun availability(playerId: UUID, productId: String): CompletableFuture<ShopAvailabilityResult> {
        val product = catalog.findProduct(productId)
            ?: return completed(
                ShopAvailabilityResult(
                    ShopAvailabilityStatus.PRODUCT_NOT_FOUND,
                    message = "商品不存在或已下架。",
                )
            )
        val date = today()
        return executor.supply {
            when (val result = purchases.purchasedQuantity(playerId, product.id, date)) {
                is RepositoryResult.Success -> ShopAvailabilityResult(
                    status = ShopAvailabilityStatus.AVAILABLE,
                    product = product,
                    remaining = (product.dailyLimit - result.value).coerceAtLeast(0),
                    message = "查询成功。",
                )

                RepositoryResult.Failure -> ShopAvailabilityResult(
                    ShopAvailabilityStatus.INFRASTRUCTURE_FAILURE,
                    product = product,
                    message = "读取每日限购失败。",
                )
            }
        }.exceptionally {
            ShopAvailabilityResult(
                ShopAvailabilityStatus.INFRASTRUCTURE_FAILURE,
                product = product,
                message = "读取每日限购失败。",
            )
        }
    }

    /**
     * 验证数量并计算本次购买报价。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param quantity 要购买的物品数量。
     * @return 异步报价结果。
     */
    fun quote(playerId: UUID, productId: String, quantity: Int): CompletableFuture<ShopQuoteResult> {
        val product = catalog.findProduct(productId)
            ?: return completed(
                ShopQuoteResult(
                    ShopQuoteStatus.PRODUCT_NOT_FOUND,
                    message = "商品不存在或已下架。",
                )
            )
        if (quantity <= 0) {
            return completed(
                ShopQuoteResult(
                    ShopQuoteStatus.INVALID_QUANTITY,
                    product = product,
                    quantity = quantity,
                    message = "购买数量必须大于 0。",
                )
            )
        }
        return availability(playerId, productId).thenApply { availability ->
            when (availability.status) {
                ShopAvailabilityStatus.AVAILABLE -> {
                    if (quantity > availability.remaining) {
                        ShopQuoteResult(
                            ShopQuoteStatus.DAILY_LIMIT_EXCEEDED,
                            product = product,
                            quantity = quantity,
                            remainingDailyLimit = availability.remaining,
                            message = "该商品今日仅剩 ${availability.remaining} 个可购买。",
                        )
                    } else {
                        ShopQuoteResult(
                            ShopQuoteStatus.AVAILABLE,
                            product = product,
                            quantity = quantity,
                            totalPrice = totalPrice(product, quantity),
                            remainingDailyLimit = availability.remaining,
                            message = "报价成功。",
                        )
                    }
                }

                ShopAvailabilityStatus.PRODUCT_NOT_FOUND -> ShopQuoteResult(
                    ShopQuoteStatus.PRODUCT_NOT_FOUND,
                    message = availability.message,
                )

                ShopAvailabilityStatus.INFRASTRUCTURE_FAILURE -> ShopQuoteResult(
                    ShopQuoteStatus.INFRASTRUCTURE_FAILURE,
                    product = product,
                    message = availability.message,
                )
            }
        }
    }

    /**
     * 执行一次完整商店购买。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param quantity 要购买的物品数量。
     * @param inventory 玩家背包端口，必须保证 Folia 兼容。
     * @return 异步最终结算结果。
     */
    fun purchase(
        playerId: UUID,
        productId: String,
        quantity: Int,
        inventory: ShopInventoryPort,
    ): CompletableFuture<ShopCheckoutResult> {
        val product = catalog.findProduct(productId)
            ?: return completed(checkout(ShopCheckoutStatus.PRODUCT_NOT_FOUND, message = "商品不存在或已下架。"))
        if (quantity <= 0) {
            return completed(
                checkout(
                    ShopCheckoutStatus.INVALID_QUANTITY,
                    product,
                    quantity,
                    message = "购买数量必须大于 0。",
                )
            )
        }
        if (!purchasesInProgress.add(playerId)) {
            return completed(
                checkout(
                    ShopCheckoutStatus.IN_PROGRESS,
                    product,
                    quantity,
                    message = "已有一笔商店购买正在处理中，请稍后再试。",
                )
            )
        }

        val price = totalPrice(product, quantity)
        val date = today()
        return inventory.checkCapacity(product, quantity)
            .thenCompose { capacity ->
                if (!capacity.successful) {
                    completed(inventoryFailure(product, quantity, price, capacity.status))
                } else {
                    reserveAndPurchase(playerId, product, quantity, price, date, inventory)
                }
            }
            .handle { result, error ->
                result ?: checkout(
                    ShopCheckoutStatus.INFRASTRUCTURE_FAILURE,
                    product,
                    quantity,
                    price,
                    message = "商店服务发生异常，请稍后再试。",
                )
            }
            .whenComplete { _, _ -> purchasesInProgress.remove(playerId) }
    }

    private fun reserveAndPurchase(
        playerId: UUID,
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        date: LocalDate,
        inventory: ShopInventoryPort,
    ): CompletableFuture<ShopCheckoutResult> {
        return executor.supply {
            purchases.reserve(playerId, product.id, date, quantity, product.dailyLimit)
        }.thenCompose { reservation ->
            when (reservation) {
                is ShopLimitReservationResult.Reserved -> withdrawAndDeliver(
                    playerId,
                    product,
                    quantity,
                    price,
                    date,
                    reservation.purchasedQuantity,
                    inventory,
                )

                is ShopLimitReservationResult.LimitExceeded -> completed(
                    checkout(
                        ShopCheckoutStatus.DAILY_LIMIT_EXCEEDED,
                        product,
                        quantity,
                        price,
                        remaining = (product.dailyLimit - reservation.purchasedQuantity).coerceAtLeast(0),
                        message = "该商品今日购买额度不足。",
                    )
                )

                ShopLimitReservationResult.InfrastructureFailure -> completed(
                    checkout(
                        ShopCheckoutStatus.INFRASTRUCTURE_FAILURE,
                        product,
                        quantity,
                        price,
                        message = "更新每日限购失败。",
                    )
                )
            }
        }
    }

    private fun withdrawAndDeliver(
        playerId: UUID,
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        date: LocalDate,
        purchasedQuantity: Int,
        inventory: ShopInventoryPort,
    ): CompletableFuture<ShopCheckoutResult> {
        return wallet.withdraw(playerId, price)
            .handle { result, _ -> result ?: ShopWalletOperation(ShopWalletStatus.FAILED) }
            .thenCompose { withdrawal ->
                if (!withdrawal.successful) {
                    releaseAfterPaymentFailure(
                        playerId,
                        product,
                        quantity,
                        price,
                        date,
                        withdrawal,
                    )
                } else {
                    deliverAfterPayment(
                        playerId,
                        product,
                        quantity,
                        price,
                        date,
                        purchasedQuantity,
                        withdrawal,
                        inventory,
                    )
                }
            }
    }

    private fun releaseAfterPaymentFailure(
        playerId: UUID,
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        date: LocalDate,
        withdrawal: ShopWalletOperation,
    ): CompletableFuture<ShopCheckoutResult> {
        return release(playerId, product, quantity, date).thenApply { released ->
            if (!released) {
                checkout(
                    ShopCheckoutStatus.COMPENSATION_FAILED,
                    product,
                    quantity,
                    price,
                    balance = withdrawal.balance,
                    message = "扣款失败且无法释放每日限购，请联系管理员。",
                )
            } else {
                paymentFailure(product, quantity, price, withdrawal)
            }
        }
    }

    private fun deliverAfterPayment(
        playerId: UUID,
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        date: LocalDate,
        purchasedQuantity: Int,
        withdrawal: ShopWalletOperation,
        inventory: ShopInventoryPort,
    ): CompletableFuture<ShopCheckoutResult> {
        return inventory.deliver(product, quantity)
            .handle { result, _ -> result ?: ShopInventoryOperation(ShopInventoryStatus.FAILED) }
            .thenCompose { delivery ->
                if (delivery.successful) {
                    completed(
                        checkout(
                            ShopCheckoutStatus.SUCCESS,
                            product,
                            quantity,
                            price,
                            remaining = (product.dailyLimit - purchasedQuantity).coerceAtLeast(0),
                            balance = withdrawal.balance,
                            message = "已购买 ${product.displayName} x$quantity，花费 $price DDB。",
                        )
                    )
                } else {
                    refundAndRelease(playerId, product, quantity, price, date, delivery.status)
                }
            }
    }

    private fun refundAndRelease(
        playerId: UUID,
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        date: LocalDate,
        deliveryStatus: ShopInventoryStatus,
    ): CompletableFuture<ShopCheckoutResult> {
        return wallet.refund(playerId, price)
            .handle { result, _ -> result?.successful == true }
            .thenCompose { refunded ->
                release(playerId, product, quantity, date).thenApply { released ->
                    when {
                        refunded && released -> checkout(
                            ShopCheckoutStatus.DELIVERY_REFUNDED,
                            product,
                            quantity,
                            price,
                            message = deliveryFailureMessage(deliveryStatus) + "，扣款已退回。",
                        )

                        !refunded && released -> checkout(
                            ShopCheckoutStatus.REFUND_FAILED,
                            product,
                            quantity,
                            price,
                            message = deliveryFailureMessage(deliveryStatus) + "，退款失败，请立即联系管理员。",
                        )

                        else -> checkout(
                            ShopCheckoutStatus.COMPENSATION_FAILED,
                            product,
                            quantity,
                            price,
                            message = deliveryFailureMessage(deliveryStatus) + "，退款或限额补偿失败，请立即联系管理员。",
                        )
                    }
                }
            }
    }

    private fun release(
        playerId: UUID,
        product: ShopProduct,
        quantity: Int,
        date: LocalDate,
    ): CompletableFuture<Boolean> {
        return executor.supply {
            purchases.release(playerId, product.id, date, quantity)
        }.handle { result, error -> error == null && result is RepositoryResult.Success }
    }

    private fun inventoryFailure(
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        status: ShopInventoryStatus,
    ): ShopCheckoutResult {
        val checkoutStatus = when (status) {
            ShopInventoryStatus.INVENTORY_FULL -> ShopCheckoutStatus.INVENTORY_FULL
            ShopInventoryStatus.PLAYER_UNAVAILABLE -> ShopCheckoutStatus.PLAYER_UNAVAILABLE
            ShopInventoryStatus.SUCCESS,
            ShopInventoryStatus.FAILED,
            -> ShopCheckoutStatus.INFRASTRUCTURE_FAILURE
        }
        return checkout(checkoutStatus, product, quantity, price, message = deliveryFailureMessage(status))
    }

    private fun paymentFailure(
        product: ShopProduct,
        quantity: Int,
        price: BigDecimal,
        withdrawal: ShopWalletOperation,
    ): ShopCheckoutResult {
        val status = when (withdrawal.status) {
            ShopWalletStatus.INSUFFICIENT_FUNDS -> ShopCheckoutStatus.INSUFFICIENT_FUNDS
            else -> ShopCheckoutStatus.PAYMENT_FAILED
        }
        val message = when (withdrawal.status) {
            ShopWalletStatus.INSUFFICIENT_FUNDS -> "DDB 余额不足。"
            ShopWalletStatus.SERVICE_UNAVAILABLE -> "DDB 经济服务当前不可用。"
            ShopWalletStatus.ACCOUNT_UNAVAILABLE -> "无法读取您的 DDB 账户。"
            ShopWalletStatus.CURRENCY_NOT_SUPPORTED -> "您的账户不支持 DDB。"
            ShopWalletStatus.INVALID_AMOUNT -> "商品价格无效。"
            ShopWalletStatus.SUCCESS,
            ShopWalletStatus.FAILED,
            -> "DDB 扣款失败，请稍后再试。"
        }
        return checkout(status, product, quantity, price, balance = withdrawal.balance, message = message)
    }

    private fun deliveryFailureMessage(status: ShopInventoryStatus): String {
        return when (status) {
            ShopInventoryStatus.INVENTORY_FULL -> "背包空间不足"
            ShopInventoryStatus.PLAYER_UNAVAILABLE -> "玩家已离线"
            ShopInventoryStatus.SUCCESS,
            ShopInventoryStatus.FAILED,
            -> "物品发放失败"
        }
    }

    private fun checkout(
        status: ShopCheckoutStatus,
        product: ShopProduct? = null,
        quantity: Int = 0,
        price: BigDecimal? = null,
        remaining: Int? = null,
        balance: BigDecimal? = null,
        message: String,
    ): ShopCheckoutResult {
        return ShopCheckoutResult(status, product, quantity, price, remaining, balance, message)
    }

    private fun totalPrice(product: ShopProduct, quantity: Int): BigDecimal {
        return product.price.multiply(BigDecimal.valueOf(quantity.toLong()))
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(SHOP_TIME_ZONE))

    private fun <T> completed(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)
}
