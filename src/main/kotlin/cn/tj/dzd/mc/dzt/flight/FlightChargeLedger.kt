package cn.tj.dzd.mc.dzt.flight

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 记录周期检查已经确认、但尚未完成扣款的飞行次数。
 *
 * 每次入账代表一次 100-tick 检查。ServiceIO 响应较慢时，后续检查只增加计数，
 * 不会并发提交账户操作，也不会丢弃本应扣除的次数。
 */
internal class FlightChargeLedger {

    private val pending = ConcurrentHashMap<UUID, Int>()

    /**
     * 为玩家增加一次待扣款检查。
     *
     * @param playerId 玩家 UUID。
     */
    fun enqueue(playerId: UUID) {
        pending.compute(playerId) { _, count ->
            if (count == Int.MAX_VALUE) Int.MAX_VALUE else (count ?: 0) + 1
        }
    }

    /**
     * 取出玩家的一次待扣款检查。
     *
     * @param playerId 玩家 UUID。
     * @return 有待扣次数并成功取出时返回 `true`。
     */
    fun poll(playerId: UUID): Boolean {
        var polled = false
        pending.compute(playerId) { _, count ->
            if (count == null || count <= 0) {
                null
            } else {
                polled = true
                (count - 1).takeIf { it > 0 }
            }
        }
        return polled
    }

    /**
     * 检查玩家是否仍有待扣次数。
     *
     * @param playerId 玩家 UUID。
     * @return 至少有一次待扣款检查时返回 `true`。
     */
    fun hasPending(playerId: UUID): Boolean = (pending[playerId] ?: 0) > 0

    /**
     * 清除玩家全部待扣次数。
     *
     * @param playerId 玩家 UUID。
     */
    fun clear(playerId: UUID) {
        pending.remove(playerId)
    }

    /** 清除全部玩家的待扣次数。 */
    fun clear() {
        pending.clear()
    }
}
