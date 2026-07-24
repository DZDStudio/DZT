package cn.tj.dzd.mc.dzt.util

import taboolib.common.platform.ProxyCommandSender
import kotlin.test.Test
import kotlin.test.assertNull

class ProxyCommandSenderTest {

    @Test
    fun `non player command senders are not force cast to Bukkit players`() {
        val sender = object : ProxyCommandSender {
            override val origin: Any = Any()
            override val name: String = "Console"
            override var isOp: Boolean = true

            override fun isOnline(): Boolean = true

            override fun sendMessage(message: String) = Unit

            override fun performCommand(command: String): Boolean = false

            override fun hasPermission(permission: String): Boolean = true
        }

        assertNull(sender.bukkitPlayerOrNull())
    }
}
