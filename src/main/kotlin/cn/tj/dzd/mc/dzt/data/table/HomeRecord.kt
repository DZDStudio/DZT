package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import cn.tj.dzd.mc.dzt.util.Icon
import taboolib.expansion.Key
import taboolib.expansion.Length
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
val homeRecordMapper by cachedMapper<HomeRecord>(DataSource)
