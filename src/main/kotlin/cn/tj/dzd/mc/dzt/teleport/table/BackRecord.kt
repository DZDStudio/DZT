package cn.tj.dzd.mc.dzt.teleport.table

import cn.tj.dzd.mc.dzt.data.DataSource
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.TableName
import taboolib.expansion.mapper
import java.util.UUID

@TableName("back_record")
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
val backRecordMapper by mapper<BackRecord>(DataSource)
