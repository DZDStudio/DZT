package cn.tj.dzd.mc.dzt.route

import cn.tj.dzd.mc.dzt.data.config
import cn.tj.dzd.mc.dzt.data.repository.PersistentPreferredRouteRepository
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.teleport.ui.sendTeleportSuccess
import cn.tj.dzd.mc.dzt.util.DEFAULT_FOLIA_TELEPORT_SOUND
import cn.tj.dzd.mc.dzt.util.foliaPlaySound
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.expansion.DurationType
import taboolib.expansion.submitChain
import java.util.UUID

object PreferredRouteService {
    private const val JAVA_PORT = 26111
    private const val BEDROCK_PORT = 36332
    private const val ROUTE_COOLDOWN_MILLIS = 30_000L

    private val routeCooldowns = mutableMapOf<UUID, Long>()
    private val application = PreferredRouteApplicationService(PersistentPreferredRouteRepository)

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val targetIp = config.getString("preferred-route.ip", "")?.trim().orEmpty()
        if (targetIp.isEmpty()) {
            return
        }

        player.foliaRun {
            val snapshot = PreferredRoutePlayerSnapshot(uniqueId, isBePlayer())
            DztAsyncExecutor.supply {
                isPreferredPlayer(snapshot.uuid)
            }.whenComplete { preferred, error ->
                if (error != null || preferred != true) {
                    return@whenComplete
                }
                runForOnlinePlayer(snapshot.uuid) {
                    if (tryAcquireRouteCooldown(snapshot.uuid)) {
                        transfer(targetIp, snapshot)
                    }
                }
            }
        }
    }

    fun isPreferredPlayer(uuid: UUID): Boolean {
        return application.isPreferred(uuid)
    }

    private fun transfer(targetIp: String, snapshot: PreferredRoutePlayerSnapshot) {
        submitChain {
            if (snapshot.isBedrock) {
                wait(1000 * 3, DurationType.MILLIS)
            }
            sync {
                runForOnlinePlayer(snapshot.uuid) {
                    sendTeleportSuccess("尊敬的优选用户，您将于 6 秒后重定向至优选线路！")
                    foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            wait(1000, DurationType.MILLIS)

            sync {
                runForOnlinePlayer(snapshot.uuid) {
                    foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            wait(1000, DurationType.MILLIS)

            sync {
                runForOnlinePlayer(snapshot.uuid) {
                    foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            wait(1000, DurationType.MILLIS)

            sync {
                runForOnlinePlayer(snapshot.uuid) {
                    foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            wait(1000, DurationType.MILLIS)

            sync {
                runForOnlinePlayer(snapshot.uuid) {
                    foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            wait(1000, DurationType.MILLIS)

            sync {
                runForOnlinePlayer(snapshot.uuid) {
                    foliaPlaySound(DEFAULT_FOLIA_TELEPORT_SOUND, pitch = 0.65f)
                }
            }

            wait(1000, DurationType.MILLIS)

            async {
                runForOnlinePlayer(snapshot.uuid) {
                    if (snapshot.isBedrock) {
                        runCatching {
                            org.geysermc.geyser.api.GeyserApi.api()
                                .connectionByUuid(snapshot.uuid)
                                ?.transfer(targetIp, BEDROCK_PORT)
                        }
                    } else {
                        transfer(targetIp, JAVA_PORT)
                    }
                }
            }
        }
    }

    private fun tryAcquireRouteCooldown(uuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        return synchronized(routeCooldowns) {
            routeCooldowns.entries.removeIf { now - it.value >= ROUTE_COOLDOWN_MILLIS }
            val lastTransfer = routeCooldowns[uuid]
            if (lastTransfer != null && now - lastTransfer < ROUTE_COOLDOWN_MILLIS) {
                false
            } else {
                routeCooldowns[uuid] = now
                true
            }
        }
    }
}

private data class PreferredRoutePlayerSnapshot(
    val uuid: UUID,
    val isBedrock: Boolean,
)
