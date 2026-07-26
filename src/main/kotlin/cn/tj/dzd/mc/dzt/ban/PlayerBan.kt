package cn.tj.dzd.mc.dzt.ban

import java.util.UUID

/**
 * 一条可审计的玩家封禁记录。
 *
 * [unbanAt] 是计划自动解封时间，[releasedAt] 则记录提前手动解封或被新封禁替换的时间。封禁历史不会因到期或
 * 解除而删除。
 *
 * @property recordId 本次封禁记录的稳定 ID。
 * @property playerId 被封禁玩家 UUID。
 * @property bannedAt 封禁开始的 Unix 毫秒时间戳。
 * @property unbanAt 计划自动解封的 Unix 毫秒时间戳。
 * @property reason 封禁原因。
 * @property active 是否尚未被手动解除或后一次封禁替换。
 * @property releasedAt 提前解除时间；0 表示未提前解除。
 * @property type 封禁作用域；未指定时为 [BanType.BAN]，保证历史调用仍禁止登录。
 */
data class PlayerBan(
    val recordId: UUID,
    val playerId: UUID,
    val bannedAt: Long,
    val unbanAt: Long,
    val reason: String,
    val active: Boolean,
    val releasedAt: Long,
    val type: BanType = BanType.BAN,
) {

    /**
     * 判断此记录在指定时间是否仍应阻止玩家登录。
     *
     * @param currentTimeMillis 当前 Unix 毫秒时间戳。
     * @return 当记录未提前解除且计划解封时间尚未来到时返回 true。
     */
    fun isEffectiveAt(currentTimeMillis: Long): Boolean {
        return active && unbanAt > currentTimeMillis
    }
}

/** 玩家执行封禁后的结果。 */
sealed interface BanIssueResult {

    /** 封禁记录已持久化。 */
    data class Banned(val record: PlayerBan) : BanIssueResult

    /** 持久化失败，未确认封禁是否已生效。 */
    data object Failed : BanIssueResult
}

/** 查询玩家当前封禁状态的结果。 */
sealed interface BanLookupResult {

    /** 玩家当前存在有效封禁。 */
    data class Active(val record: PlayerBan) : BanLookupResult

    /** 玩家当前没有有效封禁。 */
    data object NotBanned : BanLookupResult

    /** 无法读取持久化数据，不能可靠判断封禁状态。 */
    data object Unavailable : BanLookupResult
}

/** 查询玩家封禁历史的结果。 */
sealed interface BanHistoryResult {

    /** 成功读取到的封禁记录，按封禁时间倒序排列。 */
    data class Available(val records: List<PlayerBan>) : BanHistoryResult

    /** 无法读取持久化数据。 */
    data object Unavailable : BanHistoryResult
}

/** 解除玩家当前有效封禁后的结果。 */
enum class UnbanResult {
    /** 当前有效封禁已被解除，历史记录仍被保留。 */
    UNBANNED,

    /** 玩家本来就没有有效封禁。 */
    NOT_BANNED,

    /** 持久化失败，未确认解封是否已生效。 */
    FAILED,
}
