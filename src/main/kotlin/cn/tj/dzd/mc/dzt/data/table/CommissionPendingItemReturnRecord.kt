package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.ColumnType
import taboolib.expansion.Id
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.TableName
import taboolib.expansion.UniqueKey
import taboolib.module.database.ColumnTypePostgreSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.ColumnTypeSQLite
import java.util.UUID

/**
 * 上交物品未能即时返还时保留的原始 ItemStack 数据。
 *
 * 该表是委托模块独立新增的补偿队列；[itemPayload] 使用 Paper 的 NBT 序列化字节，能够保留名称、附魔
 * 与数据组件，不会简化为裸材料后丢失物品信息。
 */
@TableName("commission_pending_item_return")
data class CommissionPendingItemReturnRecord(
    /** 本次待返还记录的稳定 ID。 */
    @param:Id
    @param:UniqueKey
    val returnId: UUID,
    /** 目标玩家 UUID，用于玩家登录时读取其待返还记录。 */
    @param:Key
    val playerId: UUID,
    /** 产生返还的委托 ID，仅用于审计与运维定位。 */
    @param:Length(64)
    val commissionId: String,
    /** Paper ItemStack 数组的保真 NBT 序列化数据。 */
    @param:ColumnType(
        sql = ColumnTypeSQL.LONGBLOB,
        sqlite = ColumnTypeSQLite.BLOB,
        postgresql = ColumnTypePostgreSQL.BYTEA,
    )
    val itemPayload: ByteArray,
    /** 入队时间的毫秒时间戳。 */
    val createdAt: Long,
)

/** CommissionPendingItemReturnRecord 使用的 Persistent Container 映射器。 */
val commissionPendingItemReturnRecordMapper by cachedMapper<CommissionPendingItemReturnRecord>(DataSource)

/** CommissionPendingItemReturnRecord 使用的下划线列名。 */
object CommissionPendingItemReturnColumns {
    const val RETURN_ID = "return_id"
    const val PLAYER_ID = "player_id"
}
