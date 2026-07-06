package cn.tj.dzd.mc.dzt.sidebar

import cn.tj.dzd.mc.dzt.money.MoneyService
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
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Sidebar {
    private const val SERVER_QQ_GROUP = "747121127"
    private const val OBJECTIVE_NAME = "dzt_sidebar"
    private const val PLAYER_SYNC_PERIOD_TICKS = 20L
    private const val UPDATE_PERIOD_TICKS = 100L

    private var playerSyncTask: PlatformExecutor.PlatformTask? = null
    private val sidebarTasks = ConcurrentHashMap<UUID, PlatformExecutor.PlatformTask>()
    private val sidebarStates = ConcurrentHashMap<UUID, PacketSidebarState>()
    private val lineEntries = listOf("§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9")

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
        playerSyncTask = submit(delay = 1L, period = PLAYER_SYNC_PERIOD_TICKS) {
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
        sidebarTasks.values.forEach { it.cancel() }
        sidebarTasks.clear()
        sidebarStates.clear()
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
        val uuid = event.player.uniqueId
        sidebarTasks.remove(uuid)?.cancel()
        sidebarStates.remove(uuid)
    }

    private fun syncOnlinePlayers() {
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val onlineIds = onlinePlayers.mapTo(mutableSetOf()) { it.uniqueId }

        sidebarTasks.keys
            .filter { it !in onlineIds }
            .forEach {
                sidebarTasks.remove(it)?.cancel()
                sidebarStates.remove(it)
            }

        onlinePlayers.forEach { player ->
            if (!sidebarTasks.containsKey(player.uniqueId)) {
                startSidebarTask(player)
            }
        }
    }

    private fun startSidebarTask(player: Player) {
        val uuid = player.uniqueId
        sidebarTasks.remove(uuid)?.cancel()
        sidebarStates.remove(uuid)
        sidebarTasks[uuid] = submit(async = true, delay = 1L, period = UPDATE_PERIOD_TICKS) {
            refreshSidebar(player, uuid)
        }
    }

    private fun refreshSidebar(player: Player, uuid: UUID) {
        if (!player.isOnline) {
            sidebarTasks.remove(uuid)?.cancel()
            return
        }

        runCatching {
            val balance = MoneyService.getBalance(uuid)
            player.foliaRun {
                updateSidebar(balance)
            }.whenComplete { success, error ->
                if (error != null) {
                    sidebarTasks.remove(uuid)?.cancel()
                    severe(
                        "侧边栏刷新失败。",
                        "玩家 UUID: $uuid",
                        error.stackTraceToString()
                    )
                    return@whenComplete
                }

                if (!success) {
                    sidebarTasks.remove(uuid)?.cancel()
                }
            }
        }.onFailure {
            sidebarTasks.remove(uuid)?.cancel()
            throw it
        }
    }

    private fun Player.updateSidebar(balance: Int) {
        val sidebarLines = buildSidebarLines(balance)
        val state = sidebarStates.computeIfAbsent(uniqueId) { PacketSidebarState() }
        PacketSidebar.update(this, OBJECTIVE_NAME, TextLogo, sidebarLines, lineEntries, state)
    }

    private fun Player.buildSidebarLines(balance: Int): List<String> {
        val bedrockPlayer = isBePlayer()
        val ping = networkPing().coerceAtLeast(0)

        return buildList {
            add("")
            add("§e弟弟币: §6$balance")
            add("§ePing: §a${ping}ms${if (bedrockPlayer) " §7BE" else ""}")
            add("")
            add("§7QQ: $SERVER_QQ_GROUP")

            if (!bedrockPlayer) {
                add("§7Shift+F 打开菜单")
            }
        }
    }
}
