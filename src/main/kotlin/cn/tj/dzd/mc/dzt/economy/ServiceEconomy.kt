package cn.tj.dzd.mc.dzt.economy

import net.thenextlvl.service.economy.Account
import net.thenextlvl.service.economy.EconomyController
import net.thenextlvl.service.economy.TransactionResult
import net.thenextlvl.service.economy.currency.Currency
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import taboolib.common.platform.function.severe
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * ServiceIO 经济接口适配层。
 *
 * 所有账户加载均使用 ServiceIO 的异步接口，余额读取和交易操作不会占用 Folia 实体线程。
 */
object ServiceEconomy {

    private const val MAX_AMOUNT_INPUT_LENGTH = 32
    private const val ACCOUNT_LOCK_STRIPES = 64

    private val accountLocks = Array(ACCOUNT_LOCK_STRIPES) { ReentrantLock() }

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
            controller.resolveAccount(uuid).thenApplyAsync { account ->
                account.toBalance(currency)
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
        val normalized = input.trim()
        if (normalized.isEmpty() || normalized.length > MAX_AMOUNT_INPUT_LENGTH) {
            return null
        }
        if (!normalized.matches(PLAIN_DECIMAL_PATTERN)) {
            return null
        }

        val amount = normalized.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
        if (amount <= BigDecimal.ZERO || !amount.toDouble().isFinite()) {
            return null
        }
        return amount
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
        if (from == to || amount <= BigDecimal.ZERO || !amount.toDouble().isFinite()) {
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
                .thenCombineAsync(controller.resolveAccount(to)) { sender, receiver ->
                    transferResolved(from, to, amount, currency, sender, receiver)
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
        return amount.stripTrailingZeros().toPlainString()
    }

    private fun transferResolved(
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        currency: Currency,
        sender: Optional<Account>,
        receiver: Optional<Account>,
    ): EconomyTransferResult {
        if (sender.isEmpty) {
            return EconomyTransferResult(EconomyTransferStatus.SENDER_ACCOUNT_UNAVAILABLE, amount)
        }
        if (receiver.isEmpty) {
            return EconomyTransferResult(EconomyTransferStatus.RECEIVER_ACCOUNT_UNAVAILABLE, amount)
        }

        return withAccountLocks(from, to) {
            val senderAccount = sender.get()
            val receiverAccount = receiver.get()
            if (!senderAccount.canHold(currency) || !receiverAccount.canHold(currency)) {
                return@withAccountLocks EconomyTransferResult(
                    EconomyTransferStatus.CURRENCY_NOT_SUPPORTED,
                    amount,
                )
            }

            val withdrawal = senderAccount.withdraw(amount, currency)
            if (!withdrawal.successful()) {
                val status = when (withdrawal.status()) {
                    TransactionResult.Status.INSUFFICIENT_FUNDS -> EconomyTransferStatus.INSUFFICIENT_FUNDS
                    TransactionResult.Status.CURRENCY_NOT_SUPPORTED -> EconomyTransferStatus.CURRENCY_NOT_SUPPORTED
                    else -> EconomyTransferStatus.WITHDRAWAL_FAILED
                }
                return@withAccountLocks EconomyTransferResult(status, amount, withdrawal.balance().toBigDecimalOrNull())
            }

            val deposit = receiverAccount.deposit(amount, currency)
            if (deposit.successful()) {
                return@withAccountLocks EconomyTransferResult(
                    EconomyTransferStatus.SUCCESS,
                    amount,
                    withdrawal.balance().toBigDecimalOrNull(),
                )
            }

            val refund = senderAccount.deposit(amount, currency)
            if (refund.successful()) {
                return@withAccountLocks EconomyTransferResult(
                    EconomyTransferStatus.DEPOSIT_FAILED_REFUNDED,
                    amount,
                    refund.balance().toBigDecimalOrNull(),
                )
            }

            severe(
                "ServiceIO 转账入账失败且退款失败，请人工核对账户。",
                "转出玩家 UUID: $from",
                "接收玩家 UUID: $to",
                "金额: ${formatAmount(amount)}",
                "入账状态: ${deposit.status()}",
                "退款状态: ${refund.status()}",
            )
            EconomyTransferResult(
                EconomyTransferStatus.DEPOSIT_FAILED_REFUND_FAILED,
                amount,
                refund.balance().toBigDecimalOrNull(),
            )
        }
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
        val fractionalDigits = currency.fractionalDigits
        if (fractionalDigits < 0) {
            return true
        }
        return amount.stripTrailingZeros().scale().coerceAtLeast(0) <= fractionalDigits
    }

    private fun controller(): EconomyController? {
        return Bukkit.getServicesManager().load(EconomyController::class.java)
    }

    private fun <T> withAccountLocks(first: UUID, second: UUID, block: () -> T): T {
        val lockIndexes = listOf(lockIndex(first), lockIndex(second))
            .distinct()
            .sorted()

        fun lock(index: Int): T {
            if (index >= lockIndexes.size) {
                return block()
            }
            return accountLocks[lockIndexes[index]].withLock {
                lock(index + 1)
            }
        }

        return lock(0)
    }

    private fun lockIndex(uuid: UUID): Int {
        return (uuid.hashCode() and Int.MAX_VALUE) % accountLocks.size
    }

    private fun Number.toBigDecimalOrNull(): BigDecimal? {
        return toString().toBigDecimalOrNull()
            ?: runCatching { BigDecimal.valueOf(toDouble()) }.getOrNull()
    }

    private val PLAIN_DECIMAL_PATTERN = Regex("[0-9]+(?:\\.[0-9]+)?")
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
