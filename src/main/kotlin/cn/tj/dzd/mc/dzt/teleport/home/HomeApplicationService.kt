package cn.tj.dzd.mc.dzt.teleport.home

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.teleport.model.StoredLocation
import java.util.UUID

/**
 * Home 用例服务。
 *
 * 该类不依赖 Bukkit 和 TabooLib，可在单元测试中使用内存仓库验证业务规则。
 *
 * @param repository Home 持久化端口。
 * @param maximumHomes 单个玩家允许保存的最大 Home 数量。
 */
class HomeApplicationService(
    private val repository: HomeRepository,
    private val maximumHomes: Int = DEFAULT_MAXIMUM_HOMES,
) {

    private val ownerLocks = Array(LOCK_STRIPES) { Any() }

    init {
        require(maximumHomes > 0) { "Home 数量上限必须大于 0" }
    }

    /**
     * 创建 Home。
     *
     * @param ownerId 玩家 UUID。
     * @param name Home 名称，会自动去除首尾空白。
     * @param location 保存的坐标快照。
     * @param iconIndex 图标持久化索引。
     * @return 明确区分校验、冲突和基础设施失败的结果。
     */
    fun createHome(
        ownerId: UUID,
        name: String,
        location: StoredLocation,
        iconIndex: Long,
    ): HomeCreateResult = synchronized(lockFor(ownerId)) {
        val normalizedName = normalizeName(name)
            ?: return@synchronized HomeCreateResult.InvalidName(
                if (name.trim().isEmpty()) "家名称不能为空" else "家名称长度不能超过 $MAX_NAME_LENGTH 个字符"
            )
        if (location.world.isBlank()) {
            return@synchronized HomeCreateResult.InvalidWorld("家坐标缺少世界")
        }
        if (location.world.length > MAX_WORLD_NAME_LENGTH) {
            return@synchronized HomeCreateResult.InvalidWorld(
                "世界名称长度不能超过 $MAX_WORLD_NAME_LENGTH 个字符"
            )
        }

        val existing = when (val result = repository.findAll(ownerId)) {
            is RepositoryResult.Success -> result.value
            RepositoryResult.Failure -> return@synchronized HomeCreateResult.InfrastructureFailure
        }
        if (existing.any { it.name == normalizedName }) {
            return@synchronized HomeCreateResult.DuplicateName(normalizedName)
        }
        if (existing.size >= maximumHomes) {
            return@synchronized HomeCreateResult.LimitReached(maximumHomes)
        }

        val home = HomeEntry(normalizedName, iconIndex, location)
        when (repository.insert(ownerId, home)) {
            is RepositoryResult.Success -> HomeCreateResult.Success(home)
            RepositoryResult.Failure -> HomeCreateResult.InfrastructureFailure
        }
    }

    /**
     * 读取玩家的 Home 列表。
     *
     * @param ownerId 玩家 UUID。
     * @return 查询结果。
     */
    fun listHomes(ownerId: UUID): HomeQueryResult {
        return when (val result = repository.findAll(ownerId)) {
            is RepositoryResult.Success -> HomeQueryResult.Success(result.value)
            RepositoryResult.Failure -> HomeQueryResult.InfrastructureFailure
        }
    }

    /**
     * 删除玩家的 Home。
     *
     * @param ownerId 玩家 UUID。
     * @param name Home 名称，会自动去除首尾空白。
     * @return 删除结果。为兼容旧行为，记录不存在也视为成功。
     */
    fun deleteHome(ownerId: UUID, name: String): HomeDeleteResult {
        val normalizedName = normalizeName(name)
            ?: return HomeDeleteResult.InvalidName(
                if (name.trim().isEmpty()) "家名称不能为空" else "家名称长度不能超过 $MAX_NAME_LENGTH 个字符"
            )
        return when (repository.delete(ownerId, normalizedName)) {
            is RepositoryResult.Success -> HomeDeleteResult.Success
            RepositoryResult.Failure -> HomeDeleteResult.InfrastructureFailure
        }
    }

    private fun normalizeName(name: String): String? {
        return name.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
    }

    private fun lockFor(ownerId: UUID): Any {
        return ownerLocks[(ownerId.hashCode() and Int.MAX_VALUE) % ownerLocks.size]
    }

    companion object {
        /** 默认的单玩家 Home 数量上限。 */
        const val DEFAULT_MAXIMUM_HOMES = 64

        /** Home 名称最大长度。 */
        const val MAX_NAME_LENGTH = 32

        /** 世界名称最大长度，与现有数据库字段保持一致。 */
        const val MAX_WORLD_NAME_LENGTH = 32

        private const val LOCK_STRIPES = 64
    }
}
