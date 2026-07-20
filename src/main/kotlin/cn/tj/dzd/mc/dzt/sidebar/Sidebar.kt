package cn.tj.dzd.mc.dzt.sidebar

import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.util.TextLogo
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.networkPing
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.submit as submitTask
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.nms.sendScoreboard
import taboolib.platform.util.onlinePlayers
import taboolib.platform.util.submit
import java.math.BigDecimal
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object Sidebar {
    private const val SERVER_QQ_GROUP = "747121127"
    private const val PLAYER_SYNC_PERIOD_TICKS = 20L
    private const val UPDATE_PERIOD_TICKS = 100L
    private val beijingZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val sidebarTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private var playerSyncTask: PlatformExecutor.PlatformTask? = null
    private val sidebarTasks = ConcurrentHashMap<UUID, SidebarTask>()
    private val balanceRequests = ConcurrentHashMap<UUID, Long>()
    private val reportedBalanceFailures = ConcurrentHashMap<UUID, Long>()
    private val taskGeneration = AtomicLong()

    private data class SidebarTask(
        val player: Player,
        val generation: Long,
        val task: PlatformExecutor.PlatformTask,
    )

    /**
     * 启动侧边栏在线玩家同步任务。
     *
     * 同步任务会周期性补齐已在线玩家的侧边栏刷新任务，避免只依赖进服事件导致玩家没有侧边栏。
     */
    @Awake(LifeCycle.ACTIVE)
    fun start() {
        if (playerSyncTask != null) {
            return
        }

        syncOnlinePlayers()
        playerSyncTask = submitTask(delay = 1L, period = PLAYER_SYNC_PERIOD_TICKS) {
            syncOnlinePlayers()
        }
    }

    /**
     * 插件卸载时取消所有侧边栏刷新任务。
     */
    @Awake(LifeCycle.DISABLE)
    fun stop() {
        playerSyncTask?.cancel()
        playerSyncTask = null
        sidebarTasks.values.forEach { it.task.cancel() }
        sidebarTasks.clear()
        balanceRequests.clear()
        reportedBalanceFailures.clear()
    }

    /**
     * 玩家进入服务器时启动侧边栏刷新任务。
     */
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        startSidebarTask(event.player)
    }

    /**
     * 玩家离开服务器时取消侧边栏刷新任务。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        removeSidebarTask(event.player.uniqueId, event.player)
    }

    private fun syncOnlinePlayers() {
        val players = onlinePlayers
        val onlineIds = players.mapTo(mutableSetOf()) { it.uniqueId }

        sidebarTasks.keys
            .filter { it !in onlineIds }
            .forEach {
                removeSidebarTask(it)
            }

        players.forEach(::startSidebarTask)
    }

    private fun startSidebarTask(player: Player) {
        val uuid = player.uniqueId
        sidebarTasks.compute(uuid) { _, current ->
            if (current != null && current.player === player) {
                current
            } else {
                current?.task?.cancel()
                val generation = taskGeneration.incrementAndGet()
                balanceRequests.remove(uuid)
                reportedBalanceFailures.remove(uuid)
                val task = player.submit(delay = 1L, period = UPDATE_PERIOD_TICKS) {
                    refreshSidebar(player, uuid, generation)
                }
                SidebarTask(player, generation, task)
            }
        }
    }

    private fun refreshSidebar(player: Player, uuid: UUID, generation: Long) {
        if (!isCurrentTask(uuid, generation)) {
            return
        }
        if (!player.isOnline) {
            removeSidebarTask(uuid, player, generation)
            return
        }
        if (balanceRequests.putIfAbsent(uuid, generation) != null) {
            return
        }
        if (!isCurrentTask(uuid, generation)) {
            balanceRequests.remove(uuid, generation)
            return
        }

        runCatching {
            ServiceEconomy.getBalance(uuid).whenComplete { balance, balanceError ->
                player.foliaRun {
                    balanceRequests.remove(uuid, generation)
                    if (!isCurrentTask(uuid, generation)) {
                        return@foliaRun
                    }

                    if (!isOnline) {
                        removeSidebarTask(uuid, player, generation)
                        return@foliaRun
                    }

                    if (balanceError != null && reportedBalanceFailures.putIfAbsent(uuid, generation) == null) {
                        severe(
                            "ServiceIO 余额查询失败，侧边栏将暂时隐藏余额。",
                            "玩家 UUID: $uuid",
                            balanceError.stackTraceToString(),
                        )
                    } else if (balanceError == null) {
                        reportedBalanceFailures.remove(uuid, generation)
                    }

                    updateSidebar(balance?.amount)
                }.whenComplete { success, error ->
                    if (error != null) {
                        balanceRequests.remove(uuid, generation)
                        removeSidebarTask(uuid, player, generation)
                        severe(
                            "侧边栏刷新失败。",
                            "玩家 UUID: $uuid",
                            error.stackTraceToString()
                        )
                        return@whenComplete
                    }

                    if (!success) {
                        balanceRequests.remove(uuid, generation)
                        removeSidebarTask(uuid, player, generation)
                    }
                }
            }
        }.onFailure { error ->
            balanceRequests.remove(uuid, generation)
            removeSidebarTask(uuid, player, generation)
            severe(
                "侧边栏余额查询启动失败。",
                "玩家 UUID: $uuid",
                error.stackTraceToString(),
            )
        }
    }

    private fun isCurrentTask(uuid: UUID, generation: Long): Boolean {
        return sidebarTasks[uuid]?.generation == generation
    }

    private fun removeSidebarTask(
        uuid: UUID,
        expectedPlayer: Player? = null,
        expectedGeneration: Long? = null,
    ) {
        sidebarTasks.computeIfPresent(uuid) { _, current ->
            if (
                (expectedPlayer == null || current.player === expectedPlayer) &&
                (expectedGeneration == null || current.generation == expectedGeneration)
            ) {
                current.task.cancel()
                balanceRequests.remove(uuid, current.generation)
                reportedBalanceFailures.remove(uuid, current.generation)
                null
            } else {
                current
            }
        }
    }

    private fun Player.updateSidebar(balance: BigDecimal?) {
        val sidebarLines = buildSidebarLines(balance)
        sendScoreboard(TextLogo, *sidebarLines.toTypedArray())
    }

    private fun Player.buildSidebarLines(balance: BigDecimal?): List<String> {
        val bedrockPlayer = isBePlayer()
        val ping = networkPing().coerceAtLeast(0)
        val tps = currentRegionTps()
        val regionUtilisation = FoliaRegionMetrics.currentOneMinuteUtilisation()

        return buildList {
            add("")
            add("§eDDB: §6${formatBalance(balance)}")
            add("§ePing: §a${ping}ms${if (bedrockPlayer) " §7BE" else ""}")
            add("§eTPS: ${formatTps(tps)}")
            add("§eUsage: ${formatRegionUtilisation(regionUtilisation)}")
            add("")
            add("§f${beijingTime()}")
            add("§7QQ: $SERVER_QQ_GROUP")

            if (!bedrockPlayer) {
                add("§7Shift+F 打开菜单")
            }
        }
    }

    private fun formatBalance(balance: BigDecimal?): String {
        return balance?.let(ServiceEconomy::formatAmount) ?: "--"
    }

    /**
     * 读取当前执行线程所在区域的 1 分钟 TPS。
     *
     * Folia 会根据当前区域线程返回对应区域的 TPS，因此该方法必须在玩家实体线程中调用。
     * 普通 Paper 环境下返回全服 TPS。
     */
    private fun currentRegionTps(): Double {
        return Bukkit.getTPS().firstOrNull()?.coerceAtLeast(0.0) ?: 0.0
    }

    private fun formatTps(tps: Double): String {
        val color = when {
            tps >= 18.0 -> "§a"
            tps >= 15.0 -> "§e"
            else -> "§c"
        }
        return "$color${String.format(Locale.ROOT, "%.2f", tps)}"
    }

    private fun formatRegionUtilisation(utilisation: Double?): String {
        if (utilisation == null) {
            return "§7N/A"
        }

        val color = when {
            utilisation < 0.6 -> "§a"
            utilisation < 0.8 -> "§e"
            else -> "§c"
        }
        return "$color${String.format(Locale.ROOT, "%.1f", utilisation * 100.0)}%"
    }

    private fun beijingTime(): String {
        return LocalTime.now(beijingZone).format(sidebarTimeFormatter)
    }
}
