package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import taboolib.expansion.Id
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.TableName
import taboolib.expansion.UniqueKey
import taboolib.expansion.mapper
import java.util.UUID

/**
 * 玩家封禁历史的持久化记录。
 *
 * 每次封禁都会新增一条记录，历史记录永不删除。`active` 为 false 表示该记录已被手动解除或被后一次封禁替换；
 * `releasedAt` 为 0 时表示尚未发生这类提前解除。到达 [unbanAt] 的记录仍会保留，以便查询历史。
 */
@TableName("player_ban_record")
data class PlayerBanRecord(
    /** 本次封禁记录的稳定唯一 ID。 */
    @param:Id
    @param:UniqueKey
    val recordId: UUID,
    /** 被封禁玩家的 UUID。 */
    @param:Key
    val playerId: UUID,
    /** 记录是否仍可能产生封禁效果。 */
    @param:Key
    var active: Boolean,
    /** 执行封禁时的 Unix 毫秒时间戳。 */
    @param:Key
    val bannedAt: Long,
    /** 计划自动解封时的 Unix 毫秒时间戳。 */
    @param:Key
    val unbanAt: Long,
    /** 封禁原因。 */
    @param:Length(256)
    val reason: String,
    /** 手动解除或被新封禁替换时的 Unix 毫秒时间戳；0 表示未提前解除。 */
    var releasedAt: Long,
)

/**
 * 玩家封禁记录的 Persistent Container 映射器。
 *
 * 封禁状态可能由多台共享 MySQL 的服务器读写，登录校验必须读取最新数据，因此这里刻意不启用进程内 L2 缓存。
 */
val playerBanRecordMapper by mapper<PlayerBanRecord>(DataSource)

/** PlayerBanRecord 使用的下划线列名。 */
object PlayerBanColumns {
    const val RECORD_ID = "record_id"
    const val PLAYER_ID = "player_id"
    const val ACTIVE = "active"
    const val BANNED_AT = "banned_at"
    const val UNBAN_AT = "unban_at"
    const val REASON = "reason"
    const val RELEASED_AT = "released_at"
}
