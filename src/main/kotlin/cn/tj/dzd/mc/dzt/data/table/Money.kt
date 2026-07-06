package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.Id
import java.util.UUID

data class Money(
    @param:Id
    val uuid: UUID,
    var ddCoin: Int,
)
val moneyMapper by cachedMapper<Money>(DataSource)
