package cn.tj.dzd.mc.dzt.ban

import cn.tj.dzd.mc.dzt.data.repository.PersistentBanRepository
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.util.kickOnlinePlayer
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 玩家封禁的运行时服务与登录强制执行点。
 *
 * 数据库读写方法本身是同步的；外部调用应使用 [BanApi]。预登录事件是 Paper 异步生命周期事件，必须在事件返回前
 * 完成判断，才能保留拒绝登录的精确语义，因此在此处进行有界等待。
 */
internal object BanService {

    private const val PRE_LOGIN_LOOKUP_TIMEOUT_SECONDS = 5L
    private val application = BanApplicationService(PersistentBanRepository)

    /**
     * 同步执行封禁用例。
     *
     * @param playerId 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时。
     * @param reason 封禁原因。
     * @return 封禁结果。
     */
    fun ban(playerId: UUID, hours: Long, reason: String): BanIssueResult {
        return application.ban(playerId, hours, reason)
    }

    /**
     * 同步执行支持小数时长的封禁用例。
     *
     * @param playerId 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，可使用小数。
     * @param reason 封禁原因。
     * @return 封禁结果。
     */
    fun ban(playerId: UUID, hours: BigDecimal, reason: String): BanIssueResult {
        return application.ban(playerId, hours, reason)
    }

    /**
     * 同步执行解封用例。
     *
     * @param playerId 被解除玩家 UUID。
     * @return 解封结果。
     */
    fun unban(playerId: UUID): UnbanResult {
        return application.unban(playerId)
    }

    /**
     * 同步查询玩家当前封禁状态。
     *
     * @param playerId 玩家 UUID。
     * @return 当前封禁查询结果。
     */
    fun getActiveBan(playerId: UUID): BanLookupResult {
        return application.getActiveBan(playerId)
    }

    /**
     * 同步查询玩家封禁历史。
     *
     * @param playerId 玩家 UUID。
     * @return 封禁历史查询结果。
     */
    fun getHistory(playerId: UUID): BanHistoryResult {
        return application.getHistory(playerId)
    }

    /**
     * 断开仍在线的被封禁玩家。
     *
     * @param ban 已成功写入的封禁记录。
     */
    fun disconnectOnlinePlayer(ban: PlayerBan) {
        kickOnlinePlayer(ban.playerId, BanText.loginDeniedMessage(ban))
    }

    /**
     * 在真正加入前拒绝当前有效封禁的玩家。
     *
     * [AsyncPlayerPreLoginEvent] 是唯一能在玩家加入前拒绝登录的精确 Paper 事件，TabooLib 没有可替代且保留
     * 该语义的事件。该事件本身运行在异步登录线程，因而只能访问 UUID 与数据库，不能触碰 Player 实体。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return
        }

        when (val lookup = lookupForPreLogin(event.uniqueId)) {
            is BanLookupResult.Active -> event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                BanText.loginDeniedMessage(lookup.record),
            )

            BanLookupResult.Unavailable -> event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                BanText.storageUnavailableMessage(),
            )

            BanLookupResult.NotBanned -> Unit
        }
    }

    /**
     * 在玩家加入后异步复核一次封禁状态。
     *
     * 这是预登录查询与管理员封禁写入之间极小竞争窗口的补偿措施，不能替代 [onPlayerPreLogin] 的主拦截。
     */
    @SubscribeEvent(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerId = event.player.uniqueId
        DztAsyncExecutor.supply {
            getActiveBan(playerId)
        }.whenComplete { lookup, error ->
            if (error == null && lookup is BanLookupResult.Active) {
                disconnectOnlinePlayer(lookup.record)
            }
        }
    }

    private fun lookupForPreLogin(playerId: UUID): BanLookupResult {
        val future = DztAsyncExecutor.supply {
            getActiveBan(playerId)
        }
        return try {
            future.get(PRE_LOGIN_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            BanLookupResult.Unavailable
        } catch (_: Exception) {
            future.cancel(true)
            BanLookupResult.Unavailable
        }
    }
}
