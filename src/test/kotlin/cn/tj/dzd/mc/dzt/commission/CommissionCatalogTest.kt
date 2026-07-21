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
}
