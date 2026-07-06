package cn.tj.dzd.mc.dzt.teleport.ui

import cn.tj.dzd.mc.dzt.teleport.service.teleportBed
import org.bukkit.entity.Player

object Bed {

    /**
     * 执行回床传送。
     *
     * 会传送到玩家当前有效复活点；没有床或重生锚时给出失败提示。
     */
    fun teleportBedUI(player: Player) {
        player.teleportBed().thenAccept { success ->
            if (success) {
                player.sendTeleportSuccess("已返回当前复活点。")
            } else {
                player.sendTeleportError("没有可用的床或复活点。")
            }
        }
    }
}
