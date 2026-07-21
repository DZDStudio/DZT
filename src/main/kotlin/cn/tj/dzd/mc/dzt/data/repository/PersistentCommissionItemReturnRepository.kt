package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.commission.CommissionItemReturnRepository
import cn.tj.dzd.mc.dzt.commission.CommissionPendingItemReturn
import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.CommissionPendingItemReturnColumns
import cn.tj.dzd.mc.dzt.data.table.CommissionPendingItemReturnRecord
import cn.tj.dzd.mc.dzt.data.table.commissionPendingItemReturnRecordMapper
import java.util.UUID

/**
 * 基于 TabooLib Persistent Container 的委托物品返还队列。
 *
 * 此实现只访问新增的 `commission_pending_item_return` 表。记录会在实际放回玩家背包后删除；若服务器在
 * 放回与删除之间中断，下一次登录可能重复返还，但不会无声吞掉已扣除的物品。
 */
object PersistentCommissionItemReturnRepository : CommissionItemReturnRepository {

    override fun enqueue(record: CommissionPendingItemReturn): RepositoryResult<Unit> {
        return DatabaseGuard.execute("登记委托待返还物品", RepositoryResult.Failure) {
            commissionPendingItemReturnRecordMapper.insert(record.toRecord())
            RepositoryResult.Success(Unit)
        }
    }

    override fun findAll(playerId: UUID): RepositoryResult<List<CommissionPendingItemReturn>> {
        return DatabaseGuard.execute("读取委托待返还物品", RepositoryResult.Failure) {
            RepositoryResult.Success(
                commissionPendingItemReturnRecordMapper.findAll {
                    CommissionPendingItemReturnColumns.PLAYER_ID eq playerId.toString()
                }.map { record -> record.toDomain() }
            )
        }
    }

    override fun replacePayload(returnId: UUID, payload: ByteArray): RepositoryResult<Unit> {
        return DatabaseGuard.execute("更新委托待返还物品", RepositoryResult.Failure) {
            val updated = commissionPendingItemReturnRecordMapper.rawUpdate {
                set("item_payload", payload)
                where {
                    CommissionPendingItemReturnColumns.RETURN_ID eq returnId.toString()
                }
            }
            if (updated == 1) RepositoryResult.Success(Unit) else RepositoryResult.Failure
        }
    }

    override fun delete(returnId: UUID): RepositoryResult<Unit> {
        return DatabaseGuard.execute("删除已返还委托物品", RepositoryResult.Failure) {
            val deleted = commissionPendingItemReturnRecordMapper.rawDelete {
                where {
                    CommissionPendingItemReturnColumns.RETURN_ID eq returnId.toString()
                }
            }
            if (deleted == 1) RepositoryResult.Success(Unit) else RepositoryResult.Failure
        }
    }

    private fun CommissionPendingItemReturn.toRecord(): CommissionPendingItemReturnRecord {
        return CommissionPendingItemReturnRecord(
            returnId = returnId,
            playerId = playerId,
            commissionId = commissionId,
            itemPayload = itemPayload,
            createdAt = createdAt,
        )
    }

    private fun CommissionPendingItemReturnRecord.toDomain(): CommissionPendingItemReturn {
        return CommissionPendingItemReturn(
            returnId = returnId,
            playerId = playerId,
            commissionId = commissionId,
            itemPayload = itemPayload,
            createdAt = createdAt,
        )
    }
}
