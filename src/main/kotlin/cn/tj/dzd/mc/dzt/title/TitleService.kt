package cn.tj.dzd.mc.dzt.title

import cn.tj.dzd.mc.dzt.data.repository.PersistentTitleRepository
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.onlinePlayers
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 称号的数据库服务与聊天快照缓存。
 *
 * 数据库方法均为同步方法，调用方应在异步线程中执行；对外接口请使用 [TitleApi]。
 */
internal object TitleService {

    private val application = TitleApplicationService(PersistentTitleRepository)

    private val equippedTitleCache = ConcurrentHashMap<UUID, PlayerTitle>()
    private val cacheLoadTokens = ConcurrentHashMap<UUID, Long>()
    private val cacheTokenSequence = AtomicLong()

    /**
     * 授予玩家一个称号。
     *
     * 新称号不会自动佩戴，需由玩家在称号菜单中选择。
     *
     * @param uuid 玩家 UUID。
     * @param titleId 称号稳定 ID，允许小写字母、数字、点、下划线与连字符。
     * @param displayName 称号显示名，支持 `§` 颜色代码。
     * @param description 称号介绍，允许为空。
     * @return 授予结果。
     * @throws IllegalArgumentException 参数不合法时抛出。
     */
    fun grantTitle(
        uuid: UUID,
        titleId: String,
        displayName: String,
        description: String,
    ): TitleGrantResult {
        return application.grant(uuid, titleId, displayName, description)
    }

    /**
     * 移除玩家已拥有的称号。
     *
     * 如果该称号正在佩戴，移除后会同步刷新聊天称号缓存。
     *
     * @param uuid 玩家 UUID。
     * @param titleId 需要移除的称号 ID。
     * @return 移除结果。
     * @throws IllegalArgumentException 称号 ID 不合法时抛出。
     */
    fun revokeTitle(uuid: UUID, titleId: String): TitleRevokeResult {
        val result = application.revoke(uuid, titleId)

        if (result == TitleRevokeResult.REVOKED) {
            updateEquippedTitleCache(uuid, getEquippedTitle(uuid))
        }
        return result
    }

    /**
     * 读取玩家已拥有的所有称号。
     *
     * @param uuid 玩家 UUID。
     * @return 按获得时间正序排列的称号。
     */
    fun getOwnedTitles(uuid: UUID): List<PlayerTitle> {
        return application.getOwned(uuid)
    }

    /**
     * 读取玩家当前佩戴的称号。
     *
     * @param uuid 玩家 UUID。
     * @return 当前称号，未佩戴时返回 null。
     */
    fun getEquippedTitle(uuid: UUID): PlayerTitle? {
        return application.getEquipped(uuid)
    }

    /**
     * 佩戴玩家已拥有的称号。
     *
     * 先取消该玩家的其他称号，再佩戴指定称号，两步在同一个事务中完成。
     *
     * @param uuid 玩家 UUID。
     * @param titleId 需要佩戴的称号 ID。
     * @return 佩戴结果。
     */
    fun equipTitle(uuid: UUID, titleId: String): TitleEquipResult {
        val result = application.equip(uuid, titleId)

        when (result) {
            TitleEquipResult.EQUIPPED,
            TitleEquipResult.ALREADY_EQUIPPED -> updateEquippedTitleCache(uuid, getEquippedTitle(uuid))
            else -> Unit
        }
        return result
    }

    /**
     * 取消玩家当前佩戴的称号。
     *
     * @param uuid 玩家 UUID。
     * @return 取消佩戴结果。
     */
    fun unequipTitle(uuid: UUID): TitleEquipResult {
        val result = application.unequip(uuid)

        if (result == TitleEquipResult.UNEQUIPPED) {
            updateEquippedTitleCache(uuid, null)
        }
        return result
    }

    /**
     * 返回聊天渲染使用的非阻塞称号快照。
     */
    fun getCachedEquippedTitle(uuid: UUID): PlayerTitle? {
        return equippedTitleCache[uuid]
    }

    @Awake(LifeCycle.ACTIVE)
    fun loadOnlinePlayerCaches() {
        onlinePlayers.forEach(::refreshEquippedTitleCache)
    }

    @Awake(LifeCycle.DISABLE)
    fun clearCaches() {
        equippedTitleCache.clear()
        cacheLoadTokens.clear()
    }

    @SubscribeEvent
    fun onPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val uuid = event.uniqueId
        val title = runCatching {
            DztAsyncExecutor.supply { getEquippedTitle(uuid) }.join()
        }.getOrNull()
        updateEquippedTitleCache(uuid, title, createIfAbsent = true)
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        refreshEquippedTitleCache(event.player)
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        cacheLoadTokens.remove(uuid)
        equippedTitleCache.remove(uuid)
    }

    private fun refreshEquippedTitleCache(player: Player) {
        val uuid = player.uniqueId
        val token = cacheTokenSequence.incrementAndGet()
        cacheLoadTokens[uuid] = token

        DztAsyncExecutor.supply {
            getEquippedTitle(uuid)
        }.whenComplete { title, error ->
            if (error != null || cacheLoadTokens[uuid] != token) {
                return@whenComplete
            }
            if (title == null) {
                equippedTitleCache.remove(uuid)
            } else {
                equippedTitleCache[uuid] = title
            }
        }
    }

    private fun updateEquippedTitleCache(
        uuid: UUID,
        title: PlayerTitle?,
        createIfAbsent: Boolean = false,
    ) {
        if (!createIfAbsent && !cacheLoadTokens.containsKey(uuid)) {
            return
        }
        cacheLoadTokens[uuid] = cacheTokenSequence.incrementAndGet()
        if (title == null) {
            equippedTitleCache.remove(uuid)
        } else {
            equippedTitleCache[uuid] = title
        }
    }

}
