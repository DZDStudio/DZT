package cn.tj.dzd.mc.dzt.log

import java.util.Locale

/**
 * 玩家日志的数据库文本编码工具。
 *
 * 现有 `player_log` 表可能仍使用 MySQL `utf8mb3`，无法保存补充平面的四字节字符。
 * 为避免一条聊天记录导致数据库保护逻辑关闭服务器，无法直接存储的码点会转义为 ASCII
 * `\UXXXXXXXX` 形式；不成对的 UTF-16 代理项则使用 `\uXXXX` 形式保留。
 */
internal object PlayerLogText {

    private const val UTF8MB3_MAX_CODE_POINT = 0xFFFF

    /**
     * 将文本规范为可写入 `utf8mb3` 列的形式，并限制结果长度。
     *
     * 换行会替换为空格以保持单行日志。长度以 UTF-16 字符计；转换后仅包含 BMP 字符，
     * 因而与 MySQL `VARCHAR` 的字符限制一致。若下一个完整字符或转义串超出限制，
     * 将停止写入，避免产生截断的代理对或转义串。
     *
     * @param value 原始日志文本。
     * @param maxLength 目标列可容纳的最大字符数。
     * @return 可安全存入既有 `utf8mb3` 列的文本。
     */
    fun normalizeForStorage(value: String, maxLength: Int): String {
        require(maxLength >= 0) { "maxLength 不能小于 0" }

        val result = StringBuilder(minOf(value.length, maxLength))
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val normalized = when {
                codePoint == '\n'.code || codePoint == '\r'.code -> " "
                codePoint > UTF8MB3_MAX_CODE_POINT -> "\\U${codePoint.toString(16).uppercase(Locale.ROOT).padStart(8, '0')}"
                Character.isSurrogate(codePoint.toChar()) -> "\\u${codePoint.toString(16).uppercase(Locale.ROOT).padStart(4, '0')}"
                else -> codePoint.toChar().toString()
            }
            if (result.length + normalized.length > maxLength) {
                break
            }
            result.append(normalized)
            index += Character.charCount(codePoint)
        }
        return result.toString()
    }
}
