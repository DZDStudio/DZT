package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.table.PlayerTitleRecord
import cn.tj.dzd.mc.dzt.data.table.playerTitleRecordMapper
import cn.tj.dzd.mc.dzt.title.PlayerTitle
import cn.tj.dzd.mc.dzt.title.TitleEquipDecision
import cn.tj.dzd.mc.dzt.title.TitleRepository
import java.util.UUID

/**
 * 基于 TabooLib PersistentContainer 的称号仓库。
 */
object PersistentTitleRepository : TitleRepository {

    override fun insertIfAbsent(ownerId: UUID, title: PlayerTitle): RepositoryResult<Boolean> {
        return DatabaseGuard.execute("授予称号", RepositoryResult.Failure) {
            val inserted = playerTitleRecordMapper.transaction {
                val id = recordId(ownerId, title.id)
                if (findById(id) != null) {
                    return@transaction false
                }
                insert(title.toRecord(ownerId))
                true
            }.getOrThrow()
            RepositoryResult.Success(inserted)
        }
    }

    override fun delete(ownerId: UUID, titleId: String): RepositoryResult<Boolean> {
        return DatabaseGuard.execute("移除玩家称号", RepositoryResult.Failure) {
            val deleted = playerTitleRecordMapper.transaction {
                val id = recordId(ownerId, titleId)
                findById(id) ?: return@transaction false
                deleteById(id)
                true
            }.getOrThrow()
            RepositoryResult.Success(deleted)
        }
    }

    override fun findAll(ownerId: UUID): RepositoryResult<List<PlayerTitle>> {
        return DatabaseGuard.execute("读取玩家称号", RepositoryResult.Failure) {
            RepositoryResult.Success(
                playerTitleRecordMapper.findAll {
                    "uuid" eq ownerId.toString()
                }.map { it.toDomain() }
            )
        }
    }

    override fun findEquipped(ownerId: UUID): RepositoryResult<PlayerTitle?> {
        return DatabaseGuard.execute("读取已佩戴称号", RepositoryResult.Failure) {
            RepositoryResult.Success(
                playerTitleRecordMapper.findOne {
                    "uuid" eq ownerId.toString()
                    "equipped" eq true
                }?.toDomain()
            )
        }
    }

    override fun equip(ownerId: UUID, titleId: String): RepositoryResult<TitleEquipDecision> {
        return DatabaseGuard.execute("佩戴称号", RepositoryResult.Failure) {
            val decision = playerTitleRecordMapper.transaction {
                val selected = findById(recordId(ownerId, titleId))
                    ?: return@transaction TitleEquipDecision.NOT_OWNED
                if (selected.equipped) {
                    return@transaction TitleEquipDecision.ALREADY_EQUIPPED
                }
                rawUpdate {
                    set("equipped", false)
                    where {
                        "uuid" eq ownerId.toString()
                    }
                }
                selected.equipped = true
                update(selected)
                TitleEquipDecision.EQUIPPED
            }.getOrThrow()
            RepositoryResult.Success(decision)
        }
    }

    override fun unequip(ownerId: UUID): RepositoryResult<Unit> {
        return DatabaseGuard.execute("取消佩戴称号", RepositoryResult.Failure) {
            playerTitleRecordMapper.transaction {
                rawUpdate {
                    set("equipped", false)
                    where {
                        "uuid" eq ownerId.toString()
                    }
                }
            }.getOrThrow()
            RepositoryResult.Success(Unit)
        }
    }

    private fun recordId(ownerId: UUID, titleId: String): String = "$ownerId:$titleId"

    private fun PlayerTitle.toRecord(ownerId: UUID): PlayerTitleRecord {
        return PlayerTitleRecord(
            recordId = recordId(ownerId, id),
            uuid = ownerId,
            titleId = id,
            displayName = displayName,
            description = description,
            acquiredAt = acquiredAt,
            equipped = equipped,
        )
    }

    private fun PlayerTitleRecord.toDomain(): PlayerTitle {
        return PlayerTitle(titleId, displayName, description, acquiredAt, equipped)
    }
}
