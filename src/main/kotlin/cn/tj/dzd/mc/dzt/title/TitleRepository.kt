package cn.tj.dzd.mc.dzt.title

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.util.UUID

/**
 * 称号持久化端口。
 *
 * 实现必须保证授予去重、移除以及佩戴状态切换各自在数据库事务内完成。
 */
interface TitleRepository {

    /**
     * 仅在玩家尚未拥有相同 ID 时写入称号。
     *
     * @param ownerId 玩家 UUID。
     * @param title 已标准化的称号。
     * @return true 表示新增，false 表示已拥有。
     */
    fun insertIfAbsent(ownerId: UUID, title: PlayerTitle): RepositoryResult<Boolean>

    /**
     * 移除玩家的指定称号。
     *
     * @param ownerId 玩家 UUID。
     * @param titleId 已标准化的称号 ID。
     * @return true 表示已移除，false 表示原本就未拥有。
     */
    fun delete(ownerId: UUID, titleId: String): RepositoryResult<Boolean>

    /**
     * 读取玩家拥有的全部称号。
     *
     * @param ownerId 玩家 UUID。
     * @return 存储操作结果。
     */
    fun findAll(ownerId: UUID): RepositoryResult<List<PlayerTitle>>

    /**
     * 读取玩家当前佩戴的称号。
     *
     * @param ownerId 玩家 UUID。
     * @return 未佩戴时成功值为 null。
     */
    fun findEquipped(ownerId: UUID): RepositoryResult<PlayerTitle?>

    /**
     * 取消旧称号并佩戴指定称号。
     *
     * @param ownerId 玩家 UUID。
     * @param titleId 已标准化的称号 ID。
     * @return 存储层佩戴决策。
     */
    fun equip(ownerId: UUID, titleId: String): RepositoryResult<TitleEquipDecision>

    /**
     * 取消玩家当前佩戴的称号。
     *
     * @param ownerId 玩家 UUID。
     * @return 存储操作结果。
     */
    fun unequip(ownerId: UUID): RepositoryResult<Unit>
}

/** 持久化层佩戴称号的原子决策。 */
enum class TitleEquipDecision {
    EQUIPPED,
    ALREADY_EQUIPPED,
    NOT_OWNED,
}
