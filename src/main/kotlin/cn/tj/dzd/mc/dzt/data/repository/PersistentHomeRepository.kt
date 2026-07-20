package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.table.HomeRecord
import cn.tj.dzd.mc.dzt.data.table.homeRecordMapper
import cn.tj.dzd.mc.dzt.teleport.home.HomeEntry
import cn.tj.dzd.mc.dzt.teleport.home.HomeRepository
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import cn.tj.dzd.mc.dzt.util.Icon
import java.util.UUID

/**
 * 基于 TabooLib PersistentContainer 的 Home 仓库。
 *
 * 该实现保持现有表结构与 [HomeRecord] 映射不变。
 */
object PersistentHomeRepository : HomeRepository {

    override fun findAll(ownerId: UUID): RepositoryResult<List<HomeEntry>> {
        return DatabaseGuard.execute(
            "获取家列表",
            RepositoryResult.Failure,
        ) {
            RepositoryResult.Success(
                homeRecordMapper.findAll {
                    "uuid" eq ownerId.toString()
                }.map { it.toDomain() }
            )
        }
    }

    override fun insert(ownerId: UUID, home: HomeEntry): RepositoryResult<Unit> {
        return DatabaseGuard.execute(
            "新增家",
            RepositoryResult.Failure,
        ) {
            homeRecordMapper.insert(home.toRecord(ownerId))
            RepositoryResult.Success(Unit)
        }
    }

    override fun delete(ownerId: UUID, name: String): RepositoryResult<Unit> {
        return DatabaseGuard.execute(
            "删除家",
            RepositoryResult.Failure,
        ) {
            homeRecordMapper.deleteWhere {
                "uuid" eq ownerId.toString()
                "name" eq name
            }
            RepositoryResult.Success(Unit)
        }
    }

    private fun HomeRecord.toDomain(): HomeEntry {
        return HomeEntry(
            name = name,
            iconIndex = icon.index,
            location = StoredLocation(world, x, y, z),
        )
    }

    private fun HomeEntry.toRecord(ownerId: UUID): HomeRecord {
        val icon = requireNotNull(Icon.entries.firstOrNull { it.index == iconIndex }) {
            "无法解析 Home 图标索引: $iconIndex"
        }
        return HomeRecord(
            uuid = ownerId,
            name = name,
            icon = icon,
            world = location.world,
            x = location.x,
            y = location.y,
            z = location.z,
        )
    }
}
