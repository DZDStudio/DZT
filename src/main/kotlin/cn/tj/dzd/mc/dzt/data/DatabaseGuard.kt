package cn.tj.dzd.mc.dzt.data

import cn.tj.dzd.mc.dzt.util.DZTMessageType
import cn.tj.dzd.mc.dzt.util.dztMessage
import cn.tj.dzd.mc.dzt.util.foliaKick
import org.bukkit.Bukkit
import taboolib.common.platform.function.severe
import taboolib.common.platform.function.submit

/**
 * 数据库操作保护工具。
 *
 * 统一捕获数据库调用异常，并在数据库不可用时通知玩家、关闭服务器。
 */
object DatabaseGuard {
    private const val SHUTDOWN_DELAY_TICKS = 20L
    private val PLAYER_KICK_MESSAGE = dztMessage(
        "数据库连接异常，服务器正在关闭。\n请联系运维人员！",
        DZTMessageType.ERROR
    )

    private val shutdownLock = Any()

    @Volatile
    private var shutdownScheduled = false

    /**
     * 执行数据库操作。
     *
     * @param action 当前数据库操作名称，用于控制台日志定位。
     * @param fallback 数据库操作失败时返回的兜底值。
     * @param block 实际执行的数据库操作。
     * @return 数据库操作结果，失败时返回 [fallback]。
     */
    fun <T> execute(action: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (ex: Exception) {
            handleDatabaseException(action, ex)
            fallback
        }
    }

    /**
     * 执行无返回值的数据库操作。
     *
     * @param action 当前数据库操作名称，用于控制台日志定位。
     * @param block 实际执行的数据库操作。
     * @return 操作是否执行成功。
     */
    fun execute(action: String, block: () -> Unit): Boolean {
        return execute(action, false) {
            block()
            true
        }
    }

    private fun handleDatabaseException(action: String, exception: Exception) {
        val shouldShutdown = synchronized(shutdownLock) {
            if (shutdownScheduled) {
                false
            } else {
                shutdownScheduled = true
                true
            }
        }

        val message = exception.message ?: "无异常信息"
        if (!shouldShutdown) {
            severe("数据库操作失败，服务器已在关闭流程中。操作: $action，异常: ${exception.javaClass.name}: $message")
            return
        }

        severe(
            "数据库操作失败，服务器即将关闭。",
            "操作: $action",
            "异常: ${exception.javaClass.name}: $message",
            exception.stackTraceToString()
        )
        kickPlayersAndShutdown()
    }

    private fun kickPlayersAndShutdown() {
        submit {
            Bukkit.getOnlinePlayers().forEach { player ->
                player.foliaKick(PLAYER_KICK_MESSAGE)
            }
            submit(delay = SHUTDOWN_DELAY_TICKS) {
                Bukkit.shutdown()
            }
        }
    }
}
