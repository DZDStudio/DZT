package cn.tj.dzd.mc.dzt.route

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.config
import cn.tj.dzd.mc.dzt.data.table.preferredRouteRecordMapper
import cn.tj.dzd.mc.dzt.teleport.ui.sendTeleportSuccess
import cn.tj.dzd.mc.dzt.util.DEFAULT_FOLIA_TELEPORT_SOUND
import cn.tj.dzd.mc.dzt.util.foliaPlaySound
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.expansion.DurationType
import taboolib.expansion.submitChain
import java.util.UUID
import java.util.concurrent.CompletableFuture

object PreferredRouteService {
    private const val JAVA_PORT = 26111
    private const val BEDROCK_PORT = 36332
    private const val ROUTE_COOLDOWN_MILLIS = 30_000L

    private val routeCooldowns = mutableMapOf<UUID, Long>()

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val targetIp = config.getString("preferred-route.ip", "")?.trim().orEmpty()
        if (targetIp.isEmpty()) {
            return
        }

        CompletableFuture.supplyAsync {
            isPreferredPlayer(player.uniqueId)
        }.whenComplete { preferred, error ->
            if (error != null || preferred != true || !player.isOnline) {
                return@whenComplete
            }
            val now = System.currentTimeMillis()
            val canTransfer = synchronized(routeCooldowns) {
                val lastTransfer = routeCooldowns[player.uniqueId]
                if (lastTransfer != null && now - lastTransfer < ROUTE_COOLDOWN_MILLIS) {
                    false
                } else {
                    routeCooldowns[player.uniqueId] = now
                    true
                }
            }
            if (!canTransfer) {
                return@whenComplete
            }
            player.foliaRun {
                transfer(player, targetIp)
            }
        }
    }

    fun isPreferredPlayer(uuid: UUID): Boolean {
        return DatabaseGuard.execute("查询优选线路玩家", false) {
            preferredRouteRecordMapper.findOne {
                "uuid" eq uuid.toString()
            } != null
        }
    }

    private fun transfer(player: Player, targetIp: String) {
        submitChain {
            if (player.isBePlayer()) {
                wait(1000 * 3, DurationType.MILLIS)
            }
            sync {
                player.sendTeleportSuccess("尊敬的优选用户，您将于 6 秒后重定向至优选线路！")
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
            }

            wait(1000, DurationType.MILLIS)

            sync {
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
            }

            wait(1000, DurationType.MILLIS)

            sync {
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
            }

            wait(1000, DurationType.MILLIS)

            sync {
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
            }

            wait(1000, DurationType.MILLIS)

            sync {
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
            }

            wait(1000, DurationType.MILLIS)

            sync {
                player.foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
            }

            wait(1000, DurationType.MILLIS)

            sync {
                if (player.isBePlayer()) {
                    runCatching {
                        org.geysermc.geyser.api.GeyserApi.api()
                            .connectionByUuid(player.uniqueId)
                            ?.transfer(targetIp, BEDROCK_PORT)
                    }
                } else {
                    player.transfer(targetIp, JAVA_PORT)
                }
            }
        }
    }
}
