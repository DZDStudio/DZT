package cn.tj.dzd.mc.dzt.economy

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EconomyAmountsTest {

    @Test
    fun `parse accepts plain positive decimals and strips trailing zeros`() {
        assertEquals(BigDecimal("12.5"), EconomyAmounts.parse(" 0012.500 "))
        assertEquals(BigDecimal("1E+1"), EconomyAmounts.parse("10"))
    }

    @Test
    fun `parse rejects signs exponents zero and malformed decimals`() {
        listOf("", "0", "-1", "+1", ".5", "1.", "1e3", "NaN", "1,000").forEach { input ->
            assertNull(EconomyAmounts.parse(input), "Expected '$input' to be rejected")
        }
    }

    @Test
    fun `fractional digit policy ignores meaningless trailing zeros`() {
        assertTrue(EconomyAmounts.supportsFractionalDigits(BigDecimal("1.2300"), 2))
        assertFalse(EconomyAmounts.supportsFractionalDigits(BigDecimal("1.234"), 2))
        assertTrue(EconomyAmounts.supportsFractionalDigits(BigDecimal("1.234"), -1))
    }

    @Test
    fun `format never uses scientific notation`() {
        assertEquals("1000", EconomyAmounts.format(BigDecimal("1E+3")))
        assertEquals("12.5", EconomyAmounts.format(BigDecimal("12.500")))
    }
}
