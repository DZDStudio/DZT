package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.util.foliaRespawnLocation
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import java.util.concurrent.CompletableFuture

object BedService {
    /**
     * 返回玩家当前配置的复活点。
     *
     * Paper 1.20.4 起推荐使用 RespawnLocation；它覆盖床和重生锚。该接口会在玩家所属实体线程读取
     * 不触发同步区块加载的复活点快照，再通过异步传送加载目标区块，可在 Folia 环境中安全调用。
     *
     * @param pl 玩家对象。
     * @param cause 传送原因。
     * @return 传送结果 Future；未配置复活点、目标世界不存在或玩家已离线时完成为 false。
     */
    fun teleportBed(pl: Player, cause: TeleportCause = TeleportCause.PLUGIN): CompletableFuture<Boolean> {
        val result = CompletableFuture<Boolean>()

        pl.foliaRespawnLocation().whenComplete { respawnLocation, error ->
            if (error != null) {
                result.completeExceptionally(error)
                return@whenComplete
            }
            if (respawnLocation == null || respawnLocation.world == null) {
                result.complete(false)
                return@whenComplete
            }

            pl.foliaTeleport(respawnLocation, cause).whenComplete { success, teleportError ->
                if (teleportError != null) {
                    result.completeExceptionally(teleportError)
                } else {
                    result.complete(success)
                }
            }
        }

        return result
    }
}

/**
 * 返回玩家当前配置的复活点。
 *
 * @return 传送结果 Future；未配置复活点、目标世界不存在或玩家已离线时完成为 false。
 */
fun Player.teleportBed(): CompletableFuture<Boolean> {
    return BedService.teleportBed(this)
}
