package cn.tj.dzd.mc.dzt.ban

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * 玩家封禁的用例服务。
 *
 * @param repository 封禁持久化端口。
 * @param currentTimeMillis 当前时间来源，测试时可注入固定时间。
 * @param recordIdGenerator 新封禁记录 ID 的来源，测试时可注入固定值。
 */
class BanApplicationService(
    private val repository: BanRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val recordIdGenerator: () -> UUID = UUID::randomUUID,
) {

    /**
     * 按小时封禁玩家。
     *
     * 每次调用都会新增一条历史记录；若玩家已有未到期封禁，旧记录会标记为已提前解除，新记录立即生效。
     *
     * @param playerId 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，必须大于 0。
     * @param reason 封禁原因；空白原因会保存为默认说明。
     * @return 封禁操作结果。
     * @throws IllegalArgumentException 时长或原因不合法时抛出。
     */
    fun ban(playerId: UUID, hours: Long, reason: String): BanIssueResult {
        return ban(playerId, BigDecimal.valueOf(hours), reason)
    }

    /**
     * 按小时封禁玩家，支持小数时长。
     *
     * 不能精确表示为整数毫秒的小数时长会向上取整，确保实际封禁时间不会短于管理员指定的时长。
     *
     * @param playerId 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，可使用小数，必须大于 0。
     * @param reason 封禁原因；空白原因会保存为默认说明。
     * @return 封禁操作结果。
     * @throws IllegalArgumentException 时长或原因不合法时抛出。
     */
    fun ban(playerId: UUID, hours: BigDecimal, reason: String): BanIssueResult {
        val now = currentTimeMillis()
        val record = PlayerBan(
            recordId = recordIdGenerator(),
            playerId = playerId,
            bannedAt = now,
            unbanAt = calculateUnbanAt(now, hours),
            reason = normalizeReason(reason),
            active = true,
            releasedAt = NOT_RELEASED,
        )
        return when (repository.createReplacingActive(record)) {
            is RepositoryResult.Success -> BanIssueResult.Banned(record)
            RepositoryResult.Failure -> BanIssueResult.Failed
        }
    }

    /**
     * 提前解除玩家当前有效封禁。
     *
     * 历史记录会保留原定 [PlayerBan.unbanAt]，并记录本次提前解除时间。
     *
     * @param playerId 被解除玩家 UUID。
     * @return 解除结果。
     */
    fun unban(playerId: UUID): UnbanResult {
        return when (val result = repository.releaseActive(playerId, currentTimeMillis())) {
            is RepositoryResult.Success -> if (result.value) UnbanResult.UNBANNED else UnbanResult.NOT_BANNED
            RepositoryResult.Failure -> UnbanResult.FAILED
        }
    }

    /**
     * 查询玩家当前有效的封禁记录。
     *
     * @param playerId 玩家 UUID。
     * @return 当前封禁状态；基础设施异常会返回 [BanLookupResult.Unavailable]。
     */
    fun getActiveBan(playerId: UUID): BanLookupResult {
        return when (val result = repository.findActive(playerId, currentTimeMillis())) {
            is RepositoryResult.Success -> result.value?.let(BanLookupResult::Active) ?: BanLookupResult.NotBanned
            RepositoryResult.Failure -> BanLookupResult.Unavailable
        }
    }

    /**
     * 查询玩家全部封禁历史。
     *
     * @param playerId 玩家 UUID。
     * @return 按封禁时间倒序排列的历史；基础设施异常会返回 [BanHistoryResult.Unavailable]。
     */
    fun getHistory(playerId: UUID): BanHistoryResult {
        return when (val result = repository.findHistory(playerId)) {
            is RepositoryResult.Success -> BanHistoryResult.Available(
                result.value.sortedWith(
                    compareByDescending<PlayerBan> { it.bannedAt }.thenByDescending { it.recordId.toString() }
                )
            )

            RepositoryResult.Failure -> BanHistoryResult.Unavailable
        }
    }

    private fun calculateUnbanAt(now: Long, hours: BigDecimal): Long {
        require(hours.signum() > 0) { "封禁时长必须大于 0 小时。" }
        val durationMillis = try {
            hours.multiply(MILLIS_PER_HOUR)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact()
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("封禁时长超出可表示范围。", error)
        }
        require(durationMillis > 0L) { "封禁时长必须大于 0 小时。" }
        return try {
            Math.addExact(now, durationMillis)
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("解封时间超出可表示范围。", error)
        }
    }

    private fun normalizeReason(input: String): String {
        val normalized = input
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
            .ifBlank { DEFAULT_REASON }
        require(normalized.none { it == '\u0000' }) { "封禁原因不能包含 NUL 字符。" }
        require(normalized.length <= MAX_REASON_LENGTH) { "封禁原因不能超过 $MAX_REASON_LENGTH 个字符。" }
        return normalized
    }

    private companion object {
        const val NOT_RELEASED = 0L
        const val MAX_REASON_LENGTH = 256
        const val DEFAULT_REASON = "未提供封禁原因"
        val MILLIS_PER_HOUR: BigDecimal = BigDecimal.valueOf(3_600_000L)
    }
}
