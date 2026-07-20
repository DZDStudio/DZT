package cn.tj.dzd.mc.dzt.teleport.home

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID

/**
 * Home 业务模型。
 *
 * @property name 已标准化的传送点名称。
 * @property iconIndex [cn.tj.dzd.mc.dzt.util.Icon] 的持久化索引。
 * @property location 不依赖 Bukkit 的坐标快照。
 */
data class HomeEntry(
    val name: String,
    val iconIndex: Long,
    val location: StoredLocation,
)

/**
 * Home 持久化端口。
 *
 * 实现可以使用 TabooLib PersistentContainer，但不得向业务层暴露 mapper 或 Bukkit 类型。
 */
interface HomeRepository {

    /**
     * 读取玩家的全部 Home。
     *
     * @param ownerId 玩家 UUID。
     * @return 存储操作结果。
     */
    fun findAll(ownerId: UUID): RepositoryResult<List<HomeEntry>>

    /**
     * 写入一个 Home。
     *
     * @param ownerId 玩家 UUID。
     * @param home 已通过业务校验的 Home。
     * @return 存储操作结果。
     */
    fun insert(ownerId: UUID, home: HomeEntry): RepositoryResult<Unit>

    /**
     * 删除玩家的指定 Home。
     *
     * @param ownerId 玩家 UUID。
     * @param name 已标准化的 Home 名称。
     * @return 存储操作结果。
     */
    fun delete(ownerId: UUID, name: String): RepositoryResult<Unit>
}

/** Home 列表查询结果。 */
sealed interface HomeQueryResult {
    data class Success(val homes: List<HomeEntry>) : HomeQueryResult
    data object InfrastructureFailure : HomeQueryResult
}

/** Home 创建结果。 */
sealed interface HomeCreateResult {
    data class Success(val home: HomeEntry) : HomeCreateResult
    data class InvalidName(val message: String) : HomeCreateResult
    data class DuplicateName(val name: String) : HomeCreateResult
    data class LimitReached(val maximum: Int) : HomeCreateResult
    data class InvalidWorld(val message: String) : HomeCreateResult
    data object InfrastructureFailure : HomeCreateResult
}

/** Home 删除结果。 */
sealed interface HomeDeleteResult {
    data object Success : HomeDeleteResult
    data class InvalidName(val message: String) : HomeDeleteResult
    data object InfrastructureFailure : HomeDeleteResult
}
