package cn.tj.dzd.mc.dzt.log

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.PlayerLogRecord
import cn.tj.dzd.mc.dzt.data.table.playerLogMapper
import cn.tj.dzd.mc.dzt.util.networkPing
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * 玩家行为日志服务。
 *
 * 日志表由外部数据库迁移创建，本服务只负责写入 `type`、`msg` 两列，`time` 使用数据库默认值。
 * 所有数据库操作都在单独的串行线程中执行，避免阻塞 Bukkit/Folia 的玩家线程。
 */
object PlayerLogService {

    private const val LOG_RETENTION_DAYS = 30L
    private const val MAX_MESSAGE_LENGTH = 128
    private val plainTextSerializer = PlainTextComponentSerializer.plainText()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DZT-PlayerLog").apply { isDaemon = true }
    }

    /**
     * 插件启用时清理超过 30 天的日志。
     */
    @Awake(LifeCycle.ACTIVE)
    fun cleanupExpiredLogsOnStartup() {
        enqueue("清理玩家日志") {
            val cutoff = Timestamp.from(Instant.now().minus(LOG_RETENTION_DAYS, ChronoUnit.DAYS))
            val deleted = playerLogMapper.rawDelete {
                // ActionDelete 必须通过 where 挂载过滤条件，否则会生成无条件 DELETE。
                where {
                    "time" lt cutoff
                }
            }
            taboolib.common.platform.function.info("玩家日志清理完成，删除 $deleted 条超过 ${LOG_RETENTION_DAYS} 天的记录。")
        }
    }

    /**
     * 插件关闭时停止日志队列。
     */
    @Awake(LifeCycle.DISABLE)
    fun close() {
        executor.shutdownNow()
    }

    /**
     * 记录玩家聊天内容。仅记录未被取消的聊天事件。
     */
    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncChatEvent) {
        val message = plainTextSerializer.serialize(event.message())
        record(event.player, "说话", "内容：$message")
    }

    /**
     * 记录玩家死亡信息。
     */
    @SubscribeEvent
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val deathMessage = event.deathMessage()
            ?.let(plainTextSerializer::serialize)
            ?.takeIf(String::isNotBlank)
        record(event.entity, "死亡", deathMessage?.let { "死亡信息：$it" } ?: "玩家死亡")
    }

    /**
     * 记录玩家进入服务器。
     */
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        record(event.player, "进入服务器", "玩家进入服务器")
    }

    /**
     * 记录玩家离开服务器。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        record(event.player, "离开服务器", "玩家离开服务器")
    }

    private fun record(player: Player, type: String, detail: String) {
        val ping = runCatching { player.networkPing() }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val message = buildMessage(player, ping, detail)

        enqueue("新增玩家日志") {
            playerLogMapper.insert(PlayerLogRecord(type, message))
        }
    }

    private fun buildMessage(player: Player, ping: Int, detail: String): String {
        val normalizedDetail = detail.replace('\n', ' ').replace('\r', ' ')
        return "玩家名称：${player.name}，UUID：${player.uniqueId}，Ping：${ping}ms；$normalizedDetail"
            .take(MAX_MESSAGE_LENGTH)
    }

    private fun enqueue(action: String, block: () -> Unit) {
        try {
            executor.execute {
                DatabaseGuard.execute(action) {
                    block()
                }
            }
        } catch (_: RejectedExecutionException) {
            // 插件关闭后不再接受新的日志任务。
        }
    }
}
