package cn.tj.dzd.mc.dzt.flight

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 为异步飞行设置读写提供顺序令牌，并记录扣费失败期间的强制关闭状态。
 *
 * 数据库操作按 UUID 串行执行，但其 Future 回调仍可能以不同顺序被调度。读令牌防止旧读取
 * 覆盖新操作，写令牌防止旧写入覆盖更新的写入；强制关闭令牌则确保旧扣费失败回调不能
 * 清除后来产生的新关闭状态。
 */
internal class FlightSettingStateGuard {

    private val tokenSequence = AtomicLong()
    private val latestOperations = ConcurrentHashMap<UUID, Long>()
    private val latestMutations = ConcurrentHashMap<UUID, Long>()
    private val forcedDisableTokens = ConcurrentHashMap<UUID, Long>()

    /**
     * 标记一个已经开始执行的串行数据库读取。
     *
     * @param playerId 玩家 UUID。
     * @return 本次读取的顺序令牌。
     */
    fun beginRead(playerId: UUID): Long {
        val token = tokenSequence.incrementAndGet()
        latestOperations[playerId] = token
        return token
    }

    /**
     * 标记一个已经开始执行的串行数据库写入。
     *
     * @param playerId 玩家 UUID。
     * @return 本次写入的顺序令牌。
     */
    fun beginMutation(playerId: UUID): Long {
        val token = tokenSequence.incrementAndGet()
        latestOperations[playerId] = token
        latestMutations[playerId] = token
        return token
    }

    /** 判断读取结果是否仍是该玩家最新开始的设置操作。 */
    fun isCurrentRead(playerId: UUID, token: Long): Boolean = latestOperations[playerId] == token

    /** 判断写入结果是否仍是该玩家最新开始的写入操作。 */
    fun isCurrentMutation(playerId: UUID, token: Long): Boolean = latestMutations[playerId] == token

    /**
     * 立即标记玩家飞行必须保持关闭。
     *
     * @param playerId 玩家 UUID。
     * @return 本次强制关闭的令牌，只能由持有该令牌的完成回调清除。
     */
    fun forceDisable(playerId: UUID): Long {
        val token = tokenSequence.incrementAndGet()
        forcedDisableTokens[playerId] = token
        return token
    }

    /** 判断玩家当前是否处于强制关闭状态。 */
    fun isForcedDisabled(playerId: UUID): Boolean = forcedDisableTokens.containsKey(playerId)

    /** 返回玩家当前的强制关闭令牌；未强制关闭时返回 `null`。 */
    fun forcedDisableToken(playerId: UUID): Long? = forcedDisableTokens[playerId]

    /**
     * 仅在令牌仍匹配时清除强制关闭状态。
     *
     * @return 成功清除时返回 `true`。
     */
    fun clearForcedDisable(playerId: UUID, token: Long): Boolean {
        return forcedDisableTokens.remove(playerId, token)
    }

    /** 清除全部运行时令牌。 */
    fun clear() {
        latestOperations.clear()
        latestMutations.clear()
        forcedDisableTokens.clear()
    }
}
