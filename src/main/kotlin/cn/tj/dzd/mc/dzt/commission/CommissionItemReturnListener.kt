package cn.tj.dzd.mc.dzt.commission

import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent

/**
 * 在玩家登录时恢复此前未能即时返还的委托物品。
 *
 * PlayerJoinEvent 是将离线玩家重新关联到强类型 Player 的精确 Bukkit 生命周期事件；TabooLib 没有等价事件
 * 能保留该语义。实际背包操作由 [CommissionItemReturnService] 继续调度到玩家所属 Folia 实体线程。
 */
object CommissionItemReturnListener {

    /**
     * 读取并投递登录玩家的待返还物品。
     *
     * @param event 玩家登录事件。
     */
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        CommissionItemReturnService.deliverPending(event.player)
    }
}
