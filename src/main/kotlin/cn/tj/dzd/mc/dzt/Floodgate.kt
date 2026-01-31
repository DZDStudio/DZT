package cn.tj.dzd.mc.dzt

import org.bukkit.entity.Player
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.floodgate.api.player.FloodgatePlayer

object Floodgate {
    val floodgateApi: FloodgateApi = FloodgateApi.getInstance()

    /**
     * 获取 Floodgate 玩家
     */
    fun Player.getFloodgatePlayer(): FloodgatePlayer? {
        return floodgateApi.getPlayer(uniqueId)
    }
}