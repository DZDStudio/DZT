package cn.tj.dzd.mc.dzt.teleport.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.util.Icon
import taboolib.expansion.Key
import taboolib.expansion.Length
import taboolib.expansion.mapper
import java.util.UUID

data class HomeRecord(
    @param:Key
    val uuid: UUID,
    @param:Key
    @param:Length(16)
    val name: String,
    val icon: Icon,
    @param:Length(32)
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
)
val homeRecordMapper by mapper<HomeRecord>(DataSource)
