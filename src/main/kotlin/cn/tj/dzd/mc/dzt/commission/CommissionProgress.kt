package cn.tj.dzd.mc.dzt.commission

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.time.LocalDate
import java.util.UUID

/** 玩家当天一个委托的持久化进度快照。 */
data class CommissionProgress(
    val playerId: UUID,
    val date: LocalDate,
    val commissionId: String,
    val progress: Int,
    val claimState: CommissionClaimState,
)

/** 委托奖励的领取状态。 */
enum class CommissionClaimState {
    UNCLAIMED,
    CLAIMING,
    CLAIMED,
}

/** 一次进度累加的原子结果。 */
data class CommissionProgressMutation(
    val previousProgress: Int,
    val currentProgress: Int,
    val acceptedAmount: Int,
    val claimState: CommissionClaimState,
) {
    /** 这次写入是否刚好让委托达成目标。 */
    fun becameComplete(targetAmount: Int): Boolean = previousProgress < targetAmount && currentProgress >= targetAmount
}

/** 尝试锁定奖励领取权的结果。 */
sealed interface CommissionClaimReservation {
    /** 已持久化的领奖预占；[operationId] 必须用于后续确认或释放。 */
    data class Reserved(val operationId: UUID) : CommissionClaimReservation
    data object NotCompleted : CommissionClaimReservation
    data object AlreadyClaimed : CommissionClaimReservation
    /** 点击进入异步结算队列后已跨越委托日界线。 */
    data object Expired : CommissionClaimReservation
    /** 另一条领奖流程已占用该委托，不能直接重试发币。 */
    data class ClaimInProgress(
        val operationId: UUID?,
        val reservedAt: Long?,
    ) : CommissionClaimReservation
}

/**
 * 超过正常结算窗口仍未结束的领奖预占，用于启动时的人工审计。
 *
 * ServiceIO 未提供可查询的幂等交易键，因此此类记录不能自动释放，否则外部入账已成功但回写失败时可能重复发币。
 */
data class CommissionStaleClaim(
    val playerId: UUID,
    val date: LocalDate,
    val commissionId: String,
    val operationId: UUID?,
    val reservedAt: Long?,
)

/**
 * 委托进度的持久化端口。
 *
 * 所有写入方法必须以数据库事务实现，避免多个击杀事件或重复表单回调造成进度、奖励重复结算。
 */
interface CommissionProgressRepository {

    /**
     * 读取玩家在指定自然日的所有委托进度。
     *
     * @param playerId 玩家 UUID。
     * @param date 北京时间自然日。
     * @return 成功时返回当天已创建的进度记录；从未产生进度的委托不会有记录。
     */
    fun findAll(playerId: UUID, date: LocalDate): RepositoryResult<List<CommissionProgress>>

    /**
     * 原子增加一个委托的进度，并将结果限制在 [targetAmount] 内。
     *
     * @param playerId 玩家 UUID。
     * @param date 北京时间自然日。
     * @param commissionId 委托稳定 ID。
     * @param amount 要累计的正数数量。
     * @param targetAmount 当前目录中定义的目标数量。
     * @return 成功时返回实际接受的数量及更新后的快照。
     */
    fun advance(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        amount: Int,
        targetAmount: Int,
    ): RepositoryResult<CommissionProgressMutation>

    /**
     * 为已完成委托原子预占奖励领取权。
     *
     * 预占成功后调用方必须在经济入账结果明确后使用返回的操作 ID 调用 [completeClaim] 或 [releaseClaim]。
     */
    fun reserveClaim(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        targetAmount: Int,
    ): RepositoryResult<CommissionClaimReservation>

    /**
     * 将已预占的奖励标记为已领取。
     *
     * @param playerId 玩家 UUID。
     * @param date 北京时间自然日。
     * @param commissionId 委托稳定 ID。
     * @param operationId reserveClaim 返回的预占操作 ID，只能确认对应的那次预占。
     * @return 写入是否成功。
     */
    fun completeClaim(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        operationId: UUID,
    ): RepositoryResult<Unit>

    /**
     * 释放经济入账失败后的奖励预占，使玩家可以重试。
     *
     * @param playerId 玩家 UUID。
     * @param date 北京时间自然日。
     * @param commissionId 委托稳定 ID。
     * @param operationId reserveClaim 返回的预占操作 ID，只能释放对应的那次预占。
     * @return 写入是否成功。
     */
    fun releaseClaim(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        operationId: UUID,
    ): RepositoryResult<Unit>

    /**
     * 查询早于 [beforeEpochMillis] 仍处于预占状态的领奖记录，供运维核对经济流水。
     *
     * @param beforeEpochMillis 预占时间早于该时间点的记录会被返回。
     * @return 尚未确认或释放的预占记录。
     */
    fun findStaleClaims(beforeEpochMillis: Long): RepositoryResult<List<CommissionStaleClaim>>
}
