package cn.tj.dzd.mc.dzt.flight

import cn.tj.dzd.mc.dzt.ban.BanApi
import cn.tj.dzd.mc.dzt.ban.BanIssueResult
import cn.tj.dzd.mc.dzt.ban.BanService
import cn.tj.dzd.mc.dzt.ban.BanText
import cn.tj.dzd.mc.dzt.ban.PlayerBan
import cn.tj.dzd.mc.dzt.onebot.OneBotGroupApi
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.console
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.platform.util.onlinePlayers
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 飞行速度反作弊检测系统。
 *
 * 定期检测处于飞行状态的玩家移动速度，记录违规行为，并在达到阈值时采取相应措施：
 * - 3 分钟内超过 20 次：向管理群发送提醒消息
 * - 3 分钟内超过 30 次：自动封禁 24 小时
 *
 * 检测标准：
 * - 水平速度（X-Z 平面）超过 1.1 方块/tick
 * - 垂直速度（Y 轴）超过 0.4 方块/tick
 * - 检测频率：20 tick（1 秒）一次
 *
 * ## 架构边界说明
 *
 * 本服务直接访问 [Player.isFlying] 属性以判定玩家飞行状态。这是飞行功能域的反作弊组件，
 * 需要实时读取玩家的飞行状态标志来执行速度检测。TabooLib 未提供飞行状态查询的等价接口，
 * 且该检测必须在 [PlayerMoveEvent] 的玩家实体线程中进行，以保证状态一致性。
 */
object FlightSpeedAntiCheat {

    private const val CHECK_INTERVAL_TICKS = 20L
    private const val HORIZONTAL_SPEED_THRESHOLD = 1.1
    private const val VERTICAL_SPEED_THRESHOLD = 0.4
    private const val VIOLATION_WINDOW_MILLIS = 3L * 60L * 1000L // 3 分钟
    private const val WARNING_THRESHOLD = 20
    private const val BAN_THRESHOLD = 30
    private const val BAN_HOURS = 24L

    /**
     * 违规记录。
     *
     * @property timestamp 违规发生的时间戳（毫秒）。
     * @property horizontalSpeed 水平速度（方块/tick）。
     * @property verticalSpeed 垂直速度（方块/tick）。
     */
    private data class ViolationRecord(
        val timestamp: Long,
        val horizontalSpeed: Double,
        val verticalSpeed: Double,
    )

    /**
     * 检测点数据。
     *
     * @property location 检测点位置。
     * @property timestamp 检测点时间戳（毫秒）。
     */
    private data class CheckPoint(
        val location: Location,
        val timestamp: Long,
    )

    /**
     * 玩家违规数据。
     *
     * @property lastCheckPoint 上一个检测点。
     * @property violations 违规记录列表。
     * @property warningNotified 是否已发送 20 次警告。
     * @property banned 是否已封禁。
     */
    private data class PlayerViolationData(
        var lastCheckPoint: CheckPoint? = null,
        val violations: MutableList<ViolationRecord> = mutableListOf(),
        var warningNotified: Boolean = false,
        var banned: Boolean = false,
    )

    private val playerViolations = ConcurrentHashMap<UUID, PlayerViolationData>()
    
    @Volatile
    private var cleanupTask: PlatformExecutor.PlatformTask? = null

    /**
     * 启动反作弊检测系统。
     */
    @Awake(LifeCycle.ACTIVE)
    fun start() {
        console().sendMessage("§a[反作弊] 飞行速度检测系统已启动")
        console().sendMessage("§7[反作弊] 检测阈值 - 水平: ${HORIZONTAL_SPEED_THRESHOLD} 方块/tick, 垂直: ${VERTICAL_SPEED_THRESHOLD} 方块/tick")
        
        // 每 60 秒清理一次过期数据
        cleanupTask = submit(delay = 1200L, period = 1200L) {
            cleanupExpiredViolations()
        }
    }

    /**
     * 停止反作弊检测系统。
     */
    @Awake(LifeCycle.DISABLE)
    fun stop() {
        cleanupTask?.cancel()
        cleanupTask = null
        playerViolations.clear()
        console().sendMessage("§c[反作弊] 飞行速度检测系统已停止")
    }

    /**
     * 玩家离线时清理数据。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playerViolations.remove(event.player.uniqueId)
    }

    /**
     * 监听玩家移动事件，检测飞行速度违规。
     *
     * 仅对飞行状态的玩家进行检测，且每 20 tick（1 秒）检测一次。
     *
     * TabooLib 没有飞行状态查询接口；这里必须使用 Paper 的 [Player.isFlying] 属性，
     * 并且调用方保证当前位于该玩家的 Folia 实体线程。
     */
    @SubscribeEvent
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        
        // 仅检测飞行状态的玩家
        // TabooLib 没有提供飞行状态查询的等价接口；必须使用原生 Paper API
        if (!player.isFlying) {
            // 玩家停止飞行时清除检测点
            playerViolations[player.uniqueId]?.lastCheckPoint = null
            return
        }

        val to = event.to
        val now = System.currentTimeMillis()
        
        val data = playerViolations.computeIfAbsent(player.uniqueId) { PlayerViolationData() }
        val lastCheckPoint = data.lastCheckPoint
        
        // 第一次检测或距离上次检测不足 20 tick
        if (lastCheckPoint == null) {
            data.lastCheckPoint = CheckPoint(to.clone(), now)
            return
        }
        
        val timeDelta = now - lastCheckPoint.timestamp
        if (timeDelta < CHECK_INTERVAL_TICKS * 50) { // 20 tick = 1000ms
            return
        }
        
        // 计算位移和速度
        val from = lastCheckPoint.location
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        
        // 计算每 tick 的平均速度
        val ticks = timeDelta / 50.0 // 每 tick 50ms
        val horizontalSpeed = sqrt(dx * dx + dz * dz) / ticks
        val verticalSpeed = abs(dy) / ticks
        
        // 更新检测点
        data.lastCheckPoint = CheckPoint(to.clone(), now)
        
        // 检查是否违规
        if (horizontalSpeed > HORIZONTAL_SPEED_THRESHOLD || verticalSpeed > VERTICAL_SPEED_THRESHOLD) {
            handleViolation(player, horizontalSpeed, verticalSpeed, data)
        }
    }

    /**
     * 处理违规行为。
     */
    private fun handleViolation(
        player: Player,
        horizontalSpeed: Double,
        verticalSpeed: Double,
        data: PlayerViolationData,
    ) {
        val now = System.currentTimeMillis()
        
        // 记录违规
        val record = ViolationRecord(now, horizontalSpeed, verticalSpeed)
        data.violations.add(record)
        
        // 清理 3 分钟外的记录
        data.violations.removeIf { it.timestamp < now - VIOLATION_WINDOW_MILLIS }
        
        val violationCount = data.violations.size
        
        // 打印到控制台
        console().sendMessage(
            "§e[反作弊] 飞行速度违规 §f| §7玩家: §f${player.name} §7| " +
            "§7水平: §f%.3f §7垂直: §f%.3f §7| §7累计: §c$violationCount §7次/3分钟".format(
                horizontalSpeed, verticalSpeed
            )
        )
        
        // 根据违规次数采取措施
        when {
            violationCount >= BAN_THRESHOLD && !data.banned -> {
                handleBan(player, data)
            }
            violationCount >= WARNING_THRESHOLD && !data.warningNotified -> {
                handleWarning(player, violationCount, data)
            }
        }
    }

    /**
     * 处理 20 次警告。
     */
    private fun handleWarning(player: Player, count: Int, data: PlayerViolationData) {
        data.warningNotified = true
        
        val message = """
            [飞行反作弊] 警告
            玩家 ${player.name} (UUID: ${player.uniqueId})
            3 分钟内触发飞行速度异常 $count 次
            请注意观察
        """.trimIndent()
        
        OneBotGroupApi.sendManagementGroupMessage(message).whenComplete { _, error ->
            if (error != null) {
                console().sendMessage("§c[反作弊] 向管理群发送警告消息失败: ${error.message}")
            } else {
                console().sendMessage("§a[反作弊] 已向管理群发送警告消息")
            }
        }
    }

    /**
     * 处理 30 次封禁。
     */
    private fun handleBan(player: Player, data: PlayerViolationData) {
        data.banned = true
        val reason = "飞行速度异常"

        BanApi.ban(player.uniqueId, BAN_HOURS, reason).whenComplete { result, error ->
            if (error != null) {
                console().sendMessage("§c[反作弊] 封禁玩家 ${player.name} 失败: ${error.message}")
                return@whenComplete
            }

            when (result) {
                is BanIssueResult.Banned -> {
                    console().sendMessage("§c[反作弊] 已自动封禁玩家 ${player.name}，时长 ${BAN_HOURS} 小时")
                    BanService.disconnectOnlinePlayer(result.record)
                    publishPlayerGroupAnnouncement(player.name, result.record)
                }

                BanIssueResult.Failed,
                null -> console().sendMessage("§c[反作弊] 封禁玩家 ${player.name} 失败")
            }
        }
    }

    /**
     * 将已生效的自动封禁公示异步发送到玩家交流群。
     *
     * OneBot 发送失败不会影响已经写入数据库的封禁结果。
     *
     * @param playerName 公示中展示的玩家名称。
     * @param ban 已成功写入的封禁记录。
     */
    private fun publishPlayerGroupAnnouncement(playerName: String, ban: PlayerBan) {
        val message = BanText.playerGroupAnnouncement(playerName, ban, BigDecimal.valueOf(BAN_HOURS))
        val future = runCatching {
            OneBotGroupApi.sendPlayerGroupMessage(message)
        }.getOrElse { error ->
            console().sendMessage("§c[反作弊] 向玩家交流群发送封禁公示失败: ${error.message}")
            return
        }
        future.whenComplete { _, error ->
            if (error != null) {
                console().sendMessage("§c[反作弊] 向玩家交流群发送封禁公示失败: ${error.message}")
            }
        }
    }

    /**
     * 清理过期的违规记录。
     */
    private fun cleanupExpiredViolations() {
        val now = System.currentTimeMillis()
        var cleanedPlayers = 0
        var cleanedRecords = 0
        
        playerViolations.forEach { (uuid, data) ->
            val sizeBefore = data.violations.size
            data.violations.removeIf { it.timestamp < now - VIOLATION_WINDOW_MILLIS }
            val removed = sizeBefore - data.violations.size
            
            if (removed > 0) {
                cleanedRecords += removed
            }
            
            // 如果没有违规记录了，重置通知状态
            if (data.violations.isEmpty()) {
                data.warningNotified = false
                data.banned = false
            }
        }
        
        // 清理完全没有违规记录的离线玩家
        val onlineUuids = onlinePlayers.map { it.uniqueId }.toSet()
        playerViolations.entries.removeIf { (uuid, data) ->
            val shouldRemove = !onlineUuids.contains(uuid) && data.violations.isEmpty()
            if (shouldRemove) {
                cleanedPlayers++
            }
            shouldRemove
        }
        
        if (cleanedRecords > 0 || cleanedPlayers > 0) {
            console().sendMessage(
                "§7[反作弊] 清理完成 - 移除 $cleanedRecords 条过期记录，清理 $cleanedPlayers 个玩家数据"
            )
        }
    }
}
