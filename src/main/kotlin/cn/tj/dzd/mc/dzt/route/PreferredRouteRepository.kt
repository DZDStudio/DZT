package cn.tj.dzd.mc.dzt.route

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID

/**
 * 优选线路资格持久化端口。
 */
interface PreferredRouteRepository {

    /**
     * 检查玩家是否拥有优选线路资格。
     *
     * @param ownerId 玩家 UUID。
     * @return 存储操作结果。
     */
    fun contains(ownerId: UUID): RepositoryResult<Boolean>
}

/**
 * 优选线路资格查询用例。
 *
 * @param repository 优选线路资格持久化端口。
 */
class PreferredRouteApplicationService(
    private val repository: PreferredRouteRepository,
) {

    /**
     * 检查玩家是否拥有优选线路资格。
     *
     * @param ownerId 玩家 UUID。
     * @return 有资格时返回 true；无资格或数据库失败时返回 false。
     */
    fun isPreferred(ownerId: UUID): Boolean {
        return when (val result = repository.contains(ownerId)) {
            is RepositoryResult.Success -> result.value
            RepositoryResult.Failure -> false
        }
    }
}
