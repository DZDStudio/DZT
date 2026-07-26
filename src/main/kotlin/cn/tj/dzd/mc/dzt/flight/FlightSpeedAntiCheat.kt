package cn.tj.dzd.mc.dzt.flight

import cn.tj.dzd.mc.dzt.ban.BanApi
import cn.tj.dzd.mc.dzt.ban.BanIssueResult
import cn.tj.dzd.mc.dzt.ban.BanText
import cn.tj.dzd.mc.dzt.ban.BanType
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
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 飞行速度反作弊检测系统。
 *
 * 仅在玩家实际飞行时每 20 tick 检测一次平均速度，并在内存中维护两个互不重叠的 3 分钟违规窗口：
 *
 * - 常规：水平速度超过 1.2 或垂直速度超过 0.7，7 次提醒管理群、10 次飞行封禁。
 * - 高危：水平速度超过 2.0 或垂直速度超过 1.2，2 次提醒管理群、4 次飞行封禁。
 *
 * 高危违规不会同时写入常规窗口。达到封禁阈值时只创建 `fly` 类型封禁，禁止飞行 24 小时，
 * 不会阻止玩家进入服务器。
 *
 * ## 架构边界说明
 *
 * 本服务直接访问 [Player.isFlying] 属性以判定玩家飞行状态。TabooLib 未提供等价接口，且该检测
 * 必须在 [PlayerMoveEvent] 的玩家实体线程中进行，以保证状态一致性。
 */
object FlightSpeedAntiCheat {

    private const val CHECK_INTERVAL_TICKS = 20L
    private const val BAN_HOURS = 24L
    private const val BAN_REASON = "飞行速度异常"

    /** 一次已分类的违规速度样本。 */
    private data class ViolationRecord(
        val timestamp: Long,
        val horizontalSpeed: Double,
        val verticalSpeed: Double,
    )

    /** 两次实际检测之间的位置与时间快照。 */
    private data class CheckPoint(
        val location: Location,
        val timestamp: Long,
    )

    /** 一个严重等级对应的独立违规窗口。 */
    private data class ViolationTrack(
        val records: ArrayDeque<ViolationRecord> = ArrayDeque(),
        val reminderSent: AtomicBoolean = AtomicBoolean(false),
    )

    /** 单个玩家的检测点、两级违规窗口与飞行封禁状态。 */
    private data class PlayerViolationData(
        @Volatile var lastCheckPoint: CheckPoint? = null,
        val normal: ViolationTrack = ViolationTrack(),
        val severe: ViolationTrack = ViolationTrack(),
        val flyBanIssued: AtomicBoolean = AtomicBoolean(false),
    )

    private val playerViolations = ConcurrentHashMap<UUID, PlayerViolationData>()

    @Volatile
    private var cleanupTask: PlatformExecutor.PlatformTask? = null

    /** 启动反作弊检测与过期违规记录清理任务。 */
    @Awake(LifeCycle.ACTIVE)
    fun start() {
        if (cleanupTask != null) {
            return
        }
        console().sendMessage("§a[反作弊] 飞行速度检测系统已启动")
        console().sendMessage(
            "§7[反作弊] 常规: H>${FlightSpeedViolationPolicy.NORMAL_HORIZONTAL_SPEED_THRESHOLD}, " +
                "V>${FlightSpeedViolationPolicy.NORMAL_VERTICAL_SPEED_THRESHOLD}, 7/10；" +
                "高危: H>${FlightSpeedViolationPolicy.SEVERE_HORIZONTAL_SPEED_THRESHOLD}, " +
                "V>${FlightSpeedViolationPolicy.SEVERE_VERTICAL_SPEED_THRESHOLD}, 2/4"
        )
        cleanupTask = submit(delay = 1200L, period = 1200L) {
            cleanupExpiredViolations()
        }
    }

    /** 停止反作弊检测并清理仅存于内存的违规数据。 */
    @Awake(LifeCycle.DISABLE)
    fun stop() {
        cleanupTask?.cancel()
        cleanupTask = null
        playerViolations.clear()
        console().sendMessage("§c[反作弊] 飞行速度检测系统已停止")
    }

    /**
     * 玩家离线时清除检测点但保留未过期违规，以免通过快速重连绕开三分钟统计窗口。
     */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playerViolations[event.player.uniqueId]?.lastCheckPoint = null
    }

    /**
     * 每 20 tick 检测一次玩家飞行速度。
     *
     * TabooLib 没有飞行状态查询接口；此处使用 Paper 的 [Player.isFlying]，且事件当前运行在玩家
     * 所属的 Folia 实体线程。
     */
    @SubscribeEvent
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!player.isFlying) {
            playerViolations[player.uniqueId]?.lastCheckPoint = null
            return
        }

        val now = System.currentTimeMillis()
        val data = playerViolations.computeIfAbsent(player.uniqueId) { PlayerViolationData() }
        val previous = data.lastCheckPoint
        val currentLocation = event.to

        if (previous == null) {
            data.lastCheckPoint = CheckPoint(currentLocation.clone(), now)
            return
        }

        val elapsedMillis = now - previous.timestamp
        if (elapsedMillis < CHECK_INTERVAL_TICKS * 50L) {
            return
        }

        data.lastCheckPoint = CheckPoint(currentLocation.clone(), now)
        val horizontalSpeed = horizontalSpeed(previous.location, currentLocation, elapsedMillis)
        val verticalSpeed = verticalSpeed(previous.location, currentLocation, elapsedMillis)
        val level = FlightSpeedViolationPolicy.classify(horizontalSpeed, verticalSpeed) ?: return
        handleViolation(player, horizontalSpeed, verticalSpeed, level, data, now)
    }

    /** 记录一项违规，并按该严重等级的阈值触发提醒或飞行封禁。 */
    private fun handleViolation(
        player: Player,
        horizontalSpeed: Double,
        verticalSpeed: Double,
        level: FlightSpeedViolationLevel,
        data: PlayerViolationData,
        now: Long,
    ) {
        val track = data.trackFor(level)
        val count = appendAndCount(
            track,
            ViolationRecord(now, horizontalSpeed, verticalSpeed),
            now - FlightSpeedViolationPolicy.WINDOW_MILLIS,
        )

        console().sendMessage(
            "§e[反作弊] 飞行速度${level.consoleLabel}违规 §f| §7玩家: §f${player.name} §7| " +
                "§7水平: §f%.3f §7垂直: §f%.3f §7| §7累计: §c$count§7/${level.banThreshold} 次/3分钟".format(
                    horizontalSpeed,
                    verticalSpeed,
                )
        )

        when {
            count >= level.banThreshold && data.flyBanIssued.compareAndSet(false, true) -> {
                issueFlightBan(player, level, count, data)
            }

            count >= level.reminderThreshold && track.reminderSent.compareAndSet(false, true) -> {
                sendManagementReminder(player, level, count)
            }
        }
    }

    /** 向管理群发送一个等级窗口内首次达到提醒阈值的告警。 */
    private fun sendManagementReminder(
        player: Player,
        level: FlightSpeedViolationLevel,
        count: Int,
    ) {
        val message = """
            [飞行反作弊] ${level.consoleLabel}速度提醒
            玩家 ${player.name} (UUID: ${player.uniqueId})
            3 分钟内触发${level.consoleLabel}飞行速度异常 $count 次
            当前阈值：提醒 ${level.reminderThreshold} 次，飞行封禁 ${level.banThreshold} 次
        """.trimIndent()
        OneBotGroupApi.sendManagementGroupMessage(message).whenComplete { _, error ->
            if (error != null) {
                console().sendMessage("§c[反作弊] 向管理群发送飞行速度提醒失败: ${error.message}")
            }
        }
    }

    /** 创建 24 小时 `fly` 类型封禁，并即时撤销在线玩家的飞行能力。 */
    private fun issueFlightBan(
        player: Player,
        level: FlightSpeedViolationLevel,
        count: Int,
        data: PlayerViolationData,
    ) {
        BanApi.ban(player.uniqueId, BAN_HOURS, BAN_REASON, BanType.FLY).whenComplete { result, error ->
            if (error != null || result !is BanIssueResult.Banned) {
                data.flyBanIssued.set(false)
                val detail = error?.message ?: "封禁记录未能写入数据库"
                console().sendMessage("§c[反作弊] 封禁玩家 ${player.name} 的飞行功能失败: $detail")
                return@whenComplete
            }

            FlightService.applyFlightBan(result.record)
            console().sendMessage(
                "§c[反作弊] 已封禁玩家 ${player.name} 的飞行功能 ${BAN_HOURS} 小时 " +
                    "(等级: ${level.consoleLabel}, 累计: $count 次)"
            )
            publishFlightBanAnnouncement(player.name, result.record)
        }
    }

    /** 向玩家交流群异步发送飞行封禁公示；发送失败不影响已生效的封禁。 */
    private fun publishFlightBanAnnouncement(playerName: String, ban: PlayerBan) {
        val message = BanText.playerGroupAnnouncement(playerName, ban, BigDecimal.valueOf(BAN_HOURS))
        val future = runCatching {
            OneBotGroupApi.sendPlayerGroupMessage(message)
        }.getOrElse { error ->
            console().sendMessage("§c[反作弊] 向玩家交流群发送飞行封禁公示失败: ${error.message}")
            return
        }
        future.whenComplete { _, error ->
            if (error != null) {
                console().sendMessage("§c[反作弊] 向玩家交流群发送飞行封禁公示失败: ${error.message}")
            }
        }
    }

    /** 清理超出三分钟窗口的记录，并释放已过期的离线玩家内存状态。 */
    private fun cleanupExpiredViolations() {
        val now = System.currentTimeMillis()
        val cutoff = now - FlightSpeedViolationPolicy.WINDOW_MILLIS
        val onlinePlayerIds = onlinePlayers.mapTo(HashSet()) { it.uniqueId }

        playerViolations.entries.removeIf { (playerId, data) ->
            pruneExpired(data.normal, cutoff)
            pruneExpired(data.severe, cutoff)
            if (isEmpty(data.normal)) {
                data.normal.reminderSent.set(false)
            }
            if (isEmpty(data.severe)) {
                data.severe.reminderSent.set(false)
            }
            val empty = isEmpty(data.normal) && isEmpty(data.severe)
            if (empty) {
                data.flyBanIssued.set(false)
            }
            empty && playerId !in onlinePlayerIds
        }
    }

    /** 追加一条违规记录、清理过期记录，并返回当前窗口的记录数。 */
    private fun appendAndCount(track: ViolationTrack, record: ViolationRecord, cutoff: Long): Int {
        return synchronized(track) {
            track.records.addLast(record)
            pruneExpiredLocked(track, cutoff)
            track.records.size
        }
    }

    /** 从队列头部移除早于指定时间的违规记录。 */
    private fun pruneExpired(track: ViolationTrack, cutoff: Long) {
        synchronized(track) {
            pruneExpiredLocked(track, cutoff)
        }
    }

    /** 调用方已持有 [track] 锁时执行实际清理。 */
    private fun pruneExpiredLocked(track: ViolationTrack, cutoff: Long) {
        while (true) {
            val first = track.records.peekFirst() ?: return
            if (first.timestamp >= cutoff) {
                return
            }
            track.records.pollFirst()
        }
    }

    /** 判断一个违规窗口当前是否为空。 */
    private fun isEmpty(track: ViolationTrack): Boolean {
        return synchronized(track) { track.records.isEmpty() }
    }

    /** 计算两次检测点之间的平均水平速度，单位为方块/tick。 */
    private fun horizontalSpeed(from: Location, to: Location, elapsedMillis: Long): Double {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return sqrt(dx * dx + dz * dz) / (elapsedMillis / 50.0)
    }

    /** 计算两次检测点之间的平均垂直速度绝对值，单位为方块/tick。 */
    private fun verticalSpeed(from: Location, to: Location, elapsedMillis: Long): Double {
        return abs(to.y - from.y) / (elapsedMillis / 50.0)
    }

    /** 返回某个严重等级对应的独立违规窗口。 */
    private fun PlayerViolationData.trackFor(level: FlightSpeedViolationLevel): ViolationTrack {
        return when (level) {
            FlightSpeedViolationLevel.NORMAL -> normal
            FlightSpeedViolationLevel.SEVERE -> severe
        }
    }
}
