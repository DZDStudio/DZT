package cn.tj.dzd.mc.dzt.route

import cn.tj.dzd.mc.dzt.data.config
import cn.tj.dzd.mc.dzt.teleport.ui.sendTeleportSuccess
import cn.tj.dzd.mc.dzt.util.DEFAULT_FOLIA_TELEPORT_SOUND
import cn.tj.dzd.mc.dzt.util.foliaPlaySound
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.expansion.DurationType
import taboolib.expansion.submitChain
import java.util.UUID

object PreferredRouteService {
    /** 允许玩家在加入服务器后进入优选线路的权限。 */
    const val PERMISSION = "dzt.route"

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

        player.foliaRun {
            if (!canUse(this)) {
                return@foliaRun
            }

            val snapshot = PreferredRoutePlayerSnapshot(uniqueId, isBePlayer())
            if (tryAcquireRouteCooldown(snapshot.uuid)) {
                transfer(targetIp, snapshot)
            }
        }
    }

    /**
     * 检查玩家是否拥有进入优选线路的权限。
     *
     * 此接口读取玩家权限状态，应在玩家的 Folia 实体线程调用。
     *
     * @param player 要检查的在线玩家。
     * @return 玩家拥有 [PERMISSION] 时返回 `true`。
     */
    fun canUse(player: Player): Boolean {
        return player.hasPermission(PERMISSION)
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
