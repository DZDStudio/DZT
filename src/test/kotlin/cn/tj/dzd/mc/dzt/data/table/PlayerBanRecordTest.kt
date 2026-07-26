package cn.tj.dzd.mc.dzt.data.table

import kotlin.test.Test
import kotlin.test.assertFalse

class PlayerBanRecordTest {

    @Test
    fun `persistent ban record constructor does not use Kotlin default arguments`() {
        assertFalse(
            PlayerBanRecord::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.any { type ->
                    type.name == "kotlin.jvm.internal.DefaultConstructorMarker"
                }
            },
        )
    }
}
