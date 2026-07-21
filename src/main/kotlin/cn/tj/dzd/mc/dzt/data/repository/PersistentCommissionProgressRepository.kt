package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.commission.CommissionClaimReservation
import cn.tj.dzd.mc.dzt.commission.CommissionClaimState
import cn.tj.dzd.mc.dzt.commission.CommissionProgress
import cn.tj.dzd.mc.dzt.commission.CommissionProgressMutation
import cn.tj.dzd.mc.dzt.commission.CommissionProgressRepository
import cn.tj.dzd.mc.dzt.commission.CommissionStaleClaim
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.CommissionDailyProgressColumns
import cn.tj.dzd.mc.dzt.data.table.CommissionDailyProgressRecord
import cn.tj.dzd.mc.dzt.data.table.commissionDailyProgressRecordMapper
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.UUID

/**
 * 基于 TabooLib Persistent Container 的委托进度仓库。
 *
 * 此实现只访问新增的 `commission_daily_progress` 表。每条逻辑进度记录都使用 [recordKey] 建立数据库唯一键，
 * 并通过带条件的数据库更新完成进度累加及领奖预占，避免仅依赖单台服务器的内存锁。
 */
object PersistentCommissionProgressRepository : CommissionProgressRepository {

    override fun findAll(
        playerId: UUID,
        date: LocalDate,
    ): RepositoryResult<List<CommissionProgress>> {
        return DatabaseGuard.execute("读取每日委托进度", RepositoryResult.Failure) {
            RepositoryResult.Success(
                commissionDailyProgressRecordMapper.findAll {
                    CommissionDailyProgressColumns.PLAYER_ID eq playerId.toString()
                    CommissionDailyProgressColumns.COMMISSION_DATE eq date.toString()
                }.map { record -> record.toDomain() }
            )
        }
    }

    override fun advance(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        amount: Int,
        targetAmount: Int,
    ): RepositoryResult<CommissionProgressMutation> {
        if (amount <= 0 || targetAmount <= 0) {
            return RepositoryResult.Failure
        }

        return DatabaseGuard.execute("增加每日委托进度", RepositoryResult.Failure) {
            val key = recordKey(playerId, date, commissionId)
            ensureRecord(playerId, date, commissionId, key)
            for (attempt in 0 until COMPARE_AND_SET_RETRY_LIMIT) {
                val before = findRecord(key) ?: error("委托进度记录丢失: $key")
                val previous = before.progress.coerceIn(0, targetAmount)
                val claimState = before.claimState.toClaimState()
                if (claimState != CommissionClaimState.UNCLAIMED || previous >= targetAmount) {
                    return@execute RepositoryResult.Success(
                        CommissionProgressMutation(
                            previousProgress = previous,
                            currentProgress = previous,
                            acceptedAmount = 0,
                            claimState = claimState,
                        )
                    )
                }

                val accepted = minOf(amount, targetAmount - previous)
                val changed = commissionDailyProgressRecordMapper.rawUpdate {
                    set(CommissionDailyProgressColumns.PROGRESS, previous + accepted)
                    where {
                        CommissionDailyProgressColumns.RECORD_KEY eq key
                        CommissionDailyProgressColumns.PROGRESS eq before.progress
                        CommissionDailyProgressColumns.CLAIM_STATE eq CommissionClaimState.UNCLAIMED.name
                    }
                }
                if (changed == 1) {
                    return@execute RepositoryResult.Success(
                        CommissionProgressMutation(
                            previousProgress = previous,
                            currentProgress = previous + accepted,
                            acceptedAmount = accepted,
                            claimState = CommissionClaimState.UNCLAIMED,
                        )
                    )
                }
                // A concurrent server changed progress or claim state after the read. Re-read on a new
                // auto-commit statement so MySQL's transaction snapshot cannot leak into the next attempt.
            }
            RepositoryResult.Failure
        }
    }

    override fun reserveClaim(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        targetAmount: Int,
    ): RepositoryResult<CommissionClaimReservation> {
        if (targetAmount <= 0) {
            return RepositoryResult.Failure
        }

        return DatabaseGuard.execute("预占委托奖励", RepositoryResult.Failure) {
            val key = recordKey(playerId, date, commissionId)
            for (attempt in 0 until COMPARE_AND_SET_RETRY_LIMIT) {
                val record = findRecord(key) ?: return@execute RepositoryResult.Success(
                    CommissionClaimReservation.NotCompleted
                )
                if (record.progress < targetAmount) {
                    return@execute RepositoryResult.Success(CommissionClaimReservation.NotCompleted)
                }

                when (record.claimState.toClaimState()) {
                    CommissionClaimState.UNCLAIMED -> {
                        val operationId = UUID.randomUUID()
                        val reservedAt = System.currentTimeMillis()
                        val changed = commissionDailyProgressRecordMapper.rawUpdate {
                            set(CommissionDailyProgressColumns.CLAIM_STATE, CommissionClaimState.CLAIMING.name)
                            set(CommissionDailyProgressColumns.CLAIM_OPERATION_ID, operationId.toString())
                            set(CommissionDailyProgressColumns.CLAIM_RESERVED_AT, reservedAt)
                            where {
                                CommissionDailyProgressColumns.RECORD_KEY eq key
                                CommissionDailyProgressColumns.CLAIM_STATE eq CommissionClaimState.UNCLAIMED.name
                                CommissionDailyProgressColumns.PROGRESS gte targetAmount
                            }
                        }
                        if (changed == 1) {
                            return@execute RepositoryResult.Success(
                                CommissionClaimReservation.Reserved(operationId)
                            )
                        }
                    }

                    CommissionClaimState.CLAIMING,
                    CommissionClaimState.CLAIMED,
                    -> return@execute RepositoryResult.Success(reservationFrom(record))
                }
            }
            RepositoryResult.Failure
        }
    }

    override fun completeClaim(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        operationId: UUID,
    ): RepositoryResult<Unit> {
        return updateClaimState(
            action = "确认委托奖励领取",
            playerId = playerId,
            date = date,
            commissionId = commissionId,
            operationId = operationId,
            next = CommissionClaimState.CLAIMED,
        )
    }

    override fun releaseClaim(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        operationId: UUID,
    ): RepositoryResult<Unit> {
        return updateClaimState(
            action = "释放委托奖励预占",
            playerId = playerId,
            date = date,
            commissionId = commissionId,
            operationId = operationId,
            next = CommissionClaimState.UNCLAIMED,
        )
    }

    override fun findStaleClaims(beforeEpochMillis: Long): RepositoryResult<List<CommissionStaleClaim>> {
        return DatabaseGuard.execute("读取待人工核对的委托奖励", RepositoryResult.Failure) {
            RepositoryResult.Success(
                commissionDailyProgressRecordMapper.findAll {
                    CommissionDailyProgressColumns.CLAIM_STATE eq CommissionClaimState.CLAIMING.name
                    CommissionDailyProgressColumns.CLAIM_RESERVED_AT lt beforeEpochMillis
                }.map { record ->
                    CommissionStaleClaim(
                        playerId = record.playerId,
                        date = LocalDate.parse(record.commissionDate),
                        commissionId = record.commissionId,
                        operationId = record.claimOperationId.takeIf(String::isNotBlank)?.let { value ->
                            runCatching { UUID.fromString(value) }.getOrNull()
                        },
                        reservedAt = record.claimReservedAt.takeIf { it > 0L },
                    )
                }
            )
        }
    }

    private fun updateClaimState(
        action: String,
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        operationId: UUID,
        next: CommissionClaimState,
    ): RepositoryResult<Unit> {
        return DatabaseGuard.execute(action, RepositoryResult.Failure) {
            val changed = commissionDailyProgressRecordMapper.transaction {
                rawUpdate {
                    set(CommissionDailyProgressColumns.CLAIM_STATE, next.name)
                    if (next == CommissionClaimState.UNCLAIMED) {
                        set(CommissionDailyProgressColumns.CLAIM_OPERATION_ID, "")
                        set(CommissionDailyProgressColumns.CLAIM_RESERVED_AT, 0L)
                    }
                    where {
                        CommissionDailyProgressColumns.RECORD_KEY eq recordKey(playerId, date, commissionId)
                        CommissionDailyProgressColumns.CLAIM_STATE eq CommissionClaimState.CLAIMING.name
                        CommissionDailyProgressColumns.CLAIM_OPERATION_ID eq operationId.toString()
                    }
                }
            }.getOrThrow()
            if (changed == 1) RepositoryResult.Success(Unit) else RepositoryResult.Failure
        }
    }

    /**
     * 确保一条逻辑进度记录存在。
     *
     * [CommissionDailyProgressRecord.recordKey] 的唯一索引处理跨实例并发插入。重复键错误不会进入
     * [DatabaseGuard]：插入失败后改用新的自动提交查询读取已胜出的记录，从而避开 MySQL 事务快照。
     */
    private fun ensureRecord(
        playerId: UUID,
        date: LocalDate,
        commissionId: String,
        key: String,
    ) {
        if (findRecord(key) != null) {
            return
        }
        val candidate = CommissionDailyProgressRecord(
            recordKey = key,
            playerId = playerId,
            commissionDate = date.toString(),
            commissionId = commissionId,
            progress = 0,
            claimState = CommissionClaimState.UNCLAIMED.name,
            claimOperationId = "",
            claimReservedAt = 0L,
        )
        repeat(RECORD_INSERT_RETRY_LIMIT) { attempt ->
            try {
                commissionDailyProgressRecordMapper.insert(candidate)
                return
            } catch (insertError: Exception) {
                if (findRecord(key) != null) {
                    return
                }
                if (attempt == RECORD_INSERT_RETRY_LIMIT - 1) {
                    throw insertError
                }
                Thread.yield()
            }
        }
        error("委托进度记录创建流程意外结束: $key")
    }

    /** 通过稳定主键读取一条委托进度；该映射器刻意不启用 L2 缓存。 */
    private fun findRecord(key: String): CommissionDailyProgressRecord? =
        commissionDailyProgressRecordMapper.findById(key)

    private fun reservationFrom(record: CommissionDailyProgressRecord?): CommissionClaimReservation {
        return when (record?.claimState.toClaimState()) {
            CommissionClaimState.CLAIMED -> CommissionClaimReservation.AlreadyClaimed
            CommissionClaimState.CLAIMING -> CommissionClaimReservation.ClaimInProgress(
                operationId = record?.claimOperationId?.takeIf(String::isNotBlank)?.let { value ->
                    runCatching { UUID.fromString(value) }.getOrNull()
                },
                reservedAt = record?.claimReservedAt?.takeIf { it > 0L },
            )

            CommissionClaimState.UNCLAIMED,
            -> CommissionClaimReservation.NotCompleted
        }
    }

    private fun CommissionDailyProgressRecord.toDomain(): CommissionProgress {
        return CommissionProgress(
            playerId = playerId,
            date = LocalDate.parse(commissionDate),
            commissionId = commissionId,
            progress = progress.coerceAtLeast(0),
            claimState = claimState.toClaimState(),
        )
    }

    private fun String?.toClaimState(): CommissionClaimState {
        return CommissionClaimState.entries.firstOrNull { it.name == this } ?: CommissionClaimState.UNCLAIMED
    }

    private fun recordKey(playerId: UUID, date: LocalDate, commissionId: String): String {
        val source = "$playerId|$date|$commissionId".toByteArray(StandardCharsets.UTF_8)
        return UUID.nameUUIDFromBytes(source).toString()
    }

    private const val COMPARE_AND_SET_RETRY_LIMIT = 8
    private const val RECORD_INSERT_RETRY_LIMIT = 3
}
