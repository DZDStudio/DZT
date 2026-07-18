package cn.tj.dzd.mc.dzt.title

/**
 * 对外展示的玩家称号。
 *
 * @param id 称号的稳定唯一标识。
 * @param displayName 称号显示名，支持 `§` 颜色代码。
 * @param description 称号介绍。
 * @param acquiredAt 称号授予玩家时的 Unix 毫秒时间戳。
 * @param equipped 玩家当前是否正在佩戴该称号。
 */
data class PlayerTitle(
    val id: String,
    val displayName: String,
    val description: String,
    val acquiredAt: Long,
    val equipped: Boolean,
) {
    /** 称号授予时间戳，与 [acquiredAt] 为同一值。 */
    val grantedAt: Long
        get() = acquiredAt
}

/** 授予称号的处理结果。 */
enum class TitleGrantResult {
    GRANTED,
    ALREADY_OWNED,
    FAILED,
}

/** 移除玩家称号的处理结果。 */
enum class TitleRevokeResult {
    REVOKED,
    NOT_OWNED,
    FAILED,
}

/** 更改玩家佩戴称号的处理结果。 */
enum class TitleEquipResult {
    EQUIPPED,
    UNEQUIPPED,
    ALREADY_EQUIPPED,
    NOT_OWNED,
    FAILED,
}
