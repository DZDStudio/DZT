package cn.tj.dzd.mc.dzt.commission

import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import org.bukkit.event.entity.EntityDeathEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

/**
 * 将精确实体死亡事件转换为委托击杀进度。
 *
 * EntityDeathEvent 提供原版最终击杀者归因，并且 PlayerDeathEvent 也是其子类；TabooLib 没有等价的跨端
 * 事件可同时保留这两个语义，因此在此处保留 Paper/Bukkit 事件边界。事件线程只采样 UUID 和实体 ID，
 * 后续数据库工作由 [CommissionService] 异步执行。
 */
object CommissionProgressListener {

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val killerId = killer.uniqueId
        val entityId = event.entity.type.key.toString()

        CommissionService.recordKill(killerId, entityId).whenComplete { updates, error ->
            if (error != null || updates == null) {
                return@whenComplete
            }
            val completed = updates.filter { update ->
                update.mutation.becameComplete(update.definition.targetAmount)
            }
            if (completed.isEmpty()) {
                return@whenComplete
            }

            runForOnlinePlayer(killerId) {
                completed.forEach { update ->
                    sendDZTSuccess("委托“${update.definition.displayName}”已完成，可前往委托菜单领取奖励。")
                }
            }
        }
    }
}
