package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.Key
import taboolib.expansion.Length
import java.util.UUID

data class BackRecord(
    @param:Key
    val uuid: UUID,
    @param:Key
    val time: Long,
    @param:Length(32)
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
)
val backRecordMapper by cachedMapper<BackRecord>(DataSource)
