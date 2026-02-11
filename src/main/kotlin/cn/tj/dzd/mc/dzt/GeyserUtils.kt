package cn.tj.dzd.mc.dzt

import org.bukkit.entity.Player
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.floodgate.api.player.FloodgatePlayer
import org.geysermc.geyser.api.GeyserApi
import org.geysermc.geyser.api.connection.GeyserConnection

object GeyserUtils {
    val floodgateApi: FloodgateApi = FloodgateApi.getInstance()
    val geyserApi = GeyserApi.api()

    /**
     * 获取 Floodgate 玩家
     */
    fun Player.getFloodgatePlayer(): FloodgatePlayer? {
        return floodgateApi.getPlayer(uniqueId)
    }

    /**
     * 获取 Geyser 连接
     */
    fun Player.getGeyserConnection(): GeyserConnection? {
        return geyserApi.connectionByUuid(uniqueId)
    }

    /**
     * 获取延迟
     */
    fun Player.getPing(): Int {
        val gpl = getGeyserConnection()
        return gpl?.ping() ?: ping
    }
}