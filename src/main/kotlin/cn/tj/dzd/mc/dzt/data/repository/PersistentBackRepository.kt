package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.table.BackRecord
import cn.tj.dzd.mc.dzt.data.table.backRecordMapper
import cn.tj.dzd.mc.dzt.teleport.back.BackEntry
import cn.tj.dzd.mc.dzt.teleport.back.BackRepository
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID

/**
 * 基于 TabooLib PersistentContainer 的死亡返回点仓库。
 */
object PersistentBackRepository : BackRepository {

    override fun findAll(ownerId: UUID): RepositoryResult<List<BackEntry>> {
        return DatabaseGuard.execute("获取死亡返回点列表", RepositoryResult.Failure) {
            RepositoryResult.Success(
                backRecordMapper.findAll {
                    "uuid" eq ownerId.toString()
                }.map { it.toDomain() }
            )
        }
    }

    override fun insert(ownerId: UUID, back: BackEntry): RepositoryResult<Unit> {
        return DatabaseGuard.execute("新增死亡返回点", RepositoryResult.Failure) {
            backRecordMapper.insert(back.toRecord(ownerId))
            RepositoryResult.Success(Unit)
        }
    }

    override fun delete(ownerId: UUID, time: Long): RepositoryResult<Unit> {
        return DatabaseGuard.execute("删除死亡返回点", RepositoryResult.Failure) {
            backRecordMapper.deleteWhere {
                "uuid" eq ownerId.toString()
                "time" eq time
            }
            RepositoryResult.Success(Unit)
        }
    }

    private fun BackRecord.toDomain(): BackEntry {
        return BackEntry(time, StoredLocation(world, x, y, z))
    }

    private fun BackEntry.toRecord(ownerId: UUID): BackRecord {
        return BackRecord(ownerId, time, location.world, location.x, location.y, location.z)
    }
}
