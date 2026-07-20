package cn.tj.dzd.mc.dzt.economy

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 转账引擎访问的账户端口。
 *
 * 基础设施适配器应将具体经济插件的账户和交易结果转换为此接口。
 */
interface EconomyAccountPort {

    /**
     * 检查账户是否支持当前货币。
     *
     * @return 支持时返回 true。
     */
    fun supportsCurrency(): Boolean

    /**
     * 从账户扣款。
     *
     * @param amount 正数金额。
     * @return 账户操作结果。
     */
    fun withdraw(amount: BigDecimal): EconomyAccountOperation

    /**
     * 向账户入账。
     *
     * @param amount 正数金额。
     * @return 账户操作结果。
     */
    fun deposit(amount: BigDecimal): EconomyAccountOperation
}

/** 单次账户操作状态。 */
enum class EconomyAccountOperationStatus {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    CURRENCY_NOT_SUPPORTED,
    FAILED,
}

/**
 * 单次账户操作结果。
 *
 * @property status 操作状态。
 * @property balance 操作后余额；基础设施未提供时为 null。
 */
data class EconomyAccountOperation(
    val status: EconomyAccountOperationStatus,
    val balance: BigDecimal? = null,
)

/**
 * 单账户经济变更的处理状态。
 *
 * 该状态不依赖 ServiceIO，可由购买、补偿等领域服务统一处理。
 */
enum class EconomyAccountMutationStatus {
    SUCCESS,
    INVALID_AMOUNT,
    ACCOUNT_UNAVAILABLE,
    CURRENCY_NOT_SUPPORTED,
    INSUFFICIENT_FUNDS,
    WITHDRAWAL_FAILED,
    DEPOSIT_FAILED,
}

/**
 * 单账户经济变更的纯领域结果。
 *
 * @property status 本次变更状态。
 * @property amount 本次请求金额。
 * @property balance 操作完成后的账户余额；基础设施未提供时为 null。
 */
data class EconomyAccountMutationResult(
    val status: EconomyAccountMutationStatus,
    val amount: BigDecimal,
    val balance: BigDecimal? = null,
) {
    /** 本次账户变更是否成功。 */
    val successful: Boolean
        get() = status == EconomyAccountMutationStatus.SUCCESS
}

/**
 * 不依赖 Bukkit 和 ServiceIO 的转账状态机。
 *
 * 涉及同一账户的 DZT 转账会在同一进程内串行化。该锁不能覆盖其他插件或其他服务器进程的账户修改。
 */
class EconomyTransferEngine {

    private val accountLocks = Array(ACCOUNT_LOCK_STRIPES) { ReentrantLock() }

    /**
     * 执行扣款、入账和失败退款。
     *
     * @param from 转出玩家 UUID。
     * @param to 接收玩家 UUID。
     * @param amount 转账金额。
     * @param sender 转出账户；无法解析时为 null。
     * @param receiver 接收账户；无法解析时为 null。
     * @return 完整转账结果。
     */
    fun transfer(
        from: UUID,
        to: UUID,
        amount: BigDecimal,
        sender: EconomyAccountPort?,
        receiver: EconomyAccountPort?,
    ): EconomyTransferResult {
        if (from == to || amount <= BigDecimal.ZERO || !amount.toDouble().isFinite()) {
            return EconomyTransferResult(EconomyTransferStatus.INVALID_AMOUNT, amount)
        }
        if (sender == null) {
            return EconomyTransferResult(EconomyTransferStatus.SENDER_ACCOUNT_UNAVAILABLE, amount)
        }
        if (receiver == null) {
            return EconomyTransferResult(EconomyTransferStatus.RECEIVER_ACCOUNT_UNAVAILABLE, amount)
        }

        return withAccountLocks(from, to) {
            if (!sender.supportsCurrency() || !receiver.supportsCurrency()) {
                return@withAccountLocks EconomyTransferResult(EconomyTransferStatus.CURRENCY_NOT_SUPPORTED, amount)
            }

            val withdrawal = sender.withdraw(amount)
            if (withdrawal.status != EconomyAccountOperationStatus.SUCCESS) {
                val status = when (withdrawal.status) {
                    EconomyAccountOperationStatus.INSUFFICIENT_FUNDS -> EconomyTransferStatus.INSUFFICIENT_FUNDS
                    EconomyAccountOperationStatus.CURRENCY_NOT_SUPPORTED -> EconomyTransferStatus.CURRENCY_NOT_SUPPORTED
                    else -> EconomyTransferStatus.WITHDRAWAL_FAILED
                }
                return@withAccountLocks EconomyTransferResult(status, amount, withdrawal.balance)
            }

            val deposit = receiver.deposit(amount)
            if (deposit.status == EconomyAccountOperationStatus.SUCCESS) {
                return@withAccountLocks EconomyTransferResult(
                    EconomyTransferStatus.SUCCESS,
                    amount,
                    withdrawal.balance,
                )
            }

            val refund = sender.deposit(amount)
            if (refund.status == EconomyAccountOperationStatus.SUCCESS) {
                return@withAccountLocks EconomyTransferResult(
                    EconomyTransferStatus.DEPOSIT_FAILED_REFUNDED,
                    amount,
                    refund.balance,
                )
            }
            EconomyTransferResult(
                EconomyTransferStatus.DEPOSIT_FAILED_REFUND_FAILED,
                amount,
                refund.balance,
            )
        }
    }

    /**
     * 从单个账户扣除金额。
     *
     * 与 [transfer] 使用同一组账户锁，因此同一玩家的商店扣款、退款和转账会在本进程内串行执行。
     *
     * @param player 要扣款的玩家 UUID。
     * @param amount 正数金额。
     * @param account 玩家账户；无法解析时为 null。
     * @return 扣款处理结果。
     */
    fun withdraw(
        player: UUID,
        amount: BigDecimal,
        account: EconomyAccountPort?,
    ): EconomyAccountMutationResult {
        return mutateAccount(
            player = player,
            amount = amount,
            account = account,
            failureStatus = EconomyAccountMutationStatus.WITHDRAWAL_FAILED,
            action = EconomyAccountPort::withdraw,
        )
    }

    /**
     * 向单个账户入账。
     *
     * 该方法供已经完成扣款后的补偿使用。调用方必须自行确保不会将其用于未经授权的发币操作。
     *
     * @param player 要入账的玩家 UUID。
     * @param amount 正数金额。
     * @param account 玩家账户；无法解析时为 null。
     * @return 入账处理结果。
     */
    fun deposit(
        player: UUID,
        amount: BigDecimal,
        account: EconomyAccountPort?,
    ): EconomyAccountMutationResult {
        return mutateAccount(
            player = player,
            amount = amount,
            account = account,
            failureStatus = EconomyAccountMutationStatus.DEPOSIT_FAILED,
            action = EconomyAccountPort::deposit,
        )
    }

    private fun mutateAccount(
        player: UUID,
        amount: BigDecimal,
        account: EconomyAccountPort?,
        failureStatus: EconomyAccountMutationStatus,
        action: (EconomyAccountPort, BigDecimal) -> EconomyAccountOperation,
    ): EconomyAccountMutationResult {
        if (amount <= BigDecimal.ZERO || !amount.toDouble().isFinite()) {
            return EconomyAccountMutationResult(EconomyAccountMutationStatus.INVALID_AMOUNT, amount)
        }
        if (account == null) {
            return EconomyAccountMutationResult(EconomyAccountMutationStatus.ACCOUNT_UNAVAILABLE, amount)
        }

        return withAccountLock(player) {
            if (!account.supportsCurrency()) {
                return@withAccountLock EconomyAccountMutationResult(
                    EconomyAccountMutationStatus.CURRENCY_NOT_SUPPORTED,
                    amount,
                )
            }

            val operation = action(account, amount)
            val status = when (operation.status) {
                EconomyAccountOperationStatus.SUCCESS -> EconomyAccountMutationStatus.SUCCESS
                EconomyAccountOperationStatus.INSUFFICIENT_FUNDS -> EconomyAccountMutationStatus.INSUFFICIENT_FUNDS
                EconomyAccountOperationStatus.CURRENCY_NOT_SUPPORTED -> EconomyAccountMutationStatus.CURRENCY_NOT_SUPPORTED
                EconomyAccountOperationStatus.FAILED -> failureStatus
            }
            EconomyAccountMutationResult(status, amount, operation.balance)
        }
    }

    private fun <T> withAccountLock(player: UUID, block: () -> T): T {
        return withAccountLocks(player, player, block)
    }

    private fun <T> withAccountLocks(first: UUID, second: UUID, block: () -> T): T {
        val indexes = listOf(lockIndex(first), lockIndex(second)).distinct().sorted()

        fun lock(index: Int): T {
            if (index >= indexes.size) return block()
            return accountLocks[indexes[index]].withLock { lock(index + 1) }
        }
        return lock(0)
    }

    private fun lockIndex(uuid: UUID): Int = (uuid.hashCode() and Int.MAX_VALUE) % accountLocks.size

    private companion object {
        const val ACCOUNT_LOCK_STRIPES = 64
    }
}
