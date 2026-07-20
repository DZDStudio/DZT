package cn.tj.dzd.mc.dzt.teleport.back

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID

/**
 * 死亡返回点业务模型。
 *
 * @property time 创建时的 Unix 毫秒时间戳。
 * @property location 死亡位置快照。
 */
data class BackEntry(
    val time: Long,
    val location: StoredLocation,
)

/**
 * 死亡返回点持久化端口。
 */
interface BackRepository {

    /**
     * 读取玩家的全部返回点。
     *
     * @param ownerId 玩家 UUID。
     * @return 存储操作结果。
     */
    fun findAll(ownerId: UUID): RepositoryResult<List<BackEntry>>

    /**
     * 写入一个返回点。
     *
     * @param ownerId 玩家 UUID。
     * @param back 返回点快照。
     * @return 存储操作结果。
     */
    fun insert(ownerId: UUID, back: BackEntry): RepositoryResult<Unit>

    /**
     * 按时间戳删除返回点。
     *
     * @param ownerId 玩家 UUID。
     * @param time 返回点时间戳。
     * @return 存储操作结果。
     */
    fun delete(ownerId: UUID, time: Long): RepositoryResult<Unit>
}

/** 返回点查询结果。 */
sealed interface BackQueryResult {
    data class Success(val backs: List<BackEntry>) : BackQueryResult
    data object InfrastructureFailure : BackQueryResult
}

/** 返回点修改结果。 */
enum class BackMutationResult {
    SUCCESS,
    INFRASTRUCTURE_FAILURE,
}
