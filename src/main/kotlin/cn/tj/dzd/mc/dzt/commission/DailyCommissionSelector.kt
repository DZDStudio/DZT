package cn.tj.dzd.mc.dzt.commission

import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/** 一名玩家在北京时间自然日内的委托轮换结果。 */
data class DailyCommissionSelection(
    val playerId: UUID,
    val date: LocalDate,
    val commissions: List<CommissionDefinition>,
) {
    init {
        require(commissions.size == CommissionDifficulty.entries.sumOf(CommissionDifficulty::dailyCount)) {
            "每日委托数量必须为 5。"
        }
    }
}

/**
 * 从三个委托池为一名玩家生成当天的五个委托。
 *
 * 选择使用玩家 UUID、日期和难度作为稳定随机种子。同一玩家当天无论何时打开界面、插件是否重启，
 * 都会得到同一组委托；不同玩家各自独立随机，次日种子变化后自动刷新。
 */
object DailyCommissionSelector {

    /**
     * 生成指定日期的五个委托。
     *
     * @param catalog 已校验的委托目录。
     * @param date 要生成的自然日。
     * @param playerId 要生成委托的玩家 UUID。
     * @return 依次为两个简单、两个普通和一个困难委托的选择结果。
     */
    fun select(catalog: CommissionCatalog, date: LocalDate, playerId: UUID): DailyCommissionSelection {
        val selected = CommissionDifficulty.entries.flatMap { difficulty ->
            catalog.pool(difficulty)
                .sortedBy(CommissionDefinition::id)
                .shuffled(Random(seed(date, playerId, difficulty)))
                .take(difficulty.dailyCount)
        }
        return DailyCommissionSelection(playerId, date, selected)
    }

    private fun seed(date: LocalDate, playerId: UUID, difficulty: CommissionDifficulty): Int {
        var mixed = date.toEpochDay() xor playerId.mostSignificantBits
        mixed = (mixed xor (mixed ushr 30)) * SEED_MIX_MULTIPLIER_ONE
        mixed = (mixed xor playerId.leastSignificantBits) * SEED_MIX_MULTIPLIER_TWO
        mixed = mixed xor (difficulty.ordinal.toLong() * DIFFICULTY_SEED_STEP)
        mixed = mixed xor (mixed ushr 27)
        return (mixed xor (mixed ushr 32)).toInt()
    }

    private const val SEED_MIX_MULTIPLIER_ONE = -4658895280553007687L
    private const val SEED_MIX_MULTIPLIER_TWO = -7723592293110705685L
    private const val DIFFICULTY_SEED_STEP = -7046029254386353131L
}
