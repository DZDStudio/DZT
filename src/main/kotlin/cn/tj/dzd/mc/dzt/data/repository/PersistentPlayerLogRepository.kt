package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.table.PlayerLogRecord
import cn.tj.dzd.mc.dzt.data.table.playerLogMapper
import cn.tj.dzd.mc.dzt.log.PlayerLogRepository
import java.sql.Timestamp

/**
 * 基于 TabooLib PersistentContainer 的玩家行为日志仓库。
 */
object PersistentPlayerLogRepository : PlayerLogRepository {

    override fun append(type: String, message: String): RepositoryResult<Unit> {
        return DatabaseGuard.execute("新增玩家日志", RepositoryResult.Failure) {
            playerLogMapper.insert(PlayerLogRecord(type, message))
            RepositoryResult.Success(Unit)
        }
    }

    override fun deleteBefore(cutoff: Timestamp): RepositoryResult<Int> {
        return DatabaseGuard.execute("清理玩家日志", RepositoryResult.Failure) {
            val deleted = playerLogMapper.rawDelete {
                // ActionDelete 必须通过 where 挂载过滤条件，否则会生成无条件 DELETE。
                where {
                    "time" lt cutoff
                }
            }
            RepositoryResult.Success(deleted)
        }
    }
}
