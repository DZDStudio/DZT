package cn.tj.dzd.mc.dzt.flight

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID

/**
 * 玩家飞行开关的持久化端口。
 *
 * 所有方法都会访问持久化存储，调用方应在 DZT 异步执行器上调用，
 * 不得阻塞玩家所在的 Folia 实体线程。
 */
interface FlightRepository {

    /**
     * 读取玩家持久化的飞行开关。
     *
     * 当玩家尚无持久化记录时，成功结果为 `false`。
     *
     * @param playerId 玩家 UUID。
     * @return 读取成功时包含当前开关；持久化层失败时返回 [RepositoryResult.Failure]。
     */
    fun isEnabled(playerId: UUID): RepositoryResult<Boolean>

    /**
     * 持久化玩家的飞行开关。
     *
     * 实现必须保证同一玩家只保留一条设置记录。
     *
     * @param playerId 玩家 UUID。
     * @param enabled 是否开启飞行功能。
     * @return 写入成功时返回包含 [Unit] 的成功结果；持久化层失败时返回 [RepositoryResult.Failure]。
     */
    fun setEnabled(playerId: UUID, enabled: Boolean): RepositoryResult<Unit>
}
