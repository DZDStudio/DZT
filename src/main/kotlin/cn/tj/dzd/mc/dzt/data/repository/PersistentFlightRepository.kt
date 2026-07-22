package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.PlayerFlightSettingRecord
import cn.tj.dzd.mc.dzt.data.table.playerFlightSettingRecordMapper
import cn.tj.dzd.mc.dzt.flight.FlightRepository
import java.util.UUID

/**
 * 基于 TabooLib Persistent Container 的玩家飞行开关仓库。
 *
 * 该实现只访问飞行功能独立的 `player_flight_setting` 表。
 */
object PersistentFlightRepository : FlightRepository {

    override fun isEnabled(playerId: UUID): RepositoryResult<Boolean> {
        return DatabaseGuard.execute("读取玩家飞行开关", RepositoryResult.Failure) {
            RepositoryResult.Success(playerFlightSettingRecordMapper.findById(playerId)?.enabled ?: false)
        }
    }

    override fun setEnabled(playerId: UUID, enabled: Boolean): RepositoryResult<Unit> {
        return DatabaseGuard.execute("更新玩家飞行开关", RepositoryResult.Failure) {
            ensureRecord(playerId, enabled)
            playerFlightSettingRecordMapper.transaction {
                val record = findById(playerId)
                    ?: error("飞行设置记录创建后仍无法读取: $playerId")
                if (record.enabled != enabled) {
                    record.enabled = enabled
                    update(record)
                }
            }.getOrThrow()
            RepositoryResult.Success(Unit)
        }
    }

    /**
     * 确保玩家飞行设置记录存在。
     *
     * UUID 唯一索引负责仲裁共享 MySQL 的跨服首次插入。并发插入失败后使用新的自动提交
     * 查询读取胜出记录，只有持续无法创建或读取时才将异常交给 [DatabaseGuard]。
     */
    private fun ensureRecord(playerId: UUID, enabled: Boolean) {
        if (playerFlightSettingRecordMapper.findById(playerId) != null) {
            return
        }
        val candidate = PlayerFlightSettingRecord(playerId, enabled)
        repeat(RECORD_INSERT_RETRY_LIMIT) { attempt ->
            try {
                playerFlightSettingRecordMapper.insert(candidate)
                return
            } catch (insertError: Exception) {
                if (playerFlightSettingRecordMapper.findById(playerId) != null) {
                    return
                }
                if (attempt == RECORD_INSERT_RETRY_LIMIT - 1) {
                    throw insertError
                }
                Thread.yield()
            }
        }
        error("飞行设置记录创建流程意外结束: $playerId")
    }

    private const val RECORD_INSERT_RETRY_LIMIT = 3
}
