package cn.tj.dzd.mc.dzt.data.table

import cn.tj.dzd.mc.dzt.data.DataSource
import taboolib.expansion.Id
import taboolib.expansion.TableName
import taboolib.expansion.UniqueKey
import taboolib.expansion.mapper
import java.util.UUID

/**
 * 玩家飞行开关的持久化记录。
 *
 * 该记录使用飞行功能的独立新表，不会修改任何既有数据表结构。
 *
 * @property playerId 玩家 UUID，同时作为记录主键和唯一业务键。
 * @property enabled 玩家是否开启飞行功能。
 */
@TableName("player_flight_setting")
data class PlayerFlightSettingRecord(
    @param:Id
    @param:UniqueKey
    val playerId: UUID,
    var enabled: Boolean,
)

/**
 * 玩家飞行开关的 Persistent Container 映射器。
 *
 * 飞行开关由多台共享 MySQL 的游戏服共同访问。这里不启用进程内 L2 缓存，
 * 避免玩家跨服后读取到另一服务器实例修改前的过期开关。
 */
val playerFlightSettingRecordMapper by mapper<PlayerFlightSettingRecord>(DataSource)
