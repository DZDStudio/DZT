package cn.tj.dzd.mc.dzt.shop

import cn.tj.dzd.mc.dzt.data.repository.PersistentShopPurchaseRepository
import cn.tj.dzd.mc.dzt.economy.EconomyRefundStatus
import cn.tj.dzd.mc.dzt.economy.EconomyWithdrawalStatus
import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.log.PlayerLogService
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * DZT 运行时商店服务入口。
 *
 * 该对象集中装配打包目录、Persistent Container 仓库、ServiceIO DDB 钱包和异步执行器；
 * UI 只能通过 [ShopCheckoutCoordinator] 传入 Folia 背包端口完成购买。
 */
object ShopService {

    private val application: ShopPurchaseService by lazy {
        ShopPurchaseService(
            catalog = ShopCatalogs.catalog,
            purchases = PersistentShopPurchaseRepository,
            wallet = ServiceEconomyShopWallet,
            executor = DztShopAsyncExecutor,
        )
    }

    /** 当前插件包内的只读商店目录。 */
    val catalog: ShopCatalog
        get() = ShopCatalogs.catalog

    /**
     * 查询玩家对指定商品的当前每日剩余额度。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @return 异步限额查询结果。
     */
    fun availability(playerId: UUID, productId: String): CompletableFuture<ShopAvailabilityResult> {
        return application.availability(playerId, productId)
    }

    /**
     * 计算指定数量的商品报价。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param quantity 要购买的物品数量。
     * @return 异步报价结果。
     */
    fun quote(playerId: UUID, productId: String, quantity: Int): CompletableFuture<ShopQuoteResult> {
        return application.quote(playerId, productId, quantity)
    }

    /**
     * 执行一次商店购买。
     *
     * 调用方必须提供 Folia 兼容的 [inventory] 实现；玩家背包的读取和发放不得在异步线程执行。
     *
     * @param playerId 玩家 UUID。
     * @param productId 商品稳定 ID。
     * @param quantity 要购买的物品数量。
     * @param inventory 玩家背包端口。
     * @return 异步结算结果。
     */
    fun purchase(
        playerId: UUID,
        productId: String,
        quantity: Int,
        inventory: ShopInventoryPort,
    ): CompletableFuture<ShopCheckoutResult> {
        return application.purchase(playerId, productId, quantity, inventory).thenApply { result ->
            if (result.successful) {
                PlayerLogService.recordPurchase(
                    playerId = playerId,
                    productId = requireNotNull(result.product).id,
                    quantity = result.quantity,
                    totalPrice = requireNotNull(result.totalPrice),
                )
            }
            result
        }
    }
}

/** 将 ServiceIO 默认 DDB 账户转换为商店钱包端口。 */
private object ServiceEconomyShopWallet : ShopWalletPort {

    override fun withdraw(playerId: UUID, amount: BigDecimal): CompletableFuture<ShopWalletOperation> {
        return ServiceEconomy.withdraw(playerId, amount).thenApply { result ->
            ShopWalletOperation(result.status.toShopWalletStatus(), result.balance)
        }
    }

    override fun refund(playerId: UUID, amount: BigDecimal): CompletableFuture<ShopWalletOperation> {
        return ServiceEconomy.refund(playerId, amount).thenApply { result ->
            ShopWalletOperation(result.status.toShopWalletStatus(), result.balance)
        }
    }
}

private fun EconomyWithdrawalStatus.toShopWalletStatus(): ShopWalletStatus {
    return when (this) {
        EconomyWithdrawalStatus.SUCCESS -> ShopWalletStatus.SUCCESS
        EconomyWithdrawalStatus.INSUFFICIENT_FUNDS -> ShopWalletStatus.INSUFFICIENT_FUNDS
        EconomyWithdrawalStatus.SERVICE_UNAVAILABLE -> ShopWalletStatus.SERVICE_UNAVAILABLE
        EconomyWithdrawalStatus.ACCOUNT_UNAVAILABLE -> ShopWalletStatus.ACCOUNT_UNAVAILABLE
        EconomyWithdrawalStatus.CURRENCY_NOT_SUPPORTED -> ShopWalletStatus.CURRENCY_NOT_SUPPORTED
        EconomyWithdrawalStatus.INVALID_AMOUNT,
        EconomyWithdrawalStatus.INVALID_PRECISION,
        -> ShopWalletStatus.INVALID_AMOUNT

        EconomyWithdrawalStatus.WITHDRAWAL_FAILED -> ShopWalletStatus.FAILED
    }
}

private fun EconomyRefundStatus.toShopWalletStatus(): ShopWalletStatus {
    return when (this) {
        EconomyRefundStatus.SUCCESS -> ShopWalletStatus.SUCCESS
        EconomyRefundStatus.SERVICE_UNAVAILABLE -> ShopWalletStatus.SERVICE_UNAVAILABLE
        EconomyRefundStatus.ACCOUNT_UNAVAILABLE -> ShopWalletStatus.ACCOUNT_UNAVAILABLE
        EconomyRefundStatus.CURRENCY_NOT_SUPPORTED -> ShopWalletStatus.CURRENCY_NOT_SUPPORTED
        EconomyRefundStatus.INVALID_AMOUNT,
        EconomyRefundStatus.INVALID_PRECISION,
        -> ShopWalletStatus.INVALID_AMOUNT

        EconomyRefundStatus.REFUND_FAILED -> ShopWalletStatus.FAILED
    }
}
