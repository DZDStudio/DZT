package cn.tj.dzd.mc.dzt.data.repository

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.table.preferredRouteRecordMapper
import cn.tj.dzd.mc.dzt.route.PreferredRouteRepository
import java.util.UUID

/**
 * 基于 TabooLib PersistentContainer 的优选线路资格仓库。
 */
object PersistentPreferredRouteRepository : PreferredRouteRepository {

    override fun contains(ownerId: UUID): RepositoryResult<Boolean> {
        return DatabaseGuard.execute("查询优选线路玩家", RepositoryResult.Failure) {
            RepositoryResult.Success(
                preferredRouteRecordMapper.findOne {
                    "uuid" eq ownerId.toString()
                } != null
            )
        }
    }
}
