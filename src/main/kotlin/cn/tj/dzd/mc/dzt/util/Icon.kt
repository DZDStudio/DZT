package cn.tj.dzd.mc.dzt.util

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.inventory.ItemStack
import taboolib.expansion.IndexedEnum
import java.util.Locale

/**
 * 可用于跨端菜单展示的图标。
 *
 * @property index 数据库持久化数值。
 * @property jeName Java 版物品命名空间 ID。
 * @property beTexturePath 基岩版表单图标材质路径。
 * @property displayName 图标中文名称。
 */
enum class Icon(
    val jeName: String,
    val beTexturePath: String,
    val displayName: String,
) : IndexedEnum {
    WHITE_BED("minecraft:white_bed", "textures/items/bed_white.png", "白色床"),
    RED_BED("minecraft:red_bed", "textures/items/bed_red.png", "红色床"),
    BLUE_BED("minecraft:blue_bed", "textures/items/bed_blue.png", "蓝色床"),
    GREEN_BED("minecraft:green_bed", "textures/items/bed_green.png", "绿色床"),
    YELLOW_BED("minecraft:yellow_bed", "textures/items/bed_yellow.png", "黄色床"),
    CHEST("minecraft:chest", "textures/blocks/chest_front.png", "箱子"),
    ENDER_CHEST("minecraft:ender_chest", "textures/blocks/ender_chest_front.png", "末影箱"),
    CRAFTING_TABLE("minecraft:crafting_table", "textures/blocks/crafting_table_front.png", "工作台"),
    FURNACE("minecraft:furnace", "textures/blocks/furnace_front_off.png", "熔炉"),
    BLAST_FURNACE("minecraft:blast_furnace", "textures/blocks/blast_furnace_front_off.png", "高炉"),
    SMOKER("minecraft:smoker", "textures/blocks/smoker_front_off.png", "烟熏炉"),
    BARREL("minecraft:barrel", "textures/blocks/barrel_top.png", "木桶"),
    OAK_PLANKS("minecraft:oak_planks", "textures/blocks/planks_oak.png", "橡木木板"),
    SPRUCE_PLANKS("minecraft:spruce_planks", "textures/blocks/planks_spruce.png", "云杉木板"),
    BIRCH_PLANKS("minecraft:birch_planks", "textures/blocks/planks_birch.png", "白桦木板"),
    JUNGLE_PLANKS("minecraft:jungle_planks", "textures/blocks/planks_jungle.png", "丛林木板"),
    ACACIA_PLANKS("minecraft:acacia_planks", "textures/blocks/planks_acacia.png", "金合欢木板"),
    DARK_OAK_PLANKS("minecraft:dark_oak_planks", "textures/blocks/planks_big_oak.png", "深色橡木木板"),
    MANGROVE_PLANKS("minecraft:mangrove_planks", "textures/blocks/mangrove_planks.png", "红树木板"),
    CHERRY_PLANKS("minecraft:cherry_planks", "textures/blocks/cherry_planks.png", "樱花木板"),
    OAK_LOG("minecraft:oak_log", "textures/blocks/log_oak.png", "橡木原木"),
    SPRUCE_LOG("minecraft:spruce_log", "textures/blocks/log_spruce.png", "云杉原木"),
    BIRCH_LOG("minecraft:birch_log", "textures/blocks/log_birch.png", "白桦原木"),
    JUNGLE_LOG("minecraft:jungle_log", "textures/blocks/log_jungle.png", "丛林原木"),
    ACACIA_LOG("minecraft:acacia_log", "textures/blocks/log_acacia.png", "金合欢原木"),
    DARK_OAK_LOG("minecraft:dark_oak_log", "textures/blocks/log_big_oak.png", "深色橡木原木"),
    GRASS_BLOCK("minecraft:grass_block", "textures/blocks/grass_side_carried.png", "草方块"),
    DIRT("minecraft:dirt", "textures/blocks/dirt.png", "泥土"),
    COARSE_DIRT("minecraft:coarse_dirt", "textures/blocks/coarse_dirt.png", "砂土"),
    STONE("minecraft:stone", "textures/blocks/stone.png", "石头"),
    COBBLESTONE("minecraft:cobblestone", "textures/blocks/cobblestone.png", "圆石"),
    BRICKS("minecraft:bricks", "textures/blocks/brick.png", "红砖块"),
    STONE_BRICKS("minecraft:stone_bricks", "textures/blocks/stonebrick.png", "石砖"),
    DEEPSLATE_BRICKS("minecraft:deepslate_bricks", "textures/blocks/deepslate/deepslate_bricks.png", "深板岩砖"),
    QUARTZ_BLOCK("minecraft:quartz_block", "textures/blocks/quartz_block_side.png", "石英块"),
    AMETHYST_BLOCK("minecraft:amethyst_block", "textures/blocks/amethyst_block.png", "紫水晶块"),
    DIAMOND("minecraft:diamond", "textures/items/diamond.png", "钻石"),
    EMERALD("minecraft:emerald", "textures/items/emerald.png", "绿宝石"),
    GOLD_INGOT("minecraft:gold_ingot", "textures/items/gold_ingot.png", "金锭"),
    IRON_INGOT("minecraft:iron_ingot", "textures/items/iron_ingot.png", "铁锭"),
    COPPER_INGOT("minecraft:copper_ingot", "textures/items/copper_ingot.png", "铜锭"),
    NETHERITE_INGOT("minecraft:netherite_ingot", "textures/items/netherite_ingot.png", "下界合金锭"),
    NETHER_STAR("minecraft:nether_star", "textures/items/nether_star.png", "下界之星"),
    ENDER_PEARL("minecraft:ender_pearl", "textures/items/ender_pearl.png", "末影珍珠"),
    EYE_OF_ENDER("minecraft:ender_eye", "textures/items/ender_eye.png", "末影之眼"),
    COMPASS("minecraft:compass", "textures/items/compass_item.png", "指南针"),
    RECOVERY_COMPASS("minecraft:recovery_compass", "textures/items/recovery_compass_item.png", "追溯指针"),
    CLOCK("minecraft:clock", "textures/items/clock_item.png", "时钟"),
    FILLED_MAP("minecraft:filled_map", "textures/items/map_filled.png", "地图"),
    BOOK("minecraft:book", "textures/items/book_normal.png", "书"),
    ENCHANTED_BOOK("minecraft:enchanted_book", "textures/items/book_enchanted.png", "附魔书"),
    LANTERN("minecraft:lantern", "textures/items/lantern.png", "灯笼"),
    SOUL_LANTERN("minecraft:soul_lantern", "textures/items/soul_lantern.png", "灵魂灯笼"),
    TORCH("minecraft:torch", "textures/blocks/torch_on.png", "火把"),
    REDSTONE_TORCH("minecraft:redstone_torch", "textures/blocks/redstone_torch_on.png", "红石火把"),
    SEA_LANTERN("minecraft:sea_lantern", "textures/blocks/sea_lantern.png", "海晶灯"),
    GLOWSTONE("minecraft:glowstone", "textures/blocks/glowstone.png", "荧石"),
    REDSTONE("minecraft:redstone", "textures/items/redstone_dust.png", "红石粉"),
    REDSTONE_BLOCK("minecraft:redstone_block", "textures/blocks/redstone_block.png", "红石块"),
    LAPIS_LAZULI("minecraft:lapis_lazuli", "textures/items/dye_powder_blue.png", "青金石"),
    APPLE("minecraft:apple", "textures/items/apple.png", "苹果"),
    GOLDEN_APPLE("minecraft:golden_apple", "textures/items/apple_golden.png", "金苹果"),
    BREAD("minecraft:bread", "textures/items/bread.png", "面包"),
    COOKED_BEEF("minecraft:cooked_beef", "textures/items/beef_cooked.png", "熟牛肉"),
    CAKE("minecraft:cake", "textures/items/cake.png", "蛋糕"),
    FLOWER_POT("minecraft:flower_pot", "textures/items/flower_pot.png", "花盆"),
    OAK_SAPLING("minecraft:oak_sapling", "textures/blocks/sapling_oak.png", "橡树树苗"),
    CHERRY_SAPLING("minecraft:cherry_sapling", "textures/blocks/cherry_sapling.png", "樱花树苗"),
    POPPY("minecraft:poppy", "textures/blocks/flower_rose.png", "虞美人"),
    DANDELION("minecraft:dandelion", "textures/blocks/flower_dandelion.png", "蒲公英"),
    SUNFLOWER("minecraft:sunflower", "textures/blocks/double_plant_sunflower_front.png", "向日葵"),
    WATER_BUCKET("minecraft:water_bucket", "textures/items/bucket_water.png", "水桶"),
    LAVA_BUCKET("minecraft:lava_bucket", "textures/items/bucket_lava.png", "熔岩桶"),
    TOTEM_OF_UNDYING("minecraft:totem_of_undying", "textures/items/totem.png", "不死图腾"),
    ELYTRA("minecraft:elytra", "textures/items/elytra.png", "鞘翅"),
    DIAMOND_SWORD("minecraft:diamond_sword", "textures/items/diamond_sword.png", "钻石剑"),
    DIAMOND_PICKAXE("minecraft:diamond_pickaxe", "textures/items/diamond_pickaxe.png", "钻石镐"),
    DIAMOND_AXE("minecraft:diamond_axe", "textures/items/diamond_axe.png", "钻石斧"),
    DIAMOND_SHOVEL("minecraft:diamond_shovel", "textures/items/diamond_shovel.png", "钻石锹"),
    BOW("minecraft:bow", "textures/items/bow_standby.png", "弓"),
    CROSSBOW("minecraft:crossbow", "textures/items/crossbow_standby.png", "弩");

    /**
     * 数据库持久化数值。
     *
     * 新增图标时应追加在枚举末尾，避免已存储的数值含义变化。
     */
    override val index: Long
        get() = ordinal.toLong() + 1

    /**
     * Java 版 Bukkit 物品材质对象。
     */
    val jeMaterial: Material by lazy {
        requireNotNull(resolveMaterial(jeName)) { "无法解析图标物品: $jeName" }
    }

    /**
     * 创建 Java 版展示物品。
     *
     * @param amount 物品数量，必须大于 0。
     * @return 新的 [ItemStack] 实例。
     */
    fun createJeItem(amount: Int = 1): ItemStack {
        require(amount > 0) { "物品数量必须大于 0" }
        return ItemStack(jeMaterial, amount)
    }

    companion object {
        private val byJeName = entries.associateBy { it.jeName }

        /**
         * 根据 Java 版物品命名空间 ID 查找图标。
         *
         * @param jeName Java 版物品命名空间 ID，允许省略 minecraft: 前缀。
         * @return 找到的图标；不存在时返回 null。
         */
        fun fromJeName(jeName: String): Icon? {
            return byJeName[normalizeJeName(jeName)]
        }

        /**
         * 根据 Java 版物品命名空间 ID 获取图标。
         *
         * @param jeName Java 版物品命名空间 ID，允许省略 minecraft: 前缀。
         * @return 找到的图标。
         * @throws IllegalArgumentException 当图标不存在时抛出。
         */
        fun requireJeName(jeName: String): Icon {
            val normalizedName = normalizeJeName(jeName)
            return requireNotNull(byJeName[normalizedName]) { "不支持的图标: $jeName" }
        }

        /**
         * 规范化 Java 版物品命名空间 ID。
         *
         * @param jeName Java 版物品名称或命名空间 ID。
         * @return 带 minecraft: 前缀的小写命名空间 ID。
         */
        fun normalizeJeName(jeName: String): String {
            val normalizedName = jeName.trim().lowercase(Locale.ROOT)
            require(normalizedName.isNotEmpty()) { "图标名称不能为空" }
            val namespacedName = if (normalizedName.contains(':')) normalizedName else "minecraft:$normalizedName"
            return legacyJeNameAliases[namespacedName] ?: namespacedName
        }

        private val legacyJeNameAliases = mapOf(
            "minecraft:eye_of_ender" to "minecraft:ender_eye"
        )

        private fun resolveMaterial(jeName: String): Material? {
            val normalizedName = normalizeJeName(jeName)
            val key = NamespacedKey.fromString(normalizedName)
            val registryMaterial = key?.let { Registry.MATERIAL.get(it) }
            if (registryMaterial != null) {
                return registryMaterial
            }

            val materialName = normalizedName.substringAfter(':').uppercase(Locale.ROOT)
            return Material.matchMaterial(materialName)
        }
    }
}
