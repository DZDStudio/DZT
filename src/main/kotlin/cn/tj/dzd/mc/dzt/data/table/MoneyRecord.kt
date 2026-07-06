package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import cn.tj.dzd.mc.dzt.data.cachedMapper
import taboolib.expansion.IndexedEnum
import taboolib.expansion.Key
import taboolib.expansion.Length
import java.util.UUID

/**
 * 金钱流水类型。
 */
enum class MoneyRecordType(override val index: Long, val desc: String) : IndexedEnum {
    /** 出账。 */
    OUT(1, "出账"),

    /** 入账。 */
    IN(2, "入账"),
}

/**
 * 金钱流水记录。
 *
 * @property uuid 记录所属玩家 UUID。
 * @property time 流水发生时间戳，单位为毫秒。
 * @property type 流水类型。
 * @property amount 本次变动的 DD Coin 数量。
 * @property related 关联对象标识，可存玩家 UUID、server、system 等文本。
 * @property remark 流水备注。
 */
data class MoneyRecord(
    @param:Key
    val uuid: UUID,
    @param:Key
    val time: Long,
    val type: MoneyRecordType,
    val amount: Int,
    @param:Length(64)
    val related: String?,
    @param:Length(256)
    val remark: String?,
)
val moneyRecordMapper by cachedMapper<MoneyRecord>(DataSource)
