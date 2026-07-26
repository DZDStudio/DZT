package cn.tj.dzd.mc.dzt.ban

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BanTypeTest {

    @Test
    fun `legacy null type remains a server access ban`() {
        val type = BanType.fromStored(null)

        assertEquals(BanType.BAN, type)
        assertTrue(type.blocksServerEntry)
    }

    @Test
    fun `other stored types do not block server entry`() {
        val type = BanType.fromStored("mute")

        assertEquals("mute", type.value)
        assertFalse(type.blocksServerEntry)
    }

    @Test
    fun `command types are normalized and validated`() {
        assertEquals(BanType.FLY, BanType.fromCommand(" FLY "))
        assertNull(BanType.fromCommand("invalid type"))
        assertNull(BanType.fromCommand("1invalid"))
    }
}
