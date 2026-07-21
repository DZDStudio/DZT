package cn.tj.dzd.mc.dzt.commission

import cn.tj.dzd.mc.dzt.data.repository.PersistentCommissionProgressRepository
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.log.PlayerLogService
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.severe
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** 委托日界线，与商店一致使用北京时间。 */
val COMMISSION_TIME_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** 一个委托在玩家界面中的完整状态。 */
data class CommissionView(
    val definition: CommissionDefinition,
    val progress: Int,
    val claimState: CommissionClaimState,
) {
    /** 当前目标是否已完成。 */
    val completed: Boolean
        get() = progress >= definition.targetAmount
}

/** 玩家打开委托界面时的数据快照。 */
data class CommissionDashboard(
    val date: LocalDate,
    val commissions: List<CommissionView>,
)

/** 一次击杀造成的委托进度变更。 */
data class CommissionKillProgressUpdate(
    val definition: CommissionDefinition,
    val mutation: CommissionProgressMutation,
)

/** 可供背包协调器处理的上交请求。 */
data class CommissionItemSubmissionRequest(
    val date: LocalDate,
    val definition: CommissionDefinition,
    val remainingAmount: Int,
)

/** 准备上交物品时的校验结果。 */
sealed interface CommissionItemSubmissionPreparation {
    data class Ready(val request: CommissionItemSubmissionRequest) : CommissionItemSubmissionPreparation
    data object CommissionNotFound : CommissionItemSubmissionPreparation
    data object NotItemSubmission : CommissionItemSubmissionPreparation
    data object AlreadyCompleted : CommissionItemSubmissionPreparation
}

/** 上交物品被扣除后写入进度的结果。 */
sealed interface CommissionItemSubmissionCommit {
    data class Applied(val mutation: CommissionProgressMutation) : CommissionItemSubmissionCommit
    data object Expired : CommissionItemSubmissionCommit
    data object CommissionNotFound : CommissionItemSubmissionCommit
    data object NotItemSubmission : CommissionItemSubmissionCommit
    data object StorageFailure : CommissionItemSubmissionCommit
}

/** 委托奖励领取的最终结果。 */
enum class CommissionClaimStatus {
    SUCCESS,
    COMMISSION_NOT_FOUND,
    NOT_COMPLETED,
    EXPIRED,
    ALREADY_CLAIMED,
    CLAIM_IN_PROGRESS,
    REWARD_FAILED,
    RECORD_FAILURE,
}

/**
 * 委托奖励领取结果。
 *
 * [definition] 在委托不存在时为 null；其他状态均保留原始定义，供 UI 生成准确提示。
 */
data class CommissionClaimResult(
    val status: CommissionClaimStatus,
    val definition: CommissionDefinition?,
)

/**
 * 每日委托运行时服务。
 *
 * 该对象集中处理当天抽取、进度读取、击杀累计及奖励结算。UI 和事件监听器不得直接写数据库或调用经济服务。
 */
object CommissionService {

    private val repository: CommissionProgressRepository = PersistentCommissionProgressRepository

    /** 超过此时长仍未确认的预占不会自动重试发币，而是在启动审计中要求人工核对。 */
    private const val CLAIM_AUDIT_AFTER_MILLIS = 10 * 60 * 1000L

    /**
     * 在插件启用时列出可能因停服或外部经济 Future 中断而遗留的领奖预占。
     *
     * ServiceIO 当前没有可供查询的幂等交易 ID，因此这里绝不自动释放或重试，以免外部已成功入账时重复发币；
     * 控制台会保留操作 ID、玩家和委托信息，供管理员核对经济流水后处理。
     */
    @Awake(LifeCycle.ACTIVE)
    fun auditStaleClaimReservations() {
        val before = System.currentTimeMillis() - CLAIM_AUDIT_AFTER_MILLIS
        DztAsyncExecutor.supply {
            repository.findStaleClaims(before)
        }.whenComplete { result, error ->
            if (error != null || result !is RepositoryResult.Success) {
                return@whenComplete
            }
            result.value.forEach { claim ->
                severe(
                    "发现未确认的委托奖励预占，请核对 ServiceIO 经济流水后人工处理。",
                    "玩家 UUID: ${claim.playerId}",
                    "委托 ID: ${claim.commissionId}",
                    "委托日期: ${claim.date}",
                    "领奖操作 ID: ${claim.operationId ?: "缺失"}",
                    "预占时间: ${claim.reservedAt ?: "缺失"}",
                )
            }
        }
    }

    /**
     * 生成指定玩家当天的五个委托。
     *
     * @param playerId 玩家 UUID。
     * @return 两个简单、两个普通和一个困难委托的稳定选择结果。
     */
    fun todaySelection(playerId: UUID): DailyCommissionSelection {
        return DailyCommissionSelector.select(
            catalog = CommissionCatalogs.catalog,
            date = LocalDate.now(COMMISSION_TIME_ZONE),
            playerId = playerId,
        )
    }

    /**
     * 异步读取玩家当天的委托面板。
     *
     * @param playerId 玩家 UUID。
     * @return 包含该玩家五个委托及个人进度的 Future。
     */
    fun dashboard(playerId: UUID): CompletableFuture<CommissionDashboard> {
        val selection = todaySelection(playerId)
        return DztAsyncExecutor.supply {
            val records = when (val result = repository.findAll(playerId, selection.date)) {
                is RepositoryResult.Success -> result.value
                RepositoryResult.Failure -> error("无法读取每日委托进度。")
            }.associateBy(CommissionProgress::commissionId)

            CommissionDashboard(
                date = selection.date,
                commissions = selection.commissions.map { definition ->
                    val record = records[definition.id]
                    CommissionView(
                        definition = definition,
                        progress = (record?.progress ?: 0).coerceIn(0, definition.targetAmount),
                        claimState = record?.claimState ?: CommissionClaimState.UNCLAIMED,
                    )
                },
            )
        }
    }

    /**
     * 记录玩家击杀实体造成的全部委托进度。
     *
     * @param playerId 击杀者 UUID。
     * @param entityId 被击杀实体的原版命名空间 ID。
     * @return 被当前击杀匹配到的进度变更；没有匹配委托时返回空列表。
     */
    fun recordKill(playerId: UUID, entityId: String): CompletableFuture<List<CommissionKillProgressUpdate>> {
        val selection = todaySelection(playerId)
        val matches = selection.commissions.filter {
            it.objectiveType == CommissionObjectiveType.KILL_ENTITY && it.targetId == entityId
        }
        if (matches.isEmpty()) {
            return CompletableFuture.completedFuture(emptyList())
        }

        return DztAsyncExecutor.supply {
            matches.map { definition ->
                when (
                    val result = repository.advance(
                        playerId = playerId,
                        date = selection.date,
                        commissionId = definition.id,
                        amount = 1,
                        targetAmount = definition.targetAmount,
                    )
                ) {
                    is RepositoryResult.Success -> CommissionKillProgressUpdate(definition, result.value)
                    RepositoryResult.Failure -> error("无法记录击杀委托进度。")
                }
            }
        }
    }

    /**
     * 读取上交前仍需扣除的物品数量。
     *
     * @param playerId 玩家 UUID。
     * @param commissionId 今天展示的委托 ID。
     * @return 上交准备状态；实际背包扣除由 [CommissionItemSubmissionCoordinator] 完成。
     */
    fun prepareItemSubmission(
        playerId: UUID,
        commissionId: String,
    ): CompletableFuture<CommissionItemSubmissionPreparation> {
        return dashboard(playerId).thenApply { dashboard ->
            val commission = dashboard.commissions.firstOrNull { it.definition.id == commissionId }
                ?: return@thenApply CommissionItemSubmissionPreparation.CommissionNotFound
            if (commission.definition.objectiveType != CommissionObjectiveType.SUBMIT_ITEM) {
                return@thenApply CommissionItemSubmissionPreparation.NotItemSubmission
            }
            if (commission.completed || commission.claimState != CommissionClaimState.UNCLAIMED) {
                return@thenApply CommissionItemSubmissionPreparation.AlreadyCompleted
            }
            CommissionItemSubmissionPreparation.Ready(
                CommissionItemSubmissionRequest(
                    date = dashboard.date,
                    definition = commission.definition,
                    remainingAmount = commission.definition.targetAmount - commission.progress,
                )
            )
        }
    }

    /**
     * 将已经从玩家背包精确扣除的物品数量写入委托进度。
     *
     * 调用方必须只在 [CommissionItemSubmissionRequest.date] 仍是当前委托日时提交；日期变化或写入失败时，
     * 调用方应将对应物品返还给玩家。
     *
     * @param playerId 玩家 UUID。
     * @param date 上交准备阶段采样的自然日。
     * @param commissionId 委托稳定 ID。
     * @param amount 已从背包扣除的正数数量。
     * @return 写入结果及实际接受数量。
     */
    fun commitItemSubmission(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        amount: Int,
    ): CompletableFuture<CommissionItemSubmissionCommit> {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(CommissionItemSubmissionCommit.StorageFailure)
        }

        return DztAsyncExecutor.supply {
            // 异步任务可能在进入执行器后跨越零点，因此日期必须在实际写入前后都校验。
            val selection = todaySelection(playerId)
            if (selection.date != date) {
                CommissionItemSubmissionCommit.Expired
            } else {
                val definition = selection.commissions.firstOrNull { it.id == commissionId }
                when {
                    definition == null -> CommissionItemSubmissionCommit.CommissionNotFound
                    definition.objectiveType != CommissionObjectiveType.SUBMIT_ITEM -> {
                        CommissionItemSubmissionCommit.NotItemSubmission
                    }

                    else -> when (
                        val result = repository.advance(
                            playerId = playerId,
                            date = date,
                            commissionId = commissionId,
                            amount = amount,
                            targetAmount = definition.targetAmount,
                        )
                    ) {
                        is RepositoryResult.Success -> CommissionItemSubmissionCommit.Applied(result.value)
                        RepositoryResult.Failure -> CommissionItemSubmissionCommit.StorageFailure
                    }.let { committed ->
                        // 若写入刚好跨越日界线，协调器会返还已扣除的物品；遗留的旧日进度不会在新面板出现。
                        if (LocalDate.now(COMMISSION_TIME_ZONE) == date) committed
                        else CommissionItemSubmissionCommit.Expired
                    }
                }
            }
        }
    }

    /**
     * 领取一个已完成委托的 DDB 奖励。
     *
     * 领取权会先在数据库中预占，避免重复表单回调触发多次入账。经济服务明确失败时会释放预占；网络异常等
     * 无法确认是否已入账的情况、以及入账成功但最终状态写入失败，都会保留 `CLAIMING`，以宁可人工核对也不重复发币。
     *
     * @param playerId 玩家 UUID。
     * @param commissionId 今日委托 ID。
     * @return 最终领取状态。
     */
    fun claim(playerId: UUID, commissionId: String): CompletableFuture<CommissionClaimResult> {
        val selection = todaySelection(playerId)
        val definition = selection.commissions.firstOrNull { it.id == commissionId }
            ?: return CompletableFuture.completedFuture(
                CommissionClaimResult(CommissionClaimStatus.COMMISSION_NOT_FOUND, null)
            )

        return DztAsyncExecutor.supply {
            if (LocalDate.now(COMMISSION_TIME_ZONE) != selection.date) {
                CommissionClaimReservation.Expired
            } else {
                when (
                    val result = repository.reserveClaim(
                        playerId = playerId,
                        date = selection.date,
                        commissionId = definition.id,
                        targetAmount = definition.targetAmount,
                    )
                ) {
                    is RepositoryResult.Success -> result.value
                    RepositoryResult.Failure -> null
                }
            }
        }.thenCompose { reservation ->
            when (reservation) {
                is CommissionClaimReservation.Reserved -> {
                    payClaimReward(playerId, selection.date, definition, reservation.operationId)
                }

                CommissionClaimReservation.NotCompleted -> CompletableFuture.completedFuture(
                    CommissionClaimResult(CommissionClaimStatus.NOT_COMPLETED, definition)
                )

                CommissionClaimReservation.Expired -> CompletableFuture.completedFuture(
                    CommissionClaimResult(CommissionClaimStatus.EXPIRED, definition)
                )

                CommissionClaimReservation.AlreadyClaimed -> CompletableFuture.completedFuture(
                    CommissionClaimResult(CommissionClaimStatus.ALREADY_CLAIMED, definition)
                )

                is CommissionClaimReservation.ClaimInProgress -> CompletableFuture.completedFuture(
                    CommissionClaimResult(CommissionClaimStatus.CLAIM_IN_PROGRESS, definition)
                )

                null -> CompletableFuture.completedFuture(
                    CommissionClaimResult(CommissionClaimStatus.RECORD_FAILURE, definition)
                )
            }
        }
    }

    private fun payClaimReward(
        playerId: UUID,
        date: LocalDate,
        definition: CommissionDefinition,
        operationId: UUID,
    ): CompletableFuture<CommissionClaimResult> {
        return ServiceEconomy.reward(playerId, definition.reward)
            .handle { reward, error ->
                ClaimRewardAttempt(
                    successful = error == null && reward?.successful == true,
                    definitive = error == null,
                )
            }
            .thenCompose { attempt ->
                DztAsyncExecutor.supply {
                    if (!attempt.successful && !attempt.definitive) {
                        severe(
                            "委托奖励请求发生不确定异常，保留领取预占以避免重复发币，请人工核对。",
                            "玩家 UUID: $playerId",
                            "委托 ID: ${definition.id}",
                            "日期: $date",
                            "领奖操作 ID: $operationId",
                        )
                        CommissionClaimResult(CommissionClaimStatus.RECORD_FAILURE, definition)
                    } else if (!attempt.successful) {
                        when (repository.releaseClaim(playerId, date, definition.id, operationId)) {
                            is RepositoryResult.Success -> {
                                CommissionClaimResult(CommissionClaimStatus.REWARD_FAILED, definition)
                            }

                            RepositoryResult.Failure -> {
                                severe(
                                    "委托奖励入账失败且预占状态无法释放，请人工核对。",
                                    "玩家 UUID: $playerId",
                                    "委托 ID: ${definition.id}",
                                    "日期: $date",
                                    "领奖操作 ID: $operationId",
                                )
                                CommissionClaimResult(CommissionClaimStatus.RECORD_FAILURE, definition)
                            }
                        }
                    } else {
                        PlayerLogService.recordCommissionReward(
                            playerId = playerId,
                            commissionId = definition.id,
                            amount = definition.reward,
                        )
                        when (repository.completeClaim(playerId, date, definition.id, operationId)) {
                            is RepositoryResult.Success -> {
                                CommissionClaimResult(CommissionClaimStatus.SUCCESS, definition)
                            }

                            RepositoryResult.Failure -> {
                                severe(
                                    "委托奖励已入账但领取状态无法确认，请人工核对，禁止直接重试发币。",
                                    "玩家 UUID: $playerId",
                                    "委托 ID: ${definition.id}",
                                    "日期: $date",
                                    "领奖操作 ID: $operationId",
                                )
                                CommissionClaimResult(CommissionClaimStatus.RECORD_FAILURE, definition)
                            }
                        }
                    }
                }
            }
    }
}

/** ServiceIO 奖励 Future 的确定性快照，用于区分明确失败和无法确认的异常。 */
private data class ClaimRewardAttempt(
    val successful: Boolean,
    val definitive: Boolean,
)
