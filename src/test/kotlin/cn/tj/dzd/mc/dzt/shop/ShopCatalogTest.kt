package cn.tj.dzd.mc.dzt.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.math.BigDecimal

class ShopCatalogTest {

    @Test
    fun `catalog indexes configured products by stable id`() {
        val product = product("oak_log")
        val catalog = ShopCatalog(listOf(category("wood", product)))

        assertEquals(product, catalog.findProduct("oak_log"))
    }

    @Test
    fun `duplicate product ids are rejected across categories`() {
        assertFailsWith<IllegalArgumentException> {
            ShopCatalog(
                listOf(
                    category("wood", product("oak_log")),
                    category("food", product("oak_log")),
                )
            )
        }
    }

    @Test
    fun `invalid item format price and daily limit are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            product("oak_log", materialId = "oak_log")
        }
        assertFailsWith<IllegalArgumentException> {
            product("oak_log", price = BigDecimal.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            product("oak_log", dailyLimit = 0)
        }
    }

    @Test
    fun `catalog resource is present on the test classpath`() {
        val stream = ShopCatalogs::class.java.classLoader.getResourceAsStream("shop.yml")
        assertNotNull(stream).use { source ->
            val content = source.bufferedReader().readText()
            assertTrue("categories:" in content)
            assertTrue("oak_log:" in content)
        }
    }

    private fun category(id: String, product: ShopProduct): ShopCategory {
        return ShopCategory(
            id = id,
            displayName = id,
            javaIcon = "minecraft:chest",
            bedrockIcon = "textures/blocks/chest_front.png",
            products = listOf(product),
        )
    }

    private fun product(
        id: String,
        materialId: String = "minecraft:oak_log",
        price: BigDecimal = BigDecimal.TEN,
        dailyLimit: Int = 10,
    ): ShopProduct {
        return ShopProduct(
            id = id,
            displayName = id,
            materialId = materialId,
            bedrockIcon = "textures/blocks/log_oak.png",
            price = price,
            dailyLimit = dailyLimit,
        )
    }
}
