package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.TableName
import java.util.UUID

/**
 * 玩家某商品在北京时间自然日内的累计购买数量。
 *
 * 新业务表只由商店模块使用，不会改变任何既有数据表的结构。
 */
@TableName("shop_daily_purchase")
data class ShopDailyPurchaseRecord(
    @param:Key
    val playerId: UUID,
    @param:Key
    @param:Length(64)
    val productId: String,
    @param:Key
    @param:Length(10)
    val purchaseDate: String,
    var quantity: Int,
)

/** 商店每日限购记录的 TabooLib Persistent Container 映射器。 */
val shopDailyPurchaseRecordMapper by cachedMapper<ShopDailyPurchaseRecord>(DataSource)

/** ShopDailyPurchaseRecord 在 Persistent Container 中使用的下划线列名。 */
object ShopDailyPurchaseColumns {
    const val PLAYER_ID = "player_id"
    const val PRODUCT_ID = "product_id"
    const val PURCHASE_DATE = "purchase_date"
}
