package cn.tj.dzd.mc.dzt.util

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause
import taboolib.platform.util.bukkitPlugin
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 玩家传送成功后的默认提示音。
 *
 * 这个声音会在 [foliaTeleport] 成功后播放，作为统一的“破防”反馈音效。
 */
val DEFAULT_FOLIA_TELEPORT_SOUND: Sound = Sound.BLOCK_NOTE_BLOCK_PLING

/**
 * 在玩家所属实体线程执行操作。
 *
 * Folia 环境下不能假设所有玩家操作都在同一个主线程执行；该接口会把操作调度到玩家当前所属区域线程。
 *
 * @param block 需要在玩家实体线程执行的操作。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaRun(block: Player.() -> Unit): CompletableFuture<Boolean> {
    return foliaCall(false) {
        block()
        true
    }
}

/**
 * 通过 UUID 在在线玩家的 Folia 实体线程执行操作。
 *
 * TabooLib 没有 UUID 到强类型 [Player] 的等价查找接口，因此该方法集中保留必要的
 * Bukkit 查找边界。查找结果不会在调用方线程读取实体状态，只会立即用于调度 [block]。
 *
 * @param uuid 目标玩家 UUID。
 * @param block 要在目标玩家实体线程执行的操作。
 * @return 操作是否成功进入并完成执行；目标离线或实体调度器失效时完成为 false。
 */
fun runForOnlinePlayer(uuid: UUID, block: Player.() -> Unit): CompletableFuture<Boolean> {
    val player = Bukkit.getPlayer(uuid) ?: return CompletableFuture.completedFuture(false)
    return player.foliaRun(block)
}

/**
 * 读取玩家当前位置快照。
 *
 * @return 当前位置副本；玩家离线或实体调度器失效时完成为 null。
 */
fun Player.foliaLocation(): CompletableFuture<Location?> {
    return foliaCall(null) {
        location.clone()
    }
}

/**
 * 读取玩家当前有效复活点快照。
 *
 * Paper 1.20.4 起复活点可能来自床或重生锚；该接口会在玩家实体线程读取，避免 Folia 线程问题。
 *
 * @return 复活点副本；没有有效复活点、玩家离线或实体调度器失效时完成为 null。
 */
fun Player.foliaRespawnLocation(): CompletableFuture<Location?> {
    return foliaCall(null) {
        respawnLocation?.clone()
    }
}

/**
 * Folia 兼容地传送玩家到指定坐标。
 *
 * 成功传送后会在玩家当前实体线程播放 [sound]，默认使用 [DEFAULT_FOLIA_TELEPORT_SOUND]。
 *
 * @param location 目标坐标。
 * @param cause 传送原因。
 * @param sound 传送成功后播放的音效；传 null 表示不播放。
 * @param volume 音量。
 * @param pitch 音高。
 * @return 传送结果 Future；目标世界不存在、玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaTeleport(
    location: Location,
    cause: TeleportCause = TeleportCause.PLUGIN,
    sound: Sound? = DEFAULT_FOLIA_TELEPORT_SOUND,
    volume: Float = 1.0f,
    pitch: Float = 0.65f,
): CompletableFuture<Boolean> {
    val targetLocation = location.clone()
    if (targetLocation.world == null) {
        return CompletableFuture.completedFuture(false)
    }

    val result = CompletableFuture<Boolean>()
    val scheduledTask = scheduler.run(
        bukkitPlugin,
        { _ ->
            if (!isOnline) {
                result.complete(false)
                return@run
            }

            try {
                teleportAsync(targetLocation, cause).whenComplete { success, error ->
                    if (error != null) {
                        result.completeExceptionally(error)
                        return@whenComplete
                    }

                    if (success && sound != null) {
                        foliaPlaySound(sound, volume, pitch)
                    }
                    result.complete(success)
                }
            } catch (ex: Throwable) {
                result.completeExceptionally(ex)
            }
        },
        {
            result.complete(false)
        }
    )

    if (scheduledTask == null) {
        result.complete(false)
    }

    return result
}

/**
 * Folia 兼容地传送玩家到另一名玩家当前位置。
 *
 * 目标玩家的位置会先在目标玩家实体线程读取，再传送当前玩家。
 *
 * @param target 目标玩家。
 * @param cause 传送原因。
 * @param sound 传送成功后播放的音效；传 null 表示不播放。
 * @param volume 音量。
 * @param pitch 音高。
 * @return 传送结果 Future；任一玩家离线、目标位置不可读或实体调度器失效时完成为 false。
 */
fun Player.foliaTeleport(
    target: Player,
    cause: TeleportCause = TeleportCause.PLUGIN,
    sound: Sound? = DEFAULT_FOLIA_TELEPORT_SOUND,
    volume: Float = 1.0f,
    pitch: Float = 0.65f,
): CompletableFuture<Boolean> {
    val result = CompletableFuture<Boolean>()

    target.foliaLocation().whenComplete { targetLocation, error ->
        if (error != null) {
            result.completeExceptionally(error)
            return@whenComplete
        }
        if (targetLocation == null) {
            result.complete(false)
            return@whenComplete
        }

        foliaTeleport(targetLocation, cause, sound, volume, pitch).whenComplete { success, teleportError ->
            if (teleportError != null) {
                result.completeExceptionally(teleportError)
            } else {
                result.complete(success)
            }
        }
    }

    return result
}

/**
 * 在玩家实体线程播放音效。
 *
 * @param sound 音效。
 * @param volume 音量。
 * @param pitch 音高。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaPlaySound(sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f): CompletableFuture<Boolean> {
    return foliaRun {
        playSound(location, sound, volume, pitch)
    }
}

/**
 * 在玩家实体线程关闭当前界面。
 *
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaCloseInventory(): CompletableFuture<Boolean> {
    return foliaRun {
        closeInventory()
    }
}

/**
 * 在玩家实体线程执行玩家命令。
 *
 * @param command 不带斜杠的命令内容。
 * @return 命令是否由 Bukkit 接受执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaPerformCommand(command: String): CompletableFuture<Boolean> {
    return foliaCall(false) {
        performCommand(command)
    }
}

/**
 * 在玩家实体线程踢出玩家。
 *
 * @param message 踢出提示。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaKick(message: Component): CompletableFuture<Boolean> {
    return foliaRun {
        kick(message)
    }
}

/**
 * 在玩家实体线程发送统一格式的 DZT 普通文本消息。
 *
 * @param message 不含前缀的消息主体。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaSendMessage(message: String): CompletableFuture<Boolean> {
    return sendDZTMessage(message)
}

/**
 * 在玩家实体线程发送统一格式的 DZT 普通 Adventure 消息。
 *
 * @param message 不含前缀的消息主体组件。
 * @return 操作是否成功进入并完成执行；玩家离线或实体调度器失效时完成为 false。
 */
fun Player.foliaSendMessage(message: Component): CompletableFuture<Boolean> {
    return sendDZTMessage(message)
}

private fun <T> Player.foliaCall(fallback: T, block: Player.() -> T): CompletableFuture<T> {
    val result = CompletableFuture<T>()

    val scheduledTask = scheduler.run(
        bukkitPlugin,
        { _ ->
            if (!isOnline) {
                result.complete(fallback)
                return@run
            }

            try {
                result.complete(block())
            } catch (ex: Throwable) {
                result.completeExceptionally(ex)
            }
        },
        {
            result.complete(fallback)
        }
    )

    if (scheduledTask == null) {
        result.complete(fallback)
    }

    return result
}
