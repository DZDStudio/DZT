package cn.tj.dzd.mc.dzt.economy

import java.math.BigDecimal

/**
 * 经济金额的纯文本规则。
 *
 * 该类不依赖 Bukkit 或 ServiceIO，可用于命令、Java Dialog 和基岩表单的统一校验。
 */
object EconomyAmounts {

    /**
     * 解析玩家输入的正数金额。
     *
     * @param input 仅允许普通十进制整数或小数的文本。
     * @return 移除无意义尾零的金额；非法、非正数或过长时返回 null。
     */
    fun parse(input: String): BigDecimal? {
        val normalized = input.trim()
        if (normalized.isEmpty() || normalized.length > MAX_INPUT_LENGTH) {
            return null
        }
        if (!PLAIN_DECIMAL_PATTERN.matches(normalized)) {
            return null
        }

        val amount = normalized.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
        if (amount <= BigDecimal.ZERO || !amount.toDouble().isFinite()) {
            return null
        }
        return amount
    }

    /**
     * 检查金额小数位是否受货币支持。
     *
     * @param amount 要检查的金额。
     * @param fractionalDigits 货币支持的小数位；负数表示不限制。
     * @return 小数精度是否可用。
     */
    fun supportsFractionalDigits(amount: BigDecimal, fractionalDigits: Int): Boolean {
        if (fractionalDigits < 0) {
            return true
        }
        return amount.stripTrailingZeros().scale().coerceAtLeast(0) <= fractionalDigits
    }

    /**
     * 将金额格式化为不使用科学计数法的文本。
     *
     * @param amount 要格式化的金额。
     * @return 移除无意义尾零的十进制文本。
     */
    fun format(amount: BigDecimal): String = amount.stripTrailingZeros().toPlainString()

    private const val MAX_INPUT_LENGTH = 32
    private val PLAIN_DECIMAL_PATTERN = Regex("[0-9]+(?:\\.[0-9]+)?")
}
