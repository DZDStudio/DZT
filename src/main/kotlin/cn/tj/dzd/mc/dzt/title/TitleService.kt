package cn.tj.dzd.mc.dzt.title

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.PlayerTitleRecord
import cn.tj.dzd.mc.dzt.data.table.playerTitleRecordMapper
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 称号的数据库服务与聊天快照缓存。
 *
 * 数据库方法均为同步方法，调用方应在异步线程中执行；对外接口请使用 [TitleApi]。
 */
internal object TitleService {

    private const val MAX_TITLE_ID_LENGTH = 64
    private const val MAX_DISPLAY_NAME_LENGTH = 64
    private const val MAX_DESCRIPTION_LENGTH = 256
    private val validTitleId = Regex("[a-z0-9._-]+")

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
        val normalizedId = normalizeTitleId(titleId)
        val normalizedDisplayName = normalizeDisplayName(displayName)
        val normalizedDescription = normalizeDescription(description)
        val recordId = recordId(uuid, normalizedId)

        return DatabaseGuard.execute("授予称号", TitleGrantResult.FAILED) {
            if (playerTitleRecordMapper.findById(recordId) != null) {
                return@execute TitleGrantResult.ALREADY_OWNED
            }

            playerTitleRecordMapper.insert(
                PlayerTitleRecord(
                    recordId = recordId,
                    uuid = uuid,
                    titleId = normalizedId,
                    displayName = normalizedDisplayName,
                    description = normalizedDescription,
                    acquiredAt = System.currentTimeMillis(),
                    equipped = false,
                )
            )
            TitleGrantResult.GRANTED
        }
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
        val normalizedId = normalizeTitleId(titleId)
        val targetRecordId = recordId(uuid, normalizedId)
        val result = DatabaseGuard.execute("移除玩家称号", TitleRevokeResult.FAILED) {
            playerTitleRecordMapper.transaction {
                findById(targetRecordId) ?: return@transaction TitleRevokeResult.NOT_OWNED
                deleteById(targetRecordId)
                TitleRevokeResult.REVOKED
            }.getOrThrow()
        }

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
        return DatabaseGuard.execute("读取玩家称号", emptyList()) {
            playerTitleRecordMapper.findAll {
                "uuid" eq uuid.toString()
            }.sortedBy { it.acquiredAt }
                .map { it.toPlayerTitle() }
        }
    }

    /**
     * 读取玩家当前佩戴的称号。
     *
     * @param uuid 玩家 UUID。
     * @return 当前称号，未佩戴时返回 null。
     */
    fun getEquippedTitle(uuid: UUID): PlayerTitle? {
        return DatabaseGuard.execute<PlayerTitle?>("读取已佩戴称号", null) {
            playerTitleRecordMapper.findOne {
                "uuid" eq uuid.toString()
                "equipped" eq true
            }?.toPlayerTitle()
        }
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
        val normalizedId = normalizeTitleId(titleId)
        val result = DatabaseGuard.execute("佩戴称号", TitleEquipResult.FAILED) {
            playerTitleRecordMapper.transaction {
                val selected = findById(recordId(uuid, normalizedId))
                    ?: return@transaction TitleEquipResult.NOT_OWNED
                if (selected.equipped) {
                    return@transaction TitleEquipResult.ALREADY_EQUIPPED
                }

                rawUpdate {
                    set("equipped", false)
                    where {
                        "uuid" eq uuid.toString()
                    }
                }
                selected.equipped = true
                update(selected)
                TitleEquipResult.EQUIPPED
            }.getOrThrow()
        }

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
        val result = DatabaseGuard.execute("取消佩戴称号", TitleEquipResult.FAILED) {
            playerTitleRecordMapper.transaction {
                rawUpdate {
                    set("equipped", false)
                    where {
                        "uuid" eq uuid.toString()
                    }
                }
                TitleEquipResult.UNEQUIPPED
            }.getOrThrow()
        }

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
        Bukkit.getOnlinePlayers().forEach(::refreshEquippedTitleCache)
    }

    @Awake(LifeCycle.DISABLE)
    fun clearCaches() {
        equippedTitleCache.clear()
        cacheLoadTokens.clear()
    }

    @SubscribeEvent
    fun onPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val uuid = event.uniqueId
        updateEquippedTitleCache(uuid, getEquippedTitle(uuid), createIfAbsent = true)
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

        CompletableFuture.supplyAsync {
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

    private fun normalizeTitleId(titleId: String): String {
        val normalized = titleId.trim().lowercase(Locale.ROOT)
        require(normalized.isNotEmpty()) { "称号 ID 不能为空" }
        require(normalized.length <= MAX_TITLE_ID_LENGTH) {
            "称号 ID 长度不能超过 $MAX_TITLE_ID_LENGTH 个字符"
        }
        require(validTitleId.matches(normalized)) {
            "称号 ID 只能包含小写字母、数字、点、下划线与连字符"
        }
        return normalized
    }

    private fun normalizeDisplayName(displayName: String): String {
        val normalized = displayName.trim()
        require(normalized.isNotEmpty()) { "称号显示名不能为空" }
        require(normalized.length <= MAX_DISPLAY_NAME_LENGTH) {
            "称号显示名长度不能超过 $MAX_DISPLAY_NAME_LENGTH 个字符"
        }
        require('\n' !in normalized && '\r' !in normalized) { "称号显示名不能包含换行" }
        return normalized
    }

    private fun normalizeDescription(description: String): String {
        val normalized = description
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        require(normalized.length <= MAX_DESCRIPTION_LENGTH) {
            "称号介绍长度不能超过 $MAX_DESCRIPTION_LENGTH 个字符"
        }
        require(normalized.none { it.isISOControl() && it != '\n' && it != '\t' }) {
            "称号介绍包含不可用的控制字符"
        }
        return normalized
    }

    private fun recordId(uuid: UUID, titleId: String): String {
        return "$uuid:$titleId"
    }

    private fun PlayerTitleRecord.toPlayerTitle(): PlayerTitle {
        return PlayerTitle(titleId, displayName, description, acquiredAt, equipped)
    }
}
