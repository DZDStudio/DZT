package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.ban.BanRepository
import cn.tj.dzd.mc.dzt.ban.PlayerBan
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.PlayerBanColumns
import cn.tj.dzd.mc.dzt.data.table.PlayerBanRecord
import cn.tj.dzd.mc.dzt.data.table.playerBanRecordMapper
import java.util.UUID

/**
 * 基于 TabooLib Persistent Container 的玩家封禁仓库。
 *
 * 此实现只访问新增的 `player_ban_record` 表。封禁和提前解封均只更新状态或插入新记录，不会删除历史数据。
 */
object PersistentBanRepository : BanRepository {

    override fun createReplacingActive(record: PlayerBan): RepositoryResult<Unit> {
        return DatabaseGuard.execute("封禁玩家", RepositoryResult.Failure) {
            playerBanRecordMapper.transaction {
                rawUpdate {
                    set(PlayerBanColumns.ACTIVE, false)
                    set(PlayerBanColumns.RELEASED_AT, record.bannedAt)
                    where {
                        PlayerBanColumns.PLAYER_ID eq record.playerId.toString()
                        PlayerBanColumns.ACTIVE eq true
                        PlayerBanColumns.UNBAN_AT gt record.bannedAt
                    }
                }
                insert(record.toRecord())
            }.getOrThrow()
            RepositoryResult.Success(Unit)
        }
    }

    override fun releaseActive(playerId: UUID, releasedAt: Long): RepositoryResult<Boolean> {
        return DatabaseGuard.execute("解除玩家封禁", RepositoryResult.Failure) {
            val changed = playerBanRecordMapper.rawUpdate {
                set(PlayerBanColumns.ACTIVE, false)
                set(PlayerBanColumns.RELEASED_AT, releasedAt)
                where {
                    PlayerBanColumns.PLAYER_ID eq playerId.toString()
                    PlayerBanColumns.ACTIVE eq true
                    PlayerBanColumns.UNBAN_AT gt releasedAt
                }
            }
            RepositoryResult.Success(changed > 0)
        }
    }

    override fun findActive(playerId: UUID, currentTimeMillis: Long): RepositoryResult<PlayerBan?> {
        return DatabaseGuard.execute("读取玩家封禁状态", RepositoryResult.Failure) {
            val active = findRecords(playerId)
                .filter { it.isEffectiveAt(currentTimeMillis) }
                .maxWithOrNull(compareBy<PlayerBan>(PlayerBan::bannedAt).thenBy { it.recordId.toString() })
            RepositoryResult.Success(active)
        }
    }

    override fun findHistory(playerId: UUID): RepositoryResult<List<PlayerBan>> {
        return DatabaseGuard.execute("读取玩家封禁历史", RepositoryResult.Failure) {
            RepositoryResult.Success(findRecords(playerId))
        }
    }

    private fun findRecords(playerId: UUID): List<PlayerBan> {
        return playerBanRecordMapper.findAll {
            PlayerBanColumns.PLAYER_ID eq playerId.toString()
        }.map { record -> record.toDomain() }
    }

    private fun PlayerBan.toRecord(): PlayerBanRecord {
        return PlayerBanRecord(
            recordId = recordId,
            playerId = playerId,
            active = active,
            bannedAt = bannedAt,
            unbanAt = unbanAt,
            reason = reason,
            releasedAt = releasedAt,
        )
    }

    private fun PlayerBanRecord.toDomain(): PlayerBan {
        return PlayerBan(
            recordId = recordId,
            playerId = playerId,
            bannedAt = bannedAt,
            unbanAt = unbanAt,
            reason = reason,
            active = active,
            releasedAt = releasedAt,
        )
    }
}
