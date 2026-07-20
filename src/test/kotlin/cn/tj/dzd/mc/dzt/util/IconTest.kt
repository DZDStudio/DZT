package cn.tj.dzd.mc.dzt.util

import kotlin.test.Test
import kotlin.test.assertEquals

class IconTest {

    @Test
    fun `every persisted icon name resolves to its stable entry`() {
        Icon.entries.forEach { icon ->
            assertEquals(icon, Icon.requireJeName(icon.jeName))
        }
    }

    @Test
    fun `legacy ender eye alias resolves to the current icon`() {
        assertEquals(Icon.EYE_OF_ENDER, Icon.requireJeName("eye_of_ender"))
    }

}
