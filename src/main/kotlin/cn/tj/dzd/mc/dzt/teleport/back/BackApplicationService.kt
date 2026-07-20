package cn.tj.dzd.mc.dzt.teleport.back

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID

/**
 * 死亡返回点用例服务。
 *
 * @param repository 返回点持久化端口。
 * @param maximumBacks 每个玩家保留的最大记录数。
 */
class BackApplicationService(
    private val repository: BackRepository,
    private val maximumBacks: Int = DEFAULT_MAXIMUM_BACKS,
) {

    private val ownerLocks = Array(LOCK_STRIPES) { Any() }

    init {
        require(maximumBacks > 0) { "返回点数量上限必须大于 0" }
    }

    /**
     * 记录死亡位置，并删除超出上限的最旧记录。
     *
     * @param ownerId 玩家 UUID。
     * @param time 创建时间戳。
     * @param location 死亡位置快照。
     * @return 修改结果。
     */
    fun recordBack(ownerId: UUID, time: Long, location: StoredLocation): BackMutationResult =
        synchronized(lockFor(ownerId)) {
            val entry = BackEntry(time, location)
            if (repository.insert(ownerId, entry) == RepositoryResult.Failure) {
                return@synchronized BackMutationResult.INFRASTRUCTURE_FAILURE
            }

            val existing = when (val result = repository.findAll(ownerId)) {
                is RepositoryResult.Success -> result.value.sortedByDescending(BackEntry::time)
                RepositoryResult.Failure -> return@synchronized BackMutationResult.INFRASTRUCTURE_FAILURE
            }
            for (expired in existing.drop(maximumBacks)) {
                if (repository.delete(ownerId, expired.time) == RepositoryResult.Failure) {
                    return@synchronized BackMutationResult.INFRASTRUCTURE_FAILURE
                }
            }
            BackMutationResult.SUCCESS
        }

    /**
     * 读取玩家的返回点，结果按时间倒序排列。
     *
     * @param ownerId 玩家 UUID。
     * @return 查询结果。
     */
    fun listBacks(ownerId: UUID): BackQueryResult {
        return when (val result = repository.findAll(ownerId)) {
            is RepositoryResult.Success -> BackQueryResult.Success(result.value.sortedByDescending(BackEntry::time))
            RepositoryResult.Failure -> BackQueryResult.InfrastructureFailure
        }
    }

    /**
     * 删除指定时间戳的返回点。
     *
     * @param ownerId 玩家 UUID。
     * @param time 返回点时间戳。
     * @return 修改结果；记录不存在也视为成功。
     */
    fun deleteBack(ownerId: UUID, time: Long): BackMutationResult {
        return when (repository.delete(ownerId, time)) {
            is RepositoryResult.Success -> BackMutationResult.SUCCESS
            RepositoryResult.Failure -> BackMutationResult.INFRASTRUCTURE_FAILURE
        }
    }

    private fun lockFor(ownerId: UUID): Any {
        return ownerLocks[(ownerId.hashCode() and Int.MAX_VALUE) % ownerLocks.size]
    }

    companion object {
        /** 默认的单玩家返回点保留上限。 */
        const val DEFAULT_MAXIMUM_BACKS = 32

        private const val LOCK_STRIPES = 64
    }
}
