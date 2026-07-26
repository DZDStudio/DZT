package cn.tj.dzd.mc.dzt.ban

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID

/**
 * 玩家封禁历史的持久化端口。
 *
 * 所有方法都会访问数据库，调用方必须在 DZT 异步执行器或 Paper 异步预登录线程中调用，不能阻塞 Folia 实体线程。
 */
interface BanRepository {

    /**
     * 新增一条封禁记录，并提前解除该玩家同类型且尚未到期的旧封禁。
     *
     * 旧记录不会删除，其计划解封时间保持不变，只会标记为已提前解除。
     *
     * @param record 要新增的封禁记录。
     * @return 持久化操作结果。
     */
    fun createReplacingActive(record: PlayerBan): RepositoryResult<Unit>

    /**
     * 提前解除玩家指定类型的当前有效封禁。
     *
     * @param playerId 被解除玩家 UUID。
     * @param type 需要解除的封禁类型。
     * @param releasedAt 提前解除的 Unix 毫秒时间戳。
     * @return 成功值为 true 表示至少解除了一条有效封禁；false 表示当前无有效封禁。
     */
    fun releaseActive(playerId: UUID, type: BanType, releasedAt: Long): RepositoryResult<Boolean>

    /**
     * 读取玩家指定类型在指定时间仍有效的最新一条封禁记录。
     *
     * @param playerId 玩家 UUID。
     * @param type 需要读取的封禁类型。
     * @param currentTimeMillis 当前 Unix 毫秒时间戳。
     * @return 成功值为 null 表示当前未被封禁。
     */
    fun findActive(playerId: UUID, type: BanType, currentTimeMillis: Long): RepositoryResult<PlayerBan?>

    /**
     * 读取玩家的全部封禁历史。
     *
     * @param playerId 玩家 UUID。
     * @return 持久化操作结果，排序由领域层统一处理。
     */
    fun findHistory(playerId: UUID): RepositoryResult<List<PlayerBan>>
}
