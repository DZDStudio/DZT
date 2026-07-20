package cn.tj.dzd.mc.dzt.title

import java.util.Locale

/**
 * 称号字段的纯业务校验与标准化规则。
 */
object TitlePolicy {

    /**
     * 标准化称号 ID。
     *
     * @param titleId 原始 ID。
     * @return 小写并去除首尾空白的 ID。
     * @throws IllegalArgumentException ID 为空、过长或包含非法字符时抛出。
     */
    fun normalizeId(titleId: String): String {
        val normalized = titleId.trim().lowercase(Locale.ROOT)
        require(normalized.isNotEmpty()) { "称号 ID 不能为空" }
        require(normalized.length <= MAX_ID_LENGTH) { "称号 ID 长度不能超过 $MAX_ID_LENGTH 个字符" }
        require(VALID_ID.matches(normalized)) { "称号 ID 只能包含小写字母、数字、点、下划线与连字符" }
        return normalized
    }

    /**
     * 标准化称号显示名。
     *
     * @param displayName 原始显示名。
     * @return 去除首尾空白的显示名。
     * @throws IllegalArgumentException 显示名为空、过长或包含换行时抛出。
     */
    fun normalizeDisplayName(displayName: String): String {
        val normalized = displayName.trim()
        require(normalized.isNotEmpty()) { "称号显示名不能为空" }
        require(normalized.length <= MAX_DISPLAY_NAME_LENGTH) {
            "称号显示名长度不能超过 $MAX_DISPLAY_NAME_LENGTH 个字符"
        }
        require('\n' !in normalized && '\r' !in normalized) { "称号显示名不能包含换行" }
        return normalized
    }

    /**
     * 标准化称号介绍的换行和首尾空白。
     *
     * @param description 原始介绍。
     * @return 使用 LF 换行的介绍。
     * @throws IllegalArgumentException 介绍过长或包含不可用控制字符时抛出。
     */
    fun normalizeDescription(description: String): String {
        val normalized = description.replace("\r\n", "\n").replace('\r', '\n').trim()
        require(normalized.length <= MAX_DESCRIPTION_LENGTH) {
            "称号介绍长度不能超过 $MAX_DESCRIPTION_LENGTH 个字符"
        }
        require(normalized.none { it.isISOControl() && it != '\n' && it != '\t' }) {
            "称号介绍包含不可用的控制字符"
        }
        return normalized
    }

    /** 称号 ID 最大长度。 */
    const val MAX_ID_LENGTH = 64

    /** 称号显示名最大长度。 */
    const val MAX_DISPLAY_NAME_LENGTH = 64

    /** 称号介绍最大长度。 */
    const val MAX_DESCRIPTION_LENGTH = 256

    private val VALID_ID = Regex("[a-z0-9._-]+")
}
