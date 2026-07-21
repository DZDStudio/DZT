package cn.tj.dzd.mc.dzt.commission

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommissionCatalogTest {

    @Test
    fun `packaged catalog declares complete commission pools`() {
        val content = requireNotNull(
            CommissionCatalogs::class.java.classLoader.getResourceAsStream("commission.yml")
        ).bufferedReader().use { it.readText() }

        CommissionDifficulty.entries.forEach { difficulty ->
            assertTrue("  ${difficulty.configKey}:" in content)
        }
        val taskCount = content.lineSequence().count { it.startsWith("      type:") }
        assertTrue(taskCount >= 60, "委托资源应至少提供 60 条任务，当前为 $taskCount 条。")
        listOf("target", "amount", "reward", "name", "description", "java-icon", "bedrock-icon").forEach { field ->
            val count = content.lineSequence().count { it.startsWith("      $field:") }
            assertEquals(taskCount, count, "每条委托都必须声明 $field 字段。")
        }
        assertTrue("      type: submit_item" in content)
        assertTrue("      type: kill_entity" in content)
        assertTrue("      target: minecraft:player" in content)
    }

    @Test
    fun `packaged catalog reward budget matches daily income targets`() {
        val rewards = packagedRewards()
        val simplePool = rewards.getValue(CommissionDifficulty.SIMPLE).values.toList()
        val normalPool = rewards.getValue(CommissionDifficulty.NORMAL).values.toList()
        val regularHardPool = rewards.getValue(CommissionDifficulty.HARD)
            .filterKeys { it !in ULTRA_HARD_COMMISSION_IDS }
            .values
            .toList()
        val simpleMean = meanReward(simplePool)
        val normalMean = meanReward(normalPool)
        val regularHardMean = meanReward(regularHardPool)

        assertAmount(BigDecimal("512"), simpleMean, "简单委托均值")
        assertAmount(BigDecimal("1536"), normalMean, "普通委托均值")
        assertAmount(BigDecimal("6144"), regularHardMean, "常规困难委托均值")

        val simpleDailyReward = simpleMean.multiply(BigDecimal.valueOf(CommissionDifficulty.SIMPLE.dailyCount.toLong()))
        val normalDailyReward = normalMean.multiply(BigDecimal.valueOf(CommissionDifficulty.NORMAL.dailyCount.toLong()))
        val regularAllDailyReward = simpleDailyReward.add(normalDailyReward).add(regularHardMean)
        assertAmount(BigDecimal("1024"), simpleDailyReward, "每日两项简单委托期望奖励")
        assertAmount(BigDecimal("10240"), regularAllDailyReward, "每日常规委托全清期望奖励")

        val simplePairRewards = simplePool.indices.flatMap { left ->
            (left + 1 until simplePool.size).map { right ->
                simplePool[left].add(simplePool[right])
            }
        }
        assertTrue(
            simplePairRewards.all { it >= BigDecimal("896") && it <= BigDecimal("1152") },
            "任意两项简单委托的总奖励都应维持在 1024 DDB 附近。",
        )

        val hardRewards = rewards.getValue(CommissionDifficulty.HARD)
        assertTrue(ULTRA_HARD_COMMISSION_IDS.all { hardRewards.getValue(it) > regularHardMean })
        val dragonReward = hardRewards.getValue("ender_dragon_hunt")
        assertTrue(
            dragonReward >= regularHardMean.multiply(BigDecimal("2")),
            "末影龙委托包含复活与击杀成本，奖励至少应为常规困难委托均值的两倍。",
        )
    }

    @Test
    fun `daily selection always uses configured difficulty quotas without duplicates`() {
        val catalog = sampleCatalog()
        val selection = DailyCommissionSelector.select(
            catalog,
            LocalDate.of(2026, 7, 21),
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
        )

        assertEquals(5, selection.commissions.size)
        assertEquals(
            listOf(
                CommissionDifficulty.SIMPLE,
                CommissionDifficulty.SIMPLE,
                CommissionDifficulty.NORMAL,
                CommissionDifficulty.NORMAL,
                CommissionDifficulty.HARD,
            ),
            selection.commissions.map { it.difficulty },
        )
        assertEquals(selection.commissions.size, selection.commissions.map { it.id }.distinct().size)
    }

    @Test
    fun `same player and date produce a stable personal selection`() {
        val catalog = sampleCatalog()
        val date = LocalDate.of(2026, 7, 21)
        val playerId = UUID.fromString("11111111-2222-3333-4444-555555555555")

        val first = DailyCommissionSelector.select(catalog, date, playerId)
        val second = DailyCommissionSelector.select(catalog, date, playerId)

        assertEquals(first, second)
        assertEquals(playerId, first.playerId)
        assertEquals(date, first.date)
    }

    @Test
    fun `different players use independent daily selections`() {
        val catalog = sampleCatalog()
        val date = LocalDate.of(2026, 7, 21)
        val first = DailyCommissionSelector.select(
            catalog,
            date,
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
        )
        val second = DailyCommissionSelector.select(
            catalog,
            date,
            UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"),
        )

        assertTrue(first.commissions.map { it.id } != second.commissions.map { it.id })
    }

    private fun sampleCatalog(): CommissionCatalog {
        return CommissionCatalog(
            CommissionDifficulty.entries.associateWith { difficulty ->
                (1..(difficulty.dailyCount + 2)).map { index ->
                    CommissionDefinition(
                        id = "${difficulty.configKey}_$index",
                        difficulty = difficulty,
                        objectiveType = CommissionObjectiveType.KILL_ENTITY,
                        targetId = "minecraft:zombie",
                        targetAmount = index,
                        reward = BigDecimal.valueOf(index.toLong()),
                        displayName = "${difficulty.displayName}$index",
                        description = listOf("测试委托 $index"),
                        javaIcon = "minecraft:iron_sword",
                        bedrockIcon = "textures/items/iron_sword.png",
                    )
                }
            }
        )
    }

    private fun packagedRewards(): Map<CommissionDifficulty, Map<String, BigDecimal>> {
        val content = requireNotNull(
            CommissionCatalogs::class.java.classLoader.getResourceAsStream("commission.yml")
        ).bufferedReader().use { it.readText() }
        val rewards = CommissionDifficulty.entries.associateWith { linkedMapOf<String, BigDecimal>() }
        var currentDifficulty: CommissionDifficulty? = null
        var currentCommissionId: String? = null

        content.lineSequence().forEach { line ->
            val difficulty = CommissionDifficulty.entries.firstOrNull { line == "  ${it.configKey}:" }
            if (difficulty != null) {
                currentDifficulty = difficulty
                currentCommissionId = null
                return@forEach
            }

            val taskHeader = TASK_HEADER_PATTERN.matchEntire(line)
            if (currentDifficulty != null && taskHeader != null) {
                currentCommissionId = taskHeader.groupValues[1]
                return@forEach
            }

            if (line.startsWith("      reward:")) {
                val difficultyKey = requireNotNull(currentDifficulty) { "reward 字段必须位于难度池内。" }
                val commissionId = requireNotNull(currentCommissionId) { "reward 字段必须位于委托内。" }
                rewards.getValue(difficultyKey)[commissionId] = BigDecimal(line.substringAfter(':').trim())
            }
        }
        return rewards
    }

    private fun meanReward(rewards: List<BigDecimal>): BigDecimal {
        val total = rewards.fold(BigDecimal.ZERO, BigDecimal::add)
        return total.divide(BigDecimal.valueOf(rewards.size.toLong()))
    }

    private fun assertAmount(expected: BigDecimal, actual: BigDecimal, description: String) {
        assertEquals(0, expected.compareTo(actual), "$description 应为 $expected DDB，实际为 $actual DDB。")
    }

    private companion object {
        val TASK_HEADER_PATTERN = Regex("^    ([a-z0-9][a-z0-9_-]{0,63}):$")
        val ULTRA_HARD_COMMISSION_IDS = setOf("warden_hunt", "ender_dragon_hunt", "wither_hunt")
    }
}
