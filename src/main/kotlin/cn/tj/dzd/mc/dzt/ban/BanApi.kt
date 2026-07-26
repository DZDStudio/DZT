package cn.tj.dzd.mc.dzt.ban

import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 玩家封禁系统的异步对外 API。
 *
 * 所有方法均在 DZT 异步执行器中访问数据库；调用方不应在 Folia 实体线程上等待返回的 Future。
 */
object BanApi {

    /**
     * 按小时封禁玩家。
     *
     * @param playerUuid 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，必须大于 0。
     * @param reason 封禁原因；空白原因会使用默认说明。
     * @return 异步封禁结果；参数不合法时 Future 会异常完成。
     */
    @JvmStatic
    fun ban(playerUuid: UUID, hours: Long, reason: String): CompletableFuture<BanIssueResult> {
        return DztAsyncExecutor.supply {
            BanService.ban(playerUuid, hours, reason)
        }
    }

    /**
     * 按小时创建指定类型的封禁。
     *
     * @param playerUuid 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，必须大于 0。
     * @param reason 封禁原因；空白原因会使用默认说明。
     * @param type 封禁作用域类型；[BanType.BAN] 会阻止登录，其余类型不会。
     * @return 异步封禁结果；参数不合法时 Future 会异常完成。
     */
    @JvmStatic
    fun ban(
        playerUuid: UUID,
        hours: Long,
        reason: String,
        type: BanType,
    ): CompletableFuture<BanIssueResult> {
        return DztAsyncExecutor.supply {
            BanService.ban(playerUuid, hours, reason, type)
        }
    }

    /**
     * 按小时封禁玩家，支持小数时长。
     *
     * @param playerUuid 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，可使用小数，必须大于 0。
     * @param reason 封禁原因；空白原因会使用默认说明。
     * @return 异步封禁结果；参数不合法时 Future 会异常完成。
     */
    @JvmStatic
    fun ban(playerUuid: UUID, hours: BigDecimal, reason: String): CompletableFuture<BanIssueResult> {
        return DztAsyncExecutor.supply {
            BanService.ban(playerUuid, hours, reason)
        }
    }

    /**
     * 按小时创建指定类型的封禁，支持小数时长。
     *
     * @param playerUuid 被封禁玩家 UUID。
     * @param hours 封禁时长，单位为小时，可使用小数，必须大于 0。
     * @param reason 封禁原因；空白原因会使用默认说明。
     * @param type 封禁作用域类型；[BanType.BAN] 会阻止登录，其余类型不会。
     * @return 异步封禁结果；参数不合法时 Future 会异常完成。
     */
    @JvmStatic
    fun ban(
        playerUuid: UUID,
        hours: BigDecimal,
        reason: String,
        type: BanType,
    ): CompletableFuture<BanIssueResult> {
        return DztAsyncExecutor.supply {
            BanService.ban(playerUuid, hours, reason, type)
        }
    }

    /**
     * 提前解除玩家当前有效封禁。
     *
     * @param playerUuid 被解除玩家 UUID。
     * @return 异步解封结果；历史记录不会删除。
     */
    @JvmStatic
    fun unban(playerUuid: UUID): CompletableFuture<UnbanResult> {
        return DztAsyncExecutor.supply {
            BanService.unban(playerUuid)
        }
    }

    /**
     * 提前解除玩家指定类型的当前有效封禁。
     *
     * @param playerUuid 被解除玩家 UUID。
     * @param type 需要解除的封禁类型。
     * @return 异步解封结果；历史记录不会删除。
     */
    @JvmStatic
    fun unban(playerUuid: UUID, type: BanType): CompletableFuture<UnbanResult> {
        return DztAsyncExecutor.supply {
            BanService.unban(playerUuid, type)
        }
    }

    /**
     * 异步查询玩家当前有效封禁。
     *
     * @param playerUuid 玩家 UUID。
     * @return 异步查询结果。
     */
    @JvmStatic
    fun getActiveBan(playerUuid: UUID): CompletableFuture<BanLookupResult> {
        return DztAsyncExecutor.supply {
            BanService.getActiveBan(playerUuid)
        }
    }

    /**
     * 异步查询玩家指定类型的当前有效封禁。
     *
     * @param playerUuid 玩家 UUID。
     * @param type 需要查询的封禁类型。
     * @return 异步查询结果。
     */
    @JvmStatic
    fun getActiveBan(playerUuid: UUID, type: BanType): CompletableFuture<BanLookupResult> {
        return DztAsyncExecutor.supply {
            BanService.getActiveBan(playerUuid, type)
        }
    }

    /**
     * 异步查询玩家的全部封禁历史。
     *
     * @param playerUuid 玩家 UUID。
     * @return 按封禁时间倒序排列的异步历史查询结果。
     */
    @JvmStatic
    fun getBanHistory(playerUuid: UUID): CompletableFuture<BanHistoryResult> {
        return DztAsyncExecutor.supply {
            BanService.getHistory(playerUuid)
        }
    }
}
