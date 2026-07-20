package cn.tj.dzd.mc.dzt.economy

import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import net.thenextlvl.service.economy.Account
import net.thenextlvl.service.economy.EconomyController
import net.thenextlvl.service.economy.currency.Currency
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.common.platform.function.severe
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * ServiceIO 经济接口适配层。
 *
 * 所有账户加载均使用 ServiceIO 的异步接口，余额读取和交易操作不会占用 Folia 实体线程。
 */
object ServiceEconomy {

    private val transferEngine = EconomyTransferEngine()

    /**
     * 获取玩家默认货币余额。
     *
     * @param player 要查询的玩家。
     * @return 异步余额结果；经济服务或账户不可用时完成为 null。
     */
    fun getBalance(player: OfflinePlayer): CompletableFuture<EconomyBalance?> {
        return getBalance(player.uniqueId)
    }

    /**
     * 获取玩家默认货币余额。
     *
     * @param uuid 要查询的玩家 UUID。
     * @return 异步余额结果；经济服务或账户不可用时完成为 null。
     */
    fun getBalance(uuid: UUID): CompletableFuture<EconomyBalance?> {
        return try {
            val controller = controller() ?: return CompletableFuture.completedFuture(null)
            val currency = controller.currencyController.defaultCurrency
            controller.resolveAccount(uuid).thenCompose { account ->
                DztAsyncExecutor.supply {
                    account.toBalance(currency)
                }
            }
        } catch (error: Throwable) {
            CompletableFuture.failedFuture(error)
        }
    }

    /**
     * 解析玩家输入的转账金额。
     *
     * @param input 玩家输入的金额文本。
     * @return 规范化后的正数金额；格式非法、数值非有限或文本过长时返回 null。
     */
    fun parseAmount(input: String): BigDecimal? {
        return EconomyAmounts.parse(input)
    }

    /**
     * 使用 ServiceIO 默认货币在两个玩家账户之间转账。
     *
     * 转出成功而转入失败时会立即向转出账户退款。该方法会串行化涉及同一账户的 DZT 转账，
     * 但无法为第三方插件发起的独立经济操作提供跨插件事务保证。
     *
     * @param from 转出玩家 UUID。
     * @param to 接收玩家 UUID。
     * @param amount 转账金额，必须大于 0。
     * @return 异步转账结果。
     */
    fun transfer(from: UUID, to: UUID, amount: BigDecimal): CompletableFuture<EconomyTransferResult> {
        if (from == to || !isValidPositiveAmount(amount)) {
            return CompletableFuture.completedFuture(
                EconomyTransferResult(EconomyTransferStatus.INVALID_AMOUNT, amount)
            )
        }

        return try {
            val controller = controller() ?: return CompletableFuture.completedFuture(
                EconomyTransferResult(EconomyTransferStatus.SERVICE_UNAVAILABLE, amount)
            )
            val currency = controller.currencyController.defaultCurrency
            if (!supportsFractionalDigits(amount, currency)) {
                return CompletableFuture.completedFuture(
                    EconomyTransferResult(EconomyTransferStatus.INVALID_PRECISION, amount)
                )
            }
            controller.resolveAccount(from)
                .thenCombine(controller.resolveAccount(to)) { sender, receiver -> sender to receiver }
                .thenCompose { (sender, receiver) ->
                    DztAsyncExecutor.supply {
                        val result = transferEngine.transfer(
                            from = from,
                            to = to,
                            amount = amount,
                            sender = sender.orElse(null)?.let { ServiceIoEconomyAccount(it, currency) },
                            receiver = receiver.orElse(null)?.let { ServiceIoEconomyAccount(it, currency) },
                        )
                        if (result.status == EconomyTransferStatus.DEPOSIT_FAILED_REFUND_FAILED) {
                            severe(
                                "ServiceIO 转账入账失败且退款失败，请人工核对账户。",
                                "转出玩家 UUID: $from",
                                "接收玩家 UUID: $to",
                                "金额: ${formatAmount(amount)}",
                            )
                        }
                        result
                    }
                }
        } catch (error: Throwable) {
            CompletableFuture.failedFuture(error)
        }
    }

    /**
     * 从玩家的 ServiceIO 默认货币账户异步扣款。
     *
     * 此操作与 [transfer]、[refund] 共用同一个账户锁。相同玩家的商店结算、退款和转账
     * 会在本 DZT 进程内按实际进入账户操作的顺序串行执行。
     *
     * @param player 要扣款的玩家 UUID。
     * @param amount 扣款金额，必须大于 0 且符合默认货币精度。
     * @return 异步扣款结果；底层 ServiceIO Future 异常时该 Future 异常完成。
     */
    fun withdraw(player: UUID, amount: BigDecimal): CompletableFuture<EconomyWithdrawalResult> {
        if (!isValidPositiveAmount(amount)) {
            return CompletableFuture.completedFuture(
                EconomyWithdrawalResult(EconomyWithdrawalStatus.INVALID_AMOUNT, amount)
            )
        }

        return try {
            val controller = controller() ?: return CompletableFuture.completedFuture(
                EconomyWithdrawalResult(EconomyWithdrawalStatus.SERVICE_UNAVAILABLE, amount)
            )
            val currency = controller.currencyController.defaultCurrency
            if (!supportsFractionalDigits(amount, currency)) {
                return CompletableFuture.completedFuture(
                    EconomyWithdrawalResult(EconomyWithdrawalStatus.INVALID_PRECISION, amount)
                )
            }
            controller.resolveAccount(player).thenCompose { account ->
                DztAsyncExecutor.supply {
                    transferEngine.withdraw(
                        player = player,
                        amount = amount,
                        account = account.orElse(null)?.let { ServiceIoEconomyAccount(it, currency) },
                    ).toWithdrawalResult()
                }
            }
        } catch (error: Throwable) {
            CompletableFuture.failedFuture(error)
        }
    }

    /**
     * 向玩家的 ServiceIO 默认货币账户退还已扣除的金额。
     *
     * 该接口仅用于无法完成后续业务操作时的补偿。例如商店已经扣款但物品发放失败时，
     * 应以与原扣款相同的金额调用本方法。禁止将其作为通用发币接口使用。
     *
     * @param player 要退款的玩家 UUID。
     * @param amount 退款金额，必须大于 0 且符合默认货币精度。
     * @return 异步退款结果；底层 ServiceIO Future 异常时该 Future 异常完成。
     */
    fun refund(player: UUID, amount: BigDecimal): CompletableFuture<EconomyRefundResult> {
        if (!isValidPositiveAmount(amount)) {
            return CompletableFuture.completedFuture(
                EconomyRefundResult(EconomyRefundStatus.INVALID_AMOUNT, amount)
            )
        }

        return try {
            val controller = controller() ?: return CompletableFuture.completedFuture(
                EconomyRefundResult(EconomyRefundStatus.SERVICE_UNAVAILABLE, amount)
            )
            val currency = controller.currencyController.defaultCurrency
            if (!supportsFractionalDigits(amount, currency)) {
                return CompletableFuture.completedFuture(
                    EconomyRefundResult(EconomyRefundStatus.INVALID_PRECISION, amount)
                )
            }
            controller.resolveAccount(player).thenCompose { account ->
                DztAsyncExecutor.supply {
                    transferEngine.deposit(
                        player = player,
                        amount = amount,
                        account = account.orElse(null)?.let { ServiceIoEconomyAccount(it, currency) },
                    ).toRefundResult()
                }
            }
        } catch (error: Throwable) {
            CompletableFuture.failedFuture(error)
        }
    }

    /**
     * 将经济金额格式化为不带无意义尾随零的普通十进制文本。
     *
     * @param amount 要格式化的金额。
     * @return 不使用科学计数法的金额文本。
     */
    fun formatAmount(amount: BigDecimal): String {
        return EconomyAmounts.format(amount)
    }

    private fun Optional<Account>.toBalance(currency: Currency): EconomyBalance? {
        val account = orElse(null) ?: return null
        if (!account.canHold(currency)) {
            return null
        }
        return EconomyBalance(
            amount = account.getBalance(currency),
            currencyName = currency.name,
            fractionalDigits = currency.fractionalDigits,
        )
    }

    private fun supportsFractionalDigits(amount: BigDecimal, currency: Currency): Boolean {
        return EconomyAmounts.supportsFractionalDigits(amount, currency.fractionalDigits)
    }

    private fun isValidPositiveAmount(amount: BigDecimal): Boolean {
        return amount > BigDecimal.ZERO && amount.toDouble().isFinite()
    }

    /**
     * ServiceIO registers its controller through Bukkit's service registry; TabooLib has no typed equivalent.
     */
    private fun controller(): EconomyController? {
        return Bukkit.getServicesManager().load(EconomyController::class.java)
    }

}

/**
 * ServiceIO 默认货币余额快照。
 *
 * @property amount 余额数值。
 * @property currencyName ServiceIO 货币名称。
 * @property fractionalDigits 货币支持的小数位数，负数表示不限制。
 */
data class EconomyBalance(
    val amount: BigDecimal,
    val currencyName: String,
    val fractionalDigits: Int,
)

/**
 * ServiceIO 转账处理结果。
 *
 * @property status 转账状态。
 * @property amount 本次请求金额。
 * @property balance 转出账户在操作完成后的余额；无法取得时为 null。
 */
data class EconomyTransferResult(
    val status: EconomyTransferStatus,
    val amount: BigDecimal,
    val balance: BigDecimal? = null,
) {
    /** 转账是否完整成功。 */
    val successful: Boolean
        get() = status == EconomyTransferStatus.SUCCESS
}

/**
 * ServiceIO 默认货币扣款处理结果。
 *
 * @property status 扣款状态。
 * @property amount 本次请求金额。
 * @property balance 扣款完成后的账户余额；无法取得时为 null。
 */
data class EconomyWithdrawalResult(
    val status: EconomyWithdrawalStatus,
    val amount: BigDecimal,
    val balance: BigDecimal? = null,
) {
    /** 扣款是否成功。 */
    val successful: Boolean
        get() = status == EconomyWithdrawalStatus.SUCCESS
}

/**
 * 调用 [ServiceEconomy.withdraw] 时可能产生的状态。
 */
enum class EconomyWithdrawalStatus {
    SUCCESS,
    SERVICE_UNAVAILABLE,
    INVALID_AMOUNT,
    INVALID_PRECISION,
    ACCOUNT_UNAVAILABLE,
    CURRENCY_NOT_SUPPORTED,
    INSUFFICIENT_FUNDS,
    WITHDRAWAL_FAILED,
}

/**
 * ServiceIO 默认货币退款处理结果。
 *
 * @property status 退款状态。
 * @property amount 本次请求金额。
 * @property balance 退款完成后的账户余额；无法取得时为 null。
 */
data class EconomyRefundResult(
    val status: EconomyRefundStatus,
    val amount: BigDecimal,
    val balance: BigDecimal? = null,
) {
    /** 退款是否成功。 */
    val successful: Boolean
        get() = status == EconomyRefundStatus.SUCCESS
}

/**
 * 调用 [ServiceEconomy.refund] 时可能产生的状态。
 */
enum class EconomyRefundStatus {
    SUCCESS,
    SERVICE_UNAVAILABLE,
    INVALID_AMOUNT,
    INVALID_PRECISION,
    ACCOUNT_UNAVAILABLE,
    CURRENCY_NOT_SUPPORTED,
    REFUND_FAILED,
}

/**
 * DZT 调用 ServiceIO 转账时可能产生的状态。
 */
enum class EconomyTransferStatus {
    SUCCESS,
    SERVICE_UNAVAILABLE,
    INVALID_AMOUNT,
    INVALID_PRECISION,
    SENDER_ACCOUNT_UNAVAILABLE,
    RECEIVER_ACCOUNT_UNAVAILABLE,
    CURRENCY_NOT_SUPPORTED,
    INSUFFICIENT_FUNDS,
    WITHDRAWAL_FAILED,
    DEPOSIT_FAILED_REFUNDED,
    DEPOSIT_FAILED_REFUND_FAILED,
}

private fun EconomyAccountMutationResult.toWithdrawalResult(): EconomyWithdrawalResult {
    val status = when (status) {
        EconomyAccountMutationStatus.SUCCESS -> EconomyWithdrawalStatus.SUCCESS
        EconomyAccountMutationStatus.INVALID_AMOUNT -> EconomyWithdrawalStatus.INVALID_AMOUNT
        EconomyAccountMutationStatus.ACCOUNT_UNAVAILABLE -> EconomyWithdrawalStatus.ACCOUNT_UNAVAILABLE
        EconomyAccountMutationStatus.CURRENCY_NOT_SUPPORTED -> EconomyWithdrawalStatus.CURRENCY_NOT_SUPPORTED
        EconomyAccountMutationStatus.INSUFFICIENT_FUNDS -> EconomyWithdrawalStatus.INSUFFICIENT_FUNDS
        EconomyAccountMutationStatus.WITHDRAWAL_FAILED,
        EconomyAccountMutationStatus.DEPOSIT_FAILED,
        -> EconomyWithdrawalStatus.WITHDRAWAL_FAILED
    }
    return EconomyWithdrawalResult(status, amount, balance)
}

private fun EconomyAccountMutationResult.toRefundResult(): EconomyRefundResult {
    val status = when (status) {
        EconomyAccountMutationStatus.SUCCESS -> EconomyRefundStatus.SUCCESS
        EconomyAccountMutationStatus.INVALID_AMOUNT -> EconomyRefundStatus.INVALID_AMOUNT
        EconomyAccountMutationStatus.ACCOUNT_UNAVAILABLE -> EconomyRefundStatus.ACCOUNT_UNAVAILABLE
        EconomyAccountMutationStatus.CURRENCY_NOT_SUPPORTED -> EconomyRefundStatus.CURRENCY_NOT_SUPPORTED
        EconomyAccountMutationStatus.INSUFFICIENT_FUNDS,
        EconomyAccountMutationStatus.WITHDRAWAL_FAILED,
        EconomyAccountMutationStatus.DEPOSIT_FAILED,
        -> EconomyRefundStatus.REFUND_FAILED
    }
    return EconomyRefundResult(status, amount, balance)
}
