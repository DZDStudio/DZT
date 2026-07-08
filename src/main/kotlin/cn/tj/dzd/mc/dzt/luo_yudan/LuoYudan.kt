package cn.tj.dzd.mc.dzt.luo_yudan

import cn.tj.dzd.mc.dzt.data.RedisManager
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.submit
import taboolib.expansion.submitChain
import taboolib.platform.util.onlinePlayers

object LuoYudan {
    data class SendOnlinePlayersData(
        val playerNameList: List<String>,
        val number: Number,
    )
    @Awake(LifeCycle.ACTIVE)
    fun sendOnlinePlayers() {
        submit(period = 20 * 1, async = true) {
            submitChain {
                val onlinePlayersSnapshot = sync {
                    onlinePlayers
                }

                RedisManager.publish(
                    "dzt:online_players",
                    SendOnlinePlayersData(
                        playerNameList = onlinePlayersSnapshot.map { pl -> pl.name },
                        number = onlinePlayersSnapshot.size,
                    )
                )
            }
        }
    }
}
