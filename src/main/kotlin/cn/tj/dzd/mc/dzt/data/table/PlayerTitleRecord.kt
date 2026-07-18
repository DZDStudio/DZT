package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.Id
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.TableName
import taboolib.expansion.UniqueKey
import java.util.UUID

/**
 * 玩家已拥有称号的持久化记录。
 *
 * [recordId] 由玩家 UUID 与标准化后的称号 ID 组成，用于防止同一玩家重复获得同一称号。
 */
data class PlayerTitleRecord(
    @param:Id
    @param:UniqueKey
    @param:Length(128)
    val recordId: String,
    @param:Key
    val uuid: UUID,
    @param:Length(64)
    val titleId: String,
    @param:Length(64)
    val displayName: String,
    @param:Length(256)
    val description: String,
    val acquiredAt: Long,
    var equipped: Boolean,
)

val playerTitleRecordMapper by cachedMapper<PlayerTitleRecord>(DataSource)
