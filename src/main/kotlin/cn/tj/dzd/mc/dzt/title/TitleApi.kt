package cn.tj.dzd.mc.dzt.title

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 称号系统对外 API。
 *
 * 所有方法均在异步线程访问数据库，调用方可通过返回的 [CompletableFuture] 处理结果。
 */
object TitleApi {

    /**
     * 授予玩家一个称号。
     *
     * 称号不会被自动佩戴，成功授予时会自动记录当前 Unix 毫秒时间戳。
     * 非法参数会令 Future 异常完成，数据库失败则返回 [TitleGrantResult.FAILED]。
     *
     * @param playerUuid 获得称号的玩家 UUID。
     * @param titleId 稳定的称号 ID，允许小写字母、数字、点、下划线与连字符。
     * @param displayName 称号显示名，支持 `§` 颜色代码。
     * @param description 称号介绍，最多 256 个字符。
     * @return 异步授予结果。
     */
    @JvmStatic
    fun grantTitle(
        playerUuid: UUID,
        titleId: String,
        displayName: String,
        description: String,
    ): CompletableFuture<TitleGrantResult> {
        return CompletableFuture.supplyAsync {
            TitleService.grantTitle(playerUuid, titleId, displayName, description)
        }
    }

    /**
     * 授予玩家一个无介绍的称号。
     *
     * 保留该重载用于兼容已有调用方。
     *
     * @param playerUuid 获得称号的玩家 UUID。
     * @param titleId 稳定的称号 ID。
     * @param displayName 称号显示名。
     * @return 异步授予结果。
     */
    @JvmStatic
    fun grantTitle(
        playerUuid: UUID,
        titleId: String,
        displayName: String,
    ): CompletableFuture<TitleGrantResult> {
        return grantTitle(playerUuid, titleId, displayName, "")
    }

    /**
     * 授予在线玩家一个称号。
     *
     * @param player 获得称号的玩家。
     * @param titleId 稳定的称号 ID。
     * @param displayName 称号显示名，支持 `§` 颜色代码。
     * @param description 称号介绍，最多 256 个字符。
     * @return 异步授予结果。
     */
    @JvmStatic
    fun grantTitle(
        player: Player,
        titleId: String,
        displayName: String,
        description: String,
    ): CompletableFuture<TitleGrantResult> {
        return grantTitle(player.uniqueId, titleId, displayName, description)
    }

    /**
     * 授予在线玩家一个无介绍的称号。
     *
     * @param player 获得称号的玩家。
     * @param titleId 稳定的称号 ID。
     * @param displayName 称号显示名。
     * @return 异步授予结果。
     */
    @JvmStatic
    fun grantTitle(
        player: Player,
        titleId: String,
        displayName: String,
    ): CompletableFuture<TitleGrantResult> {
        return grantTitle(player.uniqueId, titleId, displayName, "")
    }

    /**
     * 移除玩家已拥有的称号。
     *
     * @param playerUuid 玩家 UUID。
     * @param titleId 需要移除的称号 ID。
     * @return 异步移除结果。
     */
    @JvmStatic
    fun revokeTitle(
        playerUuid: UUID,
        titleId: String,
    ): CompletableFuture<TitleRevokeResult> {
        return CompletableFuture.supplyAsync {
            TitleService.revokeTitle(playerUuid, titleId)
        }
    }

    /**
     * 移除在线玩家已拥有的称号。
     *
     * @param player 玩家。
     * @param titleId 需要移除的称号 ID。
     * @return 异步移除结果。
     */
    @JvmStatic
    fun revokeTitle(
        player: Player,
        titleId: String,
    ): CompletableFuture<TitleRevokeResult> {
        return revokeTitle(player.uniqueId, titleId)
    }

    /**
     * 异步读取玩家已拥有的称号。
     *
     * @param playerUuid 玩家 UUID。
     * @return 按获得时间正序排列的称号列表。
     */
    @JvmStatic
    fun getOwnedTitles(playerUuid: UUID): CompletableFuture<List<PlayerTitle>> {
        return CompletableFuture.supplyAsync {
            TitleService.getOwnedTitles(playerUuid)
        }
    }

    /**
     * 异步读取玩家当前佩戴的称号。
     *
     * @param playerUuid 玩家 UUID。
     * @return 当前称号，未佩戴时以 null 完成。
     */
    @JvmStatic
    fun getEquippedTitle(playerUuid: UUID): CompletableFuture<PlayerTitle?> {
        return CompletableFuture.supplyAsync {
            TitleService.getEquippedTitle(playerUuid)
        }
    }
}
