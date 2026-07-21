package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import taboolib.expansion.Id
import taboolib.expansion.Length
import taboolib.expansion.UniqueKey
import taboolib.expansion.mapper
import taboolib.expansion.Key
import taboolib.expansion.TableName
import java.util.UUID

/**
 * 玩家在北京时间自然日内的委托进度与奖励领取状态。
 *
 * 这是委托模块独立新增的表，不会读取、迁移或修改任何既有业务表。
 */
@TableName("commission_daily_progress")
data class CommissionDailyProgressRecord(
    /** 由玩家、日期和委托 ID 推导的稳定唯一业务键，防止多实例重复创建同一逻辑记录。 */
    @param:Id
    @param:UniqueKey
    @param:Length(36)
    val recordKey: String,
    @param:Key
    val playerId: UUID,
    @param:Key
    @param:Length(10)
    val commissionDate: String,
    @param:Key
    @param:Length(64)
    val commissionId: String,
    var progress: Int,
    @param:Length(16)
    var claimState: String,
    /** 当前领奖预占的操作 ID；未预占时为空字符串。 */
    @param:Length(36)
    var claimOperationId: String,
    /** 当前领奖预占的创建时间（毫秒时间戳）；未预占时为 0。 */
    var claimReservedAt: Long,
)

/**
 * 委托每日进度在 Persistent Container 中的映射器。
 *
 * 进度可能由多台共享 MySQL 的游戏服同时写入。这里刻意不使用进程内 L2 缓存，避免跨实例读取陈旧状态；
 * 原子更新由仓库内的条件 SQL 完成。
 */
val commissionDailyProgressRecordMapper by mapper<CommissionDailyProgressRecord>(DataSource)

/** CommissionDailyProgressRecord 使用的下划线列名。 */
object CommissionDailyProgressColumns {
    const val RECORD_KEY = "record_key"
    const val PLAYER_ID = "player_id"
    const val COMMISSION_DATE = "commission_date"
    const val COMMISSION_ID = "commission_id"
    const val PROGRESS = "progress"
    const val CLAIM_STATE = "claim_state"
    const val CLAIM_OPERATION_ID = "claim_operation_id"
    const val CLAIM_RESERVED_AT = "claim_reserved_at"
}
