package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.Id
import taboolib.expansion.TableName
import java.util.UUID

@TableName("preferred_route")
data class PreferredRouteRecord(
    @param:Id
    val uuid: UUID,
)

val preferredRouteRecordMapper by cachedMapper<PreferredRouteRecord>(DataSource)
