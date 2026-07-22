package cn.tj.dzd.mc.dzt.log

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerLogTextTest {

    @Test
    fun `supplementary emoji is encoded for utf8mb3 storage`() {
        assertEquals(
            "内容：\\U0001F923\\U0001F923",
            PlayerLogText.normalizeForStorage("内容：🤣🤣", 128),
        )
    }

    @Test
    fun `newline and malformed surrogate are stored safely`() {
        assertEquals(
            "第一行 第二行\\uD83E",
            PlayerLogText.normalizeForStorage("第一行\n第二行\uD83E", 128),
        )
    }

    @Test
    fun `limit keeps complete escapes and valid characters only`() {
        assertEquals(
            "日志\\U0001F923",
            PlayerLogText.normalizeForStorage("日志🤣后续内容", 12),
        )
        assertEquals(
            "日志",
            PlayerLogText.normalizeForStorage("日志🤣后续内容", 11),
        )
    }
}
