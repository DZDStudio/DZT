package cn.tj.dzd.mc.dzt.title

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TitlePolicyTest {

    @Test
    fun `title id is trimmed and normalized with locale independent lowercase`() {
        assertEquals("event.winner-1", TitlePolicy.normalizeId(" EVENT.WINNER-1 "))
    }

    @Test
    fun `title id rejects whitespace and unsupported characters`() {
        assertFailsWith<IllegalArgumentException> { TitlePolicy.normalizeId("two words") }
        assertFailsWith<IllegalArgumentException> { TitlePolicy.normalizeId("称号") }
        assertFailsWith<IllegalArgumentException> { TitlePolicy.normalizeId(" ") }
    }

    @Test
    fun `display name is trimmed but cannot contain line breaks`() {
        assertEquals("§6冠军", TitlePolicy.normalizeDisplayName("  §6冠军  "))
        assertFailsWith<IllegalArgumentException> { TitlePolicy.normalizeDisplayName("第一行\n第二行") }
    }

    @Test
    fun `description normalizes all line endings`() {
        assertEquals("一\n二\n三", TitlePolicy.normalizeDescription(" 一\r\n二\r三 "))
        assertFailsWith<IllegalArgumentException> { TitlePolicy.normalizeDescription("bad\u0000value") }
    }
}
