package cn.tj.dzd.mc.dzt.economy

import net.thenextlvl.service.economy.Account
import net.thenextlvl.service.economy.TransactionResult
import net.thenextlvl.service.economy.currency.Currency
import java.math.BigDecimal

/** ServiceIO 账户到 DZT 转账引擎的适配器。 */
internal class ServiceIoEconomyAccount(
    private val account: Account,
    private val currency: Currency,
) : EconomyAccountPort {

    override fun supportsCurrency(): Boolean = account.canHold(currency)

    override fun withdraw(amount: BigDecimal): EconomyAccountOperation {
        return account.withdraw(amount, currency).toOperation()
    }

    override fun deposit(amount: BigDecimal): EconomyAccountOperation {
        return account.deposit(amount, currency).toOperation()
    }

    private fun TransactionResult.toOperation(): EconomyAccountOperation {
        val operationStatus = when (status()) {
            TransactionResult.Status.SUCCESS -> EconomyAccountOperationStatus.SUCCESS
            TransactionResult.Status.INSUFFICIENT_FUNDS -> EconomyAccountOperationStatus.INSUFFICIENT_FUNDS
            TransactionResult.Status.CURRENCY_NOT_SUPPORTED -> EconomyAccountOperationStatus.CURRENCY_NOT_SUPPORTED
            else -> EconomyAccountOperationStatus.FAILED
        }
        return EconomyAccountOperation(operationStatus, balance().toBigDecimalOrNull())
    }

    private fun Number.toBigDecimalOrNull(): BigDecimal? {
        return toString().toBigDecimalOrNull()
            ?: runCatching { BigDecimal.valueOf(toDouble()) }.getOrNull()
    }
}
