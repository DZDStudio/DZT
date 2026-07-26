package cn.tj.dzd.mc.dzt.flight

import cn.tj.dzd.mc.dzt.ban.BanApi
import cn.tj.dzd.mc.dzt.ban.BanLookupResult
import cn.tj.dzd.mc.dzt.ban.BanType
import cn.tj.dzd.mc.dzt.ban.PlayerBan
import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.repository.PersistentFlightRepository
import cn.tj.dzd.mc.dzt.economy.EconomyWithdrawalResult
import cn.tj.dzd.mc.dzt.economy.EconomyWithdrawalStatus
import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.platform.SerialTaskQueue
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.sendDZTError
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.platform.util.onlinePlayers
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** 读取玩家飞行开关的结果。 */
sealed interface FlightSettingState {

    /**
     * 成功读取到的飞行开关。
     *
     * @property enabled 飞行功能是否开启。
     */
    data class Available(val enabled: Boolean) : FlightSettingState

    /** 持久化存储不可用，无法确定飞行开关。 */
    data object Unavailable : FlightSettingState
}

/** 玩家请求切换飞行开关后的结果。 */
enum class FlightToggleResult {
    /** 已开启飞行。 */
    ENABLED,

    /** 已关闭飞行。 */
    DISABLED,

    /** 该玩家已有一次飞行开关变更尚未结束。 */
    IN_PROGRESS,

    /** 玩家当前存在有效的飞行封禁，不能开启飞行。 */
    RESTRICTED,

    /** 持久化操作失败，开关未改变。 */
    FAILED,
}

/**
 * 玩家飞行偏好、Folia 飞行能力与周期扣费的运行时服务。
 *
 * 同一 UUID 的设置读写均通过一个串行队列执行。数据库与 ServiceIO 操作不占用玩家实体线程；
 * `gameMode`、`isFlying` 与 `allowFlight` 只会在玩家所属的 Folia 实体线程读取或修改。
 */
object FlightService {

    private const val FLIGHT_CHECK_PERIOD_TICKS = 20L * 5L
    private const val WITHDRAWAL_TIMEOUT_SECONDS = 15L
    private val FLIGHT_CHECK_COST = BigDecimal.ONE

    private data class SessionKey(val playerId: UUID, val token: Long)

    private data class ActiveSession(val player: Player, val key: SessionKey)

    private enum class SettingOperationType {
        READ,
        MUTATION,
    }

    private data class VersionedSettingResult<T>(val token: Long, val value: T)

    private data class ToggleMutation(
        val result: FlightToggleResult,
        val enabled: Boolean? = null,
        val forcedDisableToken: Long? = null,
    )

    private enum class FlightBanLookup {
        CLEAR,
        RESTRICTED,
        UNAVAILABLE,
    }

    private val repository: FlightRepository = PersistentFlightRepository
    private val settingLifecycleLock = Any()
    private val activeSessions = ConcurrentHashMap<UUID, ActiveSession>()
    private val preferences = ConcurrentHashMap<SessionKey, Boolean>()
    private val settingQueues = ConcurrentHashMap<UUID, SerialTaskQueue>()
    private val toggleRequests = ConcurrentHashMap.newKeySet<UUID>()
    private val chargeInProgress = ConcurrentHashMap.newKeySet<UUID>()
    private val chargeLedger = FlightChargeLedger()
    private val settingStateGuard = FlightSettingStateGuard()
    private val flightBans = ConcurrentHashMap<UUID, PlayerBan>()
    private val sessionTokenSequence = AtomicLong()

    @Volatile
    private var flightCheckTask: PlatformExecutor.PlatformTask? = null

    @Volatile
    private var acceptingOperations = false

    /** 启动在线玩家状态加载与每 100 tick 一次的飞行扣费检查。 */
    @Awake(LifeCycle.ACTIVE)
    fun start() {
        if (flightCheckTask != null) {
            return
        }

        synchronized(settingLifecycleLock) {
            acceptingOperations = true
        }
        onlinePlayers.forEach(::registerSession)
        flightCheckTask = submit(
            delay = FLIGHT_CHECK_PERIOD_TICKS,
            period = FLIGHT_CHECK_PERIOD_TICKS,
        ) {
            reconcileExpiredFlightBans()
            checkFlyingPlayers()
        }
    }

    /** 停止周期任务并清理飞行服务的运行时状态。 */
    @Awake(LifeCycle.DISABLE)
    fun stop() {
        synchronized(settingLifecycleLock) {
            acceptingOperations = false
            settingQueues.values.forEach(SerialTaskQueue::close)
            settingQueues.clear()
        }
        flightCheckTask?.cancel()
        flightCheckTask = null
        activeSessions.clear()
        preferences.clear()
        toggleRequests.clear()
        chargeInProgress.clear()
        chargeLedger.clear()
        settingStateGuard.clear()
        flightBans.clear()
    }

    /**
     * 异步读取当前玩家会话的飞行开关。
     *
     * 在线会话已有快照时直接返回；否则在该 UUID 的设置队列中读取数据库。旧 Player 实例
     * 不会收到快速重连后新会话的结果。
     *
     * @param player 当前在线玩家。
     * @return 异步读取结果；玩家已离线或数据库不可用时返回 [FlightSettingState.Unavailable]。
     */
    fun getSetting(player: Player): CompletableFuture<FlightSettingState> {
        if (!acceptingOperations) {
            return CompletableFuture.completedFuture(FlightSettingState.Unavailable)
        }
        val session = activeSession(player)
            ?: return CompletableFuture.completedFuture(FlightSettingState.Unavailable)
        return refreshFlightBan(session).thenCompose { flightBan ->
            when (flightBan) {
                FlightBanLookup.RESTRICTED -> {
                    CompletableFuture.completedFuture(FlightSettingState.Available(false))
                }

                FlightBanLookup.UNAVAILABLE -> {
                    CompletableFuture.completedFuture(FlightSettingState.Unavailable)
                }

                FlightBanLookup.CLEAR -> getSettingWithoutFlightBan(session)
            }
        }
    }

    /** 在确认没有飞行封禁后读取当前会话的飞行设置。 */
    private fun getSettingWithoutFlightBan(session: SessionKey): CompletableFuture<FlightSettingState> {
        if (settingStateGuard.isForcedDisabled(session.playerId)) {
            return CompletableFuture.completedFuture(FlightSettingState.Available(false))
        }
        preferences[session]?.let { enabled ->
            return CompletableFuture.completedFuture(FlightSettingState.Available(enabled))
        }
        return readPreference(session, failClosedOnFailure = false)
    }

    /**
     * 异步切换并持久化当前玩家会话的飞行开关。
     *
     * 每次切换都会在该 UUID 的串行设置队列中重新读取数据库后写入相反值。这样登录加载、
     * 扣费失败关闭与玩家切换不会并发访问同一设置。成功结果会应用到完成时仍在线的当前会话。
     *
     * @param player 发起操作的当前在线玩家。
     * @return 异步切换结果。
     */
    fun toggle(player: Player): CompletableFuture<FlightToggleResult> {
        if (!acceptingOperations) {
            return CompletableFuture.completedFuture(FlightToggleResult.FAILED)
        }
        val session = activeSession(player)
            ?: return CompletableFuture.completedFuture(FlightToggleResult.FAILED)
        val playerId = session.playerId
        if (!toggleRequests.add(playerId)) {
            return CompletableFuture.completedFuture(FlightToggleResult.IN_PROGRESS)
        }

        return refreshFlightBan(session).thenCompose { flightBan ->
            when (flightBan) {
                FlightBanLookup.RESTRICTED -> CompletableFuture.completedFuture(FlightToggleResult.RESTRICTED)
                FlightBanLookup.UNAVAILABLE -> CompletableFuture.completedFuture(FlightToggleResult.FAILED)
                FlightBanLookup.CLEAR -> toggleWithoutFlightBan(playerId)
            }
        }.whenComplete { _, _ ->
            toggleRequests.remove(playerId)
        }
    }

    /** 在确认没有飞行封禁后执行飞行开关持久化。 */
    private fun toggleWithoutFlightBan(playerId: UUID): CompletableFuture<FlightToggleResult> {
        return submitSettingOperation(playerId, SettingOperationType.MUTATION) {
            val forcedDisableToken = settingStateGuard.forcedDisableToken(playerId)
            val current = if (forcedDisableToken != null) {
                false
            } else {
                when (val loaded = repository.isEnabled(playerId)) {
                    is RepositoryResult.Success -> loaded.value
                    RepositoryResult.Failure -> return@submitSettingOperation ToggleMutation(
                        FlightToggleResult.FAILED
                    )
                }
            }
            val enabled = !current
            when (repository.setEnabled(playerId, enabled)) {
                is RepositoryResult.Success -> ToggleMutation(
                    result = if (enabled) FlightToggleResult.ENABLED else FlightToggleResult.DISABLED,
                    enabled = enabled,
                    forcedDisableToken = forcedDisableToken,
                )

                RepositoryResult.Failure -> ToggleMutation(FlightToggleResult.FAILED)
            }
        }.handle { versioned, error ->
            if (error != null || versioned == null) {
                return@handle FlightToggleResult.FAILED
            }
            val mutation = versioned.value
            val enabled = mutation.enabled
            if (
                mutation.result != FlightToggleResult.FAILED &&
                enabled != null &&
                settingStateGuard.isCurrentMutation(playerId, versioned.token)
            ) {
                if (enabled) {
                    mutation.forcedDisableToken?.let { token ->
                        settingStateGuard.clearForcedDisable(playerId, token)
                    }
                }
                publishPreference(playerId, enabled)
            }
            mutation.result
        }
    }

    /**
     * 对当前在线会话即时应用一条已生效的 `fly` 封禁。
     *
     * 此操作只撤销运行时飞行能力，不修改玩家持久化的飞行开关；解封或到期后可恢复原有设置。
     *
     * @param ban 已成功持久化且类型为 [BanType.FLY] 的封禁记录。
     * @return 玩家在线且已完成飞行能力更新时为 `true`；玩家离线或记录无效时为 `false`。
     */
    fun applyFlightBan(ban: PlayerBan): CompletableFuture<Boolean> {
        if (!ban.type.blocksFlight || !ban.isEffectiveAt(System.currentTimeMillis())) {
            return CompletableFuture.completedFuture(false)
        }
        cacheFlightBan(ban)
        val active = activeSessions[ban.playerId] ?: return CompletableFuture.completedFuture(false)
        return applyPreferenceToSession(active.key)
    }

    /**
     * 清除当前进程中缓存的飞行封禁，并重新应用持久化的飞行开关。
     *
     * 调用方应仅在指定 `fly` 类型封禁已成功解除后调用。
     *
     * @param playerId 被解除飞行封禁的玩家 UUID。
     * @return 在线会话成功刷新飞行设置时为 `true`；玩家离线或存储不可用时为 `false`。
     */
    fun clearFlightBan(playerId: UUID): CompletableFuture<Boolean> {
        flightBans.remove(playerId)
        val active = activeSessions[playerId] ?: return CompletableFuture.completedFuture(false)
        return readPreference(active.key, failClosedOnFailure = true)
            .thenApply { it is FlightSettingState.Available }
    }

    /** 玩家加入后创建新会话，并异步应用数据库中的飞行开关。 */
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        registerSession(event.player)
    }

    /** 玩家离线后只清理与该 Player 实例对应的会话。 */
    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerId = event.player.uniqueId
        var removed: SessionKey? = null
        activeSessions.computeIfPresent(playerId) { _, active ->
            if (active.player === event.player) {
                removed = active.key
                null
            } else {
                active
            }
        }
        removed?.let(preferences::remove)
    }

    /** 玩家重生完成后重新应用已开启的飞行能力。 */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        reapplyEnabledFlight(event.player)
    }

    /**
     * 玩家进入生存或冒险模式后重新应用已开启的飞行能力。
     *
     * 事件完成后再通过实体调度器执行，避免服务端切换游戏模式时随后重置能力状态。
     */
    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerGameModeChange(event: PlayerGameModeChangeEvent) {
        if (FlightPolicy.managesAllowFlight(event.newGameMode.toFlightGameMode())) {
            reapplyEnabledFlight(event.player)
        }
    }

    private fun registerSession(player: Player) {
        val playerId = player.uniqueId
        val session = SessionKey(playerId, sessionTokenSequence.incrementAndGet())
        activeSessions.put(playerId, ActiveSession(player, session))?.let { previous ->
            preferences.remove(previous.key)
        }
        if (settingStateGuard.isForcedDisabled(playerId)) {
            preferences[session] = false
            applyPreferenceToSession(session)
        }
        refreshPreference(session)
        refreshFlightBan(session)
    }

    private fun activeSession(player: Player): SessionKey? {
        val active = activeSessions[player.uniqueId] ?: return null
        return active.key.takeIf { active.player === player }
    }

    private fun isActiveSession(session: SessionKey): Boolean {
        return activeSessions[session.playerId]?.key == session
    }

    private fun refreshPreference(session: SessionKey) {
        readPreference(session, failClosedOnFailure = true)
    }

    private fun readPreference(
        session: SessionKey,
        failClosedOnFailure: Boolean,
    ): CompletableFuture<FlightSettingState> {
        return submitSettingOperation(session.playerId, SettingOperationType.READ) {
            repository.isEnabled(session.playerId)
        }.handle { versioned, error ->
            if (error != null || versioned == null) {
                return@handle FlightSettingState.Unavailable
            }
            if (!settingStateGuard.isCurrentRead(session.playerId, versioned.token)) {
                return@handle currentSettingState(session)
            }

            when (val result = versioned.value) {
                is RepositoryResult.Success -> {
                    publishPreference(session.playerId, result.value)
                    currentSettingState(session)
                }

                RepositoryResult.Failure -> {
                    if (failClosedOnFailure) {
                        publishPreference(session.playerId, false)
                    }
                    FlightSettingState.Unavailable
                }
            }
        }
    }

    private fun currentSettingState(session: SessionKey): FlightSettingState {
        if (!isActiveSession(session)) {
            return FlightSettingState.Unavailable
        }
        if (settingStateGuard.isForcedDisabled(session.playerId)) {
            return FlightSettingState.Available(false)
        }
        if (hasActiveFlightBan(session.playerId)) {
            return FlightSettingState.Available(false)
        }
        return preferences[session]?.let(FlightSettingState::Available)
            ?: FlightSettingState.Unavailable
    }

    private fun publishPreference(playerId: UUID, enabled: Boolean) {
        val active = activeSessions[playerId] ?: return
        val effectiveEnabled = enabled && !settingStateGuard.isForcedDisabled(playerId)
        preferences[active.key] = effectiveEnabled
        applyPreferenceToSession(active.key)
    }

    private fun applyPreferenceToSession(session: SessionKey): CompletableFuture<Boolean> {
        val active = activeSessions[session.playerId]
            ?: return CompletableFuture.completedFuture(false)
        if (active.key != session) {
            return CompletableFuture.completedFuture(false)
        }
        return active.player.foliaRun {
            if (activeSession(this) == session) {
                val enabled = preferences[session] == true &&
                    !settingStateGuard.isForcedDisabled(session.playerId)
                    && !hasActiveFlightBan(session.playerId)
                applyManagedFlightPreference(enabled)
            }
        }
    }

    private fun reapplyEnabledFlight(player: Player) {
        val session = activeSession(player) ?: return
        if (preferences[session] != true || settingStateGuard.isForcedDisabled(session.playerId)) {
            return
        }
        player.foliaRun {
            if (
                activeSession(this) == session &&
                preferences[session] == true &&
                !settingStateGuard.isForcedDisabled(session.playerId)
                && !hasActiveFlightBan(session.playerId)
            ) {
                applyManagedFlightPreference(true)
            }
        }
    }

    private fun checkFlyingPlayers() {
        onlinePlayers.forEach { player ->
            val session = activeSession(player) ?: return@forEach
            if (
                preferences[session] != true ||
                settingStateGuard.isForcedDisabled(session.playerId) ||
                hasActiveFlightBan(session.playerId)
            ) {
                return@forEach
            }

            player.foliaRun {
                if (
                    activeSession(this) != session ||
                    settingStateGuard.isForcedDisabled(session.playerId) ||
                    hasActiveFlightBan(session.playerId)
                ) {
                    return@foliaRun
                }
                val shouldCharge = FlightPolicy.shouldChargeForFlightCheck(
                    enabled = preferences[session] == true,
                    actuallyFlying = isFlying,
                    gameMode = gameMode.toFlightGameMode(),
                )
                if (shouldCharge) {
                    enqueueFlightCharge(session.playerId)
                }
            }
        }
    }

    private fun enqueueFlightCharge(playerId: UUID) {
        chargeLedger.enqueue(playerId)
        processNextFlightCharge(playerId)
    }

    private fun processNextFlightCharge(playerId: UUID) {
        if (!acceptingOperations) {
            chargeLedger.clear(playerId)
            chargeInProgress.remove(playerId)
            return
        }
        if (!chargeInProgress.add(playerId)) {
            return
        }
        if (!chargeLedger.poll(playerId)) {
            chargeInProgress.remove(playerId)
            if (chargeLedger.hasPending(playerId)) {
                processNextFlightCharge(playerId)
            }
            return
        }

        val withdrawal = runCatching {
            ServiceEconomy.withdraw(playerId, FLIGHT_CHECK_COST)
                .orTimeout(WITHDRAWAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrElse { error ->
            handlePaymentFailure(playerId, null, error)
            return
        }
        withdrawal.whenComplete { result, error ->
            if (error == null && result?.status == EconomyWithdrawalStatus.SUCCESS) {
                chargeInProgress.remove(playerId)
                processNextFlightCharge(playerId)
            } else {
                handlePaymentFailure(playerId, result, error)
            }
        }
    }

    private fun handlePaymentFailure(
        playerId: UUID,
        withdrawal: EconomyWithdrawalResult?,
        error: Throwable?,
    ) {
        chargeLedger.clear(playerId)
        if (!acceptingOperations) {
            chargeInProgress.remove(playerId)
            return
        }

        val forcedDisableToken = settingStateGuard.forceDisable(playerId)
        failClosedActiveSession(playerId, paymentFailureMessage(withdrawal))

        if (error != null || withdrawal?.status != EconomyWithdrawalStatus.INSUFFICIENT_FUNDS) {
            val details = mutableListOf(
                "飞行周期扣除弟弟币失败，已关闭玩家飞行功能。",
                "玩家 UUID: $playerId",
                "扣款状态: ${withdrawal?.status ?: "EXCEPTION"}",
            )
            error?.stackTraceToString()?.let(details::add)
            severe(*details.toTypedArray())
        }

        submitSettingOperation(playerId, SettingOperationType.MUTATION) {
            repository.setEnabled(playerId, false)
        }.whenComplete { versioned, persistenceError ->
            if (!acceptingOperations) {
                chargeInProgress.remove(playerId)
                return@whenComplete
            }

            val persisted = persistenceError == null && versioned?.value is RepositoryResult.Success
            if (persisted && settingStateGuard.isCurrentMutation(playerId, versioned.token)) {
                settingStateGuard.clearForcedDisable(playerId, forcedDisableToken)
                publishPreference(playerId, false)
            } else if (!persisted) {
                severe(
                    "飞行扣费失败后的关闭状态未能写入数据库，运行期将继续强制关闭。",
                    "玩家 UUID: $playerId",
                )
            }

            chargeInProgress.remove(playerId)
            if (chargeLedger.hasPending(playerId)) {
                processNextFlightCharge(playerId)
            }
        }
    }

    private fun failClosedActiveSession(playerId: UUID, message: String) {
        val active = activeSessions[playerId] ?: return
        preferences[active.key] = false
        active.player.foliaRun {
            if (activeSession(this) == active.key) {
                applyManagedFlightPreference(false)
                sendDZTError(message)
            }
        }
    }

    /** 异步刷新玩家的 `fly` 类型封禁，并将结果写入当前进程的运行时缓存。 */
    private fun refreshFlightBan(session: SessionKey): CompletableFuture<FlightBanLookup> {
        val refreshStartedAt = System.currentTimeMillis()
        return BanApi.getActiveBan(session.playerId, BanType.FLY).handle { lookup, error ->
            when {
                error != null || lookup == null || lookup is BanLookupResult.Unavailable -> {
                    FlightBanLookup.UNAVAILABLE
                }

                lookup is BanLookupResult.Active -> {
                    applyFlightBan(lookup.record)
                    FlightBanLookup.RESTRICTED
                }

                lookup is BanLookupResult.NotBanned -> {
                    val cached = flightBans.computeIfPresent(session.playerId) { _, current ->
                        current.takeIf { it.bannedAt >= refreshStartedAt }
                    }
                    if (cached?.isEffectiveAt(System.currentTimeMillis()) == true) {
                        FlightBanLookup.RESTRICTED
                    } else {
                        FlightBanLookup.CLEAR
                    }
                }

                else -> FlightBanLookup.UNAVAILABLE
            }
        }
    }

    /** 判断当前进程缓存中是否存在尚未到期的飞行封禁。 */
    private fun hasActiveFlightBan(playerId: UUID): Boolean {
        return flightBans[playerId]?.isEffectiveAt(System.currentTimeMillis()) == true
    }

    /** 缓存同一玩家较新的飞行封禁，防止旧异步查询结果覆盖新记录。 */
    private fun cacheFlightBan(ban: PlayerBan) {
        flightBans.compute(ban.playerId) { _, current ->
            when {
                current == null -> ban
                ban.bannedAt > current.bannedAt -> ban
                ban.bannedAt < current.bannedAt -> current
                ban.recordId.toString() >= current.recordId.toString() -> ban
                else -> current
            }
        }
    }

    /** 到期后移除飞行封禁缓存，并为仍在线的玩家恢复其持久化飞行设置。 */
    private fun reconcileExpiredFlightBans() {
        val now = System.currentTimeMillis()
        flightBans.forEach { (playerId, ban) ->
            if (!ban.isEffectiveAt(now) && flightBans.remove(playerId, ban)) {
                activeSessions[playerId]?.key?.let(::refreshPreference)
            }
        }
    }

    private fun paymentFailureMessage(withdrawal: EconomyWithdrawalResult?): String {
        if (withdrawal?.status == EconomyWithdrawalStatus.INSUFFICIENT_FUNDS) {
            val balance = withdrawal.balance?.let(ServiceEconomy::formatAmount)
            return if (balance == null) {
                "弟弟币余额不足，飞行功能已自动关闭。"
            } else {
                "弟弟币余额不足，飞行功能已自动关闭。当前余额 $balance DDB。"
            }
        }
        return "弟弟币扣款失败，飞行功能已自动关闭，请稍后重新开启。"
    }

    private fun <T> submitSettingOperation(
        playerId: UUID,
        type: SettingOperationType,
        block: () -> T,
    ): CompletableFuture<VersionedSettingResult<T>> {
        val result = AtomicReference<VersionedSettingResult<T>>()
        val queue = synchronized(settingLifecycleLock) {
            if (!acceptingOperations) {
                null
            } else {
                settingQueues.computeIfAbsent(playerId) { SerialTaskQueue() }
            }
        } ?: return CompletableFuture.failedFuture(
            RejectedExecutionException("飞行设置服务已停止")
        )
        return queue.submit {
            val token = when (type) {
                SettingOperationType.READ -> settingStateGuard.beginRead(playerId)
                SettingOperationType.MUTATION -> settingStateGuard.beginMutation(playerId)
            }
            result.set(VersionedSettingResult(token, block()))
        }.thenApply {
            checkNotNull(result.get()) { "飞行设置队列完成但未产生操作结果" }
        }
    }

    /**
     * TabooLib 没有玩家飞行能力的等价接口；这里必须使用 Paper 的 Player 能力属性，
     * 并且调用方保证当前位于该玩家的 Folia 实体线程。
     */
    private fun Player.applyManagedFlightPreference(enabled: Boolean) {
        if (!FlightPolicy.managesAllowFlight(gameMode.toFlightGameMode())) {
            return
        }
        if (!enabled && isFlying) {
            isFlying = false
        }
        allowFlight = enabled
    }

    private fun GameMode.toFlightGameMode(): FlightGameMode {
        return when (this) {
            GameMode.SURVIVAL -> FlightGameMode.SURVIVAL
            GameMode.ADVENTURE -> FlightGameMode.ADVENTURE
            GameMode.CREATIVE -> FlightGameMode.CREATIVE
            GameMode.SPECTATOR -> FlightGameMode.SPECTATOR
        }
    }
}
