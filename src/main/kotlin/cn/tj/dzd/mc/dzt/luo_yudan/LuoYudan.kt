package cn.tj.dzd.mc.dzt.luo_yudan

import cn.tj.dzd.mc.dzt.data.RedisManager
import cn.tj.dzd.mc.dzt.util.foliaRun
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.platform.util.onlinePlayers
import java.util.concurrent.CompletableFuture

object LuoYudan {
    data class SendOnlinePlayersData(
        val playerNameList: List<String>,
        val number: Number,
    )
    @Awake(LifeCycle.ACTIVE)
    fun sendOnlinePlayers() {
        if (!RedisManager.isAvailable) {
            info("Redis 未启用，跳过在线玩家上报任务。")
            return
        }
        submit(period = 20L) {
            if (!RedisManager.isAvailable) {
                return@submit
            }

            publishOnlinePlayers(onlinePlayers.toList())
        }
    }

    /**
     * 在每个玩家实体线程读取名称后，异步上报在线玩家快照。
     *
     * @param players 由 TabooLib 提供的在线玩家句柄；该方法不会在后台线程读取其状态。
     */
    private fun publishOnlinePlayers(players: List<Player>) {
        val nameSnapshots = players.map(::snapshotPlayerName)
        CompletableFuture.allOf(*nameSnapshots.toTypedArray()).whenComplete { _, _ ->
            val names = nameSnapshots.mapNotNull { it.getNow(null) }
            submit(async = true) {
                if (RedisManager.isAvailable) {
                    RedisManager.publish(
                        "dzt:online_players",
                        SendOnlinePlayersData(
                            playerNameList = names,
                            number = names.size,
                        )
                    )
                }
            }
        }
    }

    private fun snapshotPlayerName(player: Player): CompletableFuture<String?> {
        val snapshot = CompletableFuture<String?>()
        player.foliaRun {
            snapshot.complete(name)
        }.whenComplete { scheduled, error ->
            if (error != null || scheduled != true) {
                snapshot.complete(null)
            }
        }
        return snapshot
    }
}
