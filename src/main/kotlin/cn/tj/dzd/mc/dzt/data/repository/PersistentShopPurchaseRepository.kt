package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.ShopDailyPurchaseRecord
import cn.tj.dzd.mc.dzt.data.table.ShopDailyPurchaseColumns
import cn.tj.dzd.mc.dzt.data.table.shopDailyPurchaseRecordMapper
import cn.tj.dzd.mc.dzt.shop.ShopLimitReservationResult
import cn.tj.dzd.mc.dzt.shop.ShopPurchaseRepository
import java.time.LocalDate
import java.util.UUID

/**
 * 基于 TabooLib Persistent Container 的商店每日限购仓库。
 *
 * 只访问新增的 `shop_daily_purchase` 表；既有业务表不会被商店模块修改。
 */
object PersistentShopPurchaseRepository : ShopPurchaseRepository {

    override fun purchasedQuantity(
        playerId: UUID,
        productId: String,
        date: LocalDate,
    ): RepositoryResult<Int> {
        return DatabaseGuard.execute("读取商店每日限购", RepositoryResult.Failure) {
            RepositoryResult.Success(findRecord(playerId, productId, date)?.quantity ?: 0)
        }
    }

    override fun reserve(
        playerId: UUID,
        productId: String,
        date: LocalDate,
        quantity: Int,
        dailyLimit: Int,
    ): ShopLimitReservationResult {
        if (quantity <= 0 || dailyLimit <= 0) {
            return ShopLimitReservationResult.InfrastructureFailure
        }

        return DatabaseGuard.execute(
            "预占商店每日限购",
            ShopLimitReservationResult.InfrastructureFailure,
        ) {
            shopDailyPurchaseRecordMapper.transaction {
                val record = findOne {
                    ShopDailyPurchaseColumns.PLAYER_ID eq playerId.toString()
                    ShopDailyPurchaseColumns.PRODUCT_ID eq productId
                    ShopDailyPurchaseColumns.PURCHASE_DATE eq date.toString()
                }
                val purchased = record?.quantity ?: 0
                if (purchased > dailyLimit - quantity) {
                    return@transaction ShopLimitReservationResult.LimitExceeded(purchased)
                }

                val nextQuantity = purchased + quantity
                if (record == null) {
                    insert(
                        ShopDailyPurchaseRecord(
                            playerId = playerId,
                            productId = productId,
                            purchaseDate = date.toString(),
                            quantity = nextQuantity,
                        )
                    )
                } else {
                    // This table was first released without an explicit @Id. Keep its schema stable by
                    // updating its composite @Key row directly instead of introducing a new primary column.
                    rawUpdate {
                        set("quantity", nextQuantity)
                        where {
                            ShopDailyPurchaseColumns.PLAYER_ID eq playerId.toString()
                            ShopDailyPurchaseColumns.PRODUCT_ID eq productId
                            ShopDailyPurchaseColumns.PURCHASE_DATE eq date.toString()
                        }
                    }
                }
                ShopLimitReservationResult.Reserved(nextQuantity)
            }.getOrThrow()
        }
    }

    override fun release(
        playerId: UUID,
        productId: String,
        date: LocalDate,
        quantity: Int,
    ): RepositoryResult<Unit> {
        if (quantity <= 0) {
            return RepositoryResult.Failure
        }

        return DatabaseGuard.execute("释放商店每日限购", RepositoryResult.Failure) {
            shopDailyPurchaseRecordMapper.transaction {
                val record = findOne {
                    ShopDailyPurchaseColumns.PLAYER_ID eq playerId.toString()
                    ShopDailyPurchaseColumns.PRODUCT_ID eq productId
                    ShopDailyPurchaseColumns.PURCHASE_DATE eq date.toString()
                } ?: return@transaction
                val remaining = record.quantity - quantity
                if (remaining <= 0) {
                    deleteWhere {
                        ShopDailyPurchaseColumns.PLAYER_ID eq playerId.toString()
                        ShopDailyPurchaseColumns.PRODUCT_ID eq productId
                        ShopDailyPurchaseColumns.PURCHASE_DATE eq date.toString()
                    }
                } else {
                    rawUpdate {
                        set("quantity", remaining)
                        where {
                            ShopDailyPurchaseColumns.PLAYER_ID eq playerId.toString()
                            ShopDailyPurchaseColumns.PRODUCT_ID eq productId
                            ShopDailyPurchaseColumns.PURCHASE_DATE eq date.toString()
                        }
                    }
                }
            }.getOrThrow()
            RepositoryResult.Success(Unit)
        }
    }

    private fun findRecord(playerId: UUID, productId: String, date: LocalDate): ShopDailyPurchaseRecord? {
        return shopDailyPurchaseRecordMapper.findOne {
            ShopDailyPurchaseColumns.PLAYER_ID eq playerId.toString()
            ShopDailyPurchaseColumns.PRODUCT_ID eq productId
            ShopDailyPurchaseColumns.PURCHASE_DATE eq date.toString()
        }
    }
}
