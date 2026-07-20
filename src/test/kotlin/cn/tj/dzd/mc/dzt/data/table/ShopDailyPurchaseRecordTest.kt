package cn.tj.dzd.mc.dzt.data.table

import kotlin.test.Test
import kotlin.test.assertEquals

class ShopDailyPurchaseRecordTest {

    @Test
    fun `manual shop query columns match Persistent Container snake case naming`() {
        assertEquals("player_id", ShopDailyPurchaseColumns.PLAYER_ID)
        assertEquals("product_id", ShopDailyPurchaseColumns.PRODUCT_ID)
        assertEquals("purchase_date", ShopDailyPurchaseColumns.PURCHASE_DATE)
    }
}
