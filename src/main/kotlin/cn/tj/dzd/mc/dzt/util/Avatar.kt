package cn.tj.dzd.mc.dzt.util

import org.bukkit.entity.Player

private const val MC_HEADS_MIRROR_URL = "https://heads-mc.dzd.tj.cn"

/**
 * 玩家头像来源类型。
 *
 * 用于区分 Java 玩家与 Floodgate 基岩玩家，便于头像服务选择正确的玩家标识。
 */
enum class PlayerAvatarType {
    /** Java 版玩家，使用 Bukkit 原始 UUID。 */
    JAVA,

    /** 基岩版玩家，使用 Floodgate 生成的 Java UUID。 */
    BEDROCK
}

/**
 * 玩家头像查询目标。
 *
 * @param type 玩家头像来源类型。
 * @param identifier 传给 MCHeads 的玩家标识。
 */
data class PlayerAvatarTarget(
    val type: PlayerAvatarType,
    val identifier: String,
)

/**
 * 获取玩家在 MCHeads 中使用的头像查询目标。
 *
 * Java 玩家使用 Bukkit UUID；基岩玩家使用 Floodgate 提供的 Java UUID，符合 MCHeads 对 Floodgate UUID 的支持。
 *
 * @return 包含玩家类型和头像查询标识的目标对象。
 */
fun Player.avatarTarget(): PlayerAvatarTarget {
    if (isBePlayer()) {
        val floodgateUuid = floodgateApi.getPlayer(uniqueId)?.javaUniqueId ?: uniqueId
        return PlayerAvatarTarget(PlayerAvatarType.BEDROCK, floodgateUuid.toString())
    }

    return PlayerAvatarTarget(PlayerAvatarType.JAVA, uniqueId.toString())
}

/**
 * 获取玩家在 MCHeads 镜像站的头像 URL。
 *
 * @return 可用于 Cumulus URL 图标的头像链接。
 */
fun Player.avatarUrl(): String {
    return avatarTarget().toAvatarUrl()
}

/**
 * 获取头像查询目标在 MCHeads 镜像站的头像 URL。
 *
 * @return 可用于 Cumulus URL 图标的头像链接。
 */
fun PlayerAvatarTarget.toAvatarUrl(): String {
    return "$MC_HEADS_MIRROR_URL/avatar/$identifier"
}
