package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.Length
import taboolib.expansion.TableName

/**
 * 玩家行为日志记录。
 *
 * `time` 由数据库默认值生成，因此数据类只映射业务写入的 `type` 与 `msg` 字段。
 */
@TableName("player_log")
data class PlayerLogRecord(
    @param:Length(32)
    val type: String,
    @param:Length(128)
    val msg: String,
)

val playerLogMapper by cachedMapper<PlayerLogRecord>(DataSource)
