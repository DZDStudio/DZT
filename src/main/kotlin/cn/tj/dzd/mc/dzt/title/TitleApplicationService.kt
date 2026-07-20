package cn.tj.dzd.mc.dzt.title

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID

/**
 * 称号用例服务。
 *
 * @param repository 称号持久化端口。
 * @param currentTimeMillis 授予时间来源，测试时可注入固定时间。
 */
class TitleApplicationService(
    private val repository: TitleRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {

    /**
     * 授予称号。
     *
     * @param ownerId 玩家 UUID。
     * @param titleId 称号稳定 ID。
     * @param displayName 称号显示名。
     * @param description 称号介绍。
     * @return 授予结果。
     */
    fun grant(
        ownerId: UUID,
        titleId: String,
        displayName: String,
        description: String,
    ): TitleGrantResult {
        val title = PlayerTitle(
            id = TitlePolicy.normalizeId(titleId),
            displayName = TitlePolicy.normalizeDisplayName(displayName),
            description = TitlePolicy.normalizeDescription(description),
            acquiredAt = currentTimeMillis(),
            equipped = false,
        )
        return when (val result = repository.insertIfAbsent(ownerId, title)) {
            is RepositoryResult.Success -> if (result.value) TitleGrantResult.GRANTED else TitleGrantResult.ALREADY_OWNED
            RepositoryResult.Failure -> TitleGrantResult.FAILED
        }
    }

    /**
     * 移除称号。
     *
     * @param ownerId 玩家 UUID。
     * @param titleId 称号 ID。
     * @return 移除结果。
     */
    fun revoke(ownerId: UUID, titleId: String): TitleRevokeResult {
        val normalizedId = TitlePolicy.normalizeId(titleId)
        return when (val result = repository.delete(ownerId, normalizedId)) {
            is RepositoryResult.Success -> if (result.value) TitleRevokeResult.REVOKED else TitleRevokeResult.NOT_OWNED
            RepositoryResult.Failure -> TitleRevokeResult.FAILED
        }
    }

    /**
     * 读取玩家拥有的称号，按授予时间正序返回。
     *
     * @param ownerId 玩家 UUID。
     * @return 查询成功时的称号列表；基础设施失败时返回空列表以兼容旧 API。
     */
    fun getOwned(ownerId: UUID): List<PlayerTitle> {
        return when (val result = repository.findAll(ownerId)) {
            is RepositoryResult.Success -> result.value.sortedBy(PlayerTitle::acquiredAt)
            RepositoryResult.Failure -> emptyList()
        }
    }

    /**
     * 读取玩家当前佩戴的称号。
     *
     * @param ownerId 玩家 UUID。
     * @return 当前称号；未佩戴或基础设施失败时返回 null。
     */
    fun getEquipped(ownerId: UUID): PlayerTitle? {
        return when (val result = repository.findEquipped(ownerId)) {
            is RepositoryResult.Success -> result.value
            RepositoryResult.Failure -> null
        }
    }

    /**
     * 佩戴称号。
     *
     * @param ownerId 玩家 UUID。
     * @param titleId 称号 ID。
     * @return 佩戴结果。
     */
    fun equip(ownerId: UUID, titleId: String): TitleEquipResult {
        val normalizedId = TitlePolicy.normalizeId(titleId)
        return when (val result = repository.equip(ownerId, normalizedId)) {
            is RepositoryResult.Success -> when (result.value) {
                TitleEquipDecision.EQUIPPED -> TitleEquipResult.EQUIPPED
                TitleEquipDecision.ALREADY_EQUIPPED -> TitleEquipResult.ALREADY_EQUIPPED
                TitleEquipDecision.NOT_OWNED -> TitleEquipResult.NOT_OWNED
            }
            RepositoryResult.Failure -> TitleEquipResult.FAILED
        }
    }

    /**
     * 取消佩戴称号。
     *
     * @param ownerId 玩家 UUID。
     * @return 取消佩戴结果。
     */
    fun unequip(ownerId: UUID): TitleEquipResult {
        return when (repository.unequip(ownerId)) {
            is RepositoryResult.Success -> TitleEquipResult.UNEQUIPPED
            RepositoryResult.Failure -> TitleEquipResult.FAILED
        }
    }
}
