package cn.tj.dzd.mc.dzt.ban

import java.util.Locale

/**
 * 封禁记录的作用域类型。
 *
 * `ban` 表示禁止进入服务器；`fly` 表示禁止使用飞行功能。数据库中已有记录的空类型会被视为
 * `ban`，以保持历史封禁的登录拦截语义。除 `ban` 外的其他有效类型均不会阻止玩家进入服务器。
 *
 * @property value 持久化到 `player_ban_record.type` 的规范化类型值。
 */
@JvmInline
value class BanType private constructor(val value: String) {

    /** 此类型是否阻止玩家登录服务器。 */
    val blocksServerEntry: Boolean
        get() = this == BAN

    /** 此类型是否禁止玩家使用飞行功能。 */
    val blocksFlight: Boolean
        get() = this == FLY

    override fun toString(): String = value

    companion object {
        /** 禁止进入服务器的默认封禁类型。 */
        val BAN = BanType("ban")

        /** 禁止使用飞行功能的封禁类型。 */
        val FLY = BanType("fly")

        private val COMMAND_VALUE = Regex("[a-z][a-z0-9_-]{0,15}")

        /**
         * 解析管理命令传入的封禁类型。
         *
         * 类型仅允许 1 到 16 个小写 ASCII 字符、数字、下划线或连字符，且必须以字母开头。
         *
         * @param input 管理命令中的原始类型参数。
         * @return 规范化类型；格式不合法时返回 `null`。
         */
        fun fromCommand(input: String): BanType? {
            val normalized = input.trim().lowercase(Locale.ROOT)
            return normalized.takeIf(COMMAND_VALUE::matches)?.let(::BanType)
        }

        /**
         * 将数据库类型值转换为领域类型。
         *
         * 旧记录没有 `type` 列值时会转换为 [BAN]。非空的未知类型会被原样保留，因此它们不会
         * 被误判为服务器登录封禁。
         *
         * @param input `player_ban_record.type` 读取到的可空值。
         * @return 可用于权限判断的封禁类型。
         */
        fun fromStored(input: String?): BanType {
            val normalized = input?.trim()?.lowercase(Locale.ROOT)
            return normalized?.takeIf(String::isNotEmpty)?.let(::BanType) ?: BAN
        }
    }
}
