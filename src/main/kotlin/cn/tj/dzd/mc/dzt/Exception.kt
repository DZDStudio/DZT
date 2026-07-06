package cn.tj.dzd.mc.dzt

import taboolib.common.platform.function.severe
import java.util.UUID

/**
 * DZT 错误
 * @param userMessage 用户可见的错误信息
 * @param consoleMessage 错误信息
 */
open class DztException(
    val userMessage: String,
    val consoleMessage: String
) : Exception(consoleMessage) {
    init {
        severe(consoleMessage)
    }
}

/**
 * 数据库异常
 */
class DztDataBaseException: DztException("非常抱歉，我们的数据库似乎炸了，请联系运维人员！", "数据库异常")

/**
 * 数据库异常
 */
class DztNotUserException(
    val uid: Number = -1
): DztException("没你的事", "该用户[UID:${uid}]不存在")

/**
 * 玩家离线
 * @param uid DZD 用户唯一标识符
 */
class DztPlayerOfflineException(
    val uid: Number = -1
): DztException("没你的事", "玩家[UID:${uid}]已离线")

/**
 * 不是 Floodgate 玩家
 * @param uid DZD 用户唯一标识符
 */
class DztNotFloodgatePlayerException(
    val uid: Number = -1
): DztException("DZT似乎将您判定为基岩客户端玩家，这导致了逻辑异常，请您联系运维人员报告BUG，谢谢！", "玩家[UID:${uid}]不是 Floodgate 玩家")

/**
 * 获取用户标识符失败
 * @param name 玩家名称
 * @param uniqueId 玩家游戏唯一标识符
 */
class DztGetUserIdentifierException(
    val name: String = "NULL",
    val uniqueId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
): DztException("没你的事", "获取玩家[名称:${name},游戏唯一标识符:${uniqueId}]的 DZD 用户唯一标识符失败")

/**
 * 检索用户标识符失败
 * @param name 玩家名称
 * @param uniqueId 玩家游戏唯一标识符
 */
class DztSearchUserIdentifierException(
    val name: String = "NULL",
    val uniqueId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
): DztException("没你的事", "检索玩家[名称:${name},游戏唯一标识符:${uniqueId}]的 DZD 用户唯一标识符失败")