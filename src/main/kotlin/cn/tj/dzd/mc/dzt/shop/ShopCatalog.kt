package cn.tj.dzd.mc.dzt.shop

import cn.tj.dzd.mc.dzt.DZT
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.library.xseries.XMaterial
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import java.math.BigDecimal
import java.util.Locale

private val SHOP_ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,63}")
private val MINECRAFT_ITEM_PATTERN = Regex("minecraft:[a-z0-9_]+")

/**
 * 商店的完整只读目录。
 *
 * 目录仅由插件包内的 `shop.yml` 构建，不会读取或释放数据目录中的同名文件。
 */
class ShopCatalog(categories: List<ShopCategory>) {

    /** 按配置顺序展示的分类。 */
    val categories: List<ShopCategory> = categories.toList()

    private val productsById: Map<String, ShopProduct>

    init {
        require(this.categories.isNotEmpty()) { "商店至少需要一个分类。" }
        require(this.categories.map(ShopCategory::id).distinct().size == this.categories.size) {
            "商店分类 ID 不能重复。"
        }
        val products = this.categories.flatMap(ShopCategory::products)
        require(products.isNotEmpty()) { "商店至少需要一个商品。" }
        require(products.map(ShopProduct::id).distinct().size == products.size) {
            "商店商品 ID 必须在全目录中唯一。"
        }
        productsById = products.associateBy(ShopProduct::id)
    }

    /**
     * 按稳定商品 ID 获取商品。
     *
     * @param productId `shop.yml` 中定义的商品 ID。
     * @return 商品不存在时返回 null。
     */
    fun findProduct(productId: String): ShopProduct? = productsById[productId]
}

/**
 * 商店商品分类。
 *
 * @property id 稳定配置 ID，不向玩家展示。
 * @property displayName 玩家可见分类名称。
 * @property javaIcon Java 箱子菜单的原版物品 ID。
 * @property bedrockIcon 基岩表单的材质路径。
 * @property products 该分类中的商品。
 */
data class ShopCategory(
    val id: String,
    val displayName: String,
    val javaIcon: String,
    val bedrockIcon: String,
    val products: List<ShopProduct>,
) {
    init {
        require(SHOP_ID_PATTERN.matches(id)) { "分类 ID 非法: $id" }
        require(displayName.isNotBlank()) { "分类名称不能为空。" }
        require(MINECRAFT_ITEM_PATTERN.matches(javaIcon)) { "分类 Java 图标非法: $javaIcon" }
        require(bedrockIcon.startsWith("textures/")) { "分类基岩图标必须是 textures/ 路径。" }
        require(products.isNotEmpty()) { "分类至少需要一个商品。" }
    }
}

/**
 * 商店中的单个可购买原版物品。
 *
 * @property id 稳定配置 ID，用于每日限购记录。
 * @property displayName 玩家可见名称。
 * @property materialId Java 发放与展示使用的原版物品 ID。
 * @property bedrockIcon 基岩表单按钮材质路径。
 * @property price 单个物品的 DDB 价格。
 * @property dailyLimit 按物品数量计算的北京时间每日限购。
 */
data class ShopProduct(
    val id: String,
    val displayName: String,
    val materialId: String,
    val bedrockIcon: String,
    val price: BigDecimal,
    val dailyLimit: Int,
) {
    init {
        require(SHOP_ID_PATTERN.matches(id)) { "商品 ID 非法: $id" }
        require(displayName.isNotBlank()) { "商品名称不能为空。" }
        require(MINECRAFT_ITEM_PATTERN.matches(materialId)) { "商品原版物品 ID 非法: $materialId" }
        require(bedrockIcon.startsWith("textures/")) { "商品基岩图标必须是 textures/ 路径。" }
        require(price > BigDecimal.ZERO) { "商品价格必须大于 0。" }
        require(dailyLimit > 0) { "商品每日限购必须大于 0。" }
    }
}

/**
 * 加载插件包内商店目录的入口。
 *
 * 不提供运行时重载接口，确保管理员不能在数据目录中覆盖商店内容。
 */
object ShopCatalogs {

    /** 首次访问时从插件 classpath 读取并校验的目录。 */
    val catalog: ShopCatalog by lazy(::load)

    /**
     * 在插件启用阶段提前校验打包目录，避免玩家首次打开商店才发现资源配置错误。
     */
    @Awake(LifeCycle.ACTIVE)
    fun validatePackagedCatalog() {
        catalog
    }

    private fun load(): ShopCatalog {
        val content = requireNotNull(DZT::class.java.classLoader.getResourceAsStream("shop.yml")) {
            "插件包中缺少 shop.yml。"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(content)
    }

    /**
     * 解析并校验 YAML 商店目录。
     *
     * 此函数仅供同包测试使用；正式运行时必须经由 [catalog] 读取打包资源。
     */
    internal fun parse(content: String): ShopCatalog {
        val root = Configuration.loadFromString(content)
        val categoriesSection = requireNotNull(root.getConfigurationSection("categories")) {
            "shop.yml 缺少 categories 节点。"
        }
        val categoryIds = categoriesSection.getKeys(false).sorted()
        require(categoryIds.isNotEmpty()) { "shop.yml 的 categories 不能为空。" }

        return ShopCatalog(categoryIds.map { categoryId ->
            requireId(categoryId, "分类")
            val section = requireNotNull(categoriesSection.getConfigurationSection(categoryId)) {
                "分类 $categoryId 必须是配置节。"
            }
            val productSection = requireNotNull(section.getConfigurationSection("products")) {
                "分类 $categoryId 缺少 products 节点。"
            }
            val productIds = productSection.getKeys(false).sorted()
            require(productIds.isNotEmpty()) { "分类 $categoryId 至少需要一个商品。" }

            ShopCategory(
                id = categoryId,
                displayName = section.requiredText("name", "分类 $categoryId 的名称"),
                javaIcon = section.requiredMaterial("java-icon", "分类 $categoryId 的 Java 图标"),
                bedrockIcon = section.requiredBedrockIcon("bedrock-icon", "分类 $categoryId 的基岩图标"),
                products = productIds.map { productId ->
                    requireId(productId, "商品")
                    val product = requireNotNull(productSection.getConfigurationSection(productId)) {
                        "商品 $productId 必须是配置节。"
                    }
                    ShopProduct(
                        id = productId,
                        displayName = product.requiredText("name", "商品 $productId 的名称"),
                        materialId = product.requiredMaterial("material", "商品 $productId 的物品"),
                        bedrockIcon = product.requiredBedrockIcon("bedrock-icon", "商品 $productId 的基岩图标"),
                        price = product.requiredPositiveDecimal("price", "商品 $productId 的价格"),
                        dailyLimit = product.requiredPositiveInt("daily-limit", "商品 $productId 的每日限购"),
                    )
                },
            )
        })
    }

    private fun requireId(value: String, kind: String) {
        require(SHOP_ID_PATTERN.matches(value)) { "$kind ID 非法: $value" }
    }

    private fun ConfigurationSection.requiredText(path: String, description: String): String {
        return getString(path)?.trim()?.takeIf(String::isNotEmpty)
            ?: error("$description 不能为空。")
    }

    private fun ConfigurationSection.requiredMaterial(path: String, description: String): String {
        val materialId = requiredText(path, description).lowercase(Locale.ROOT)
        val normalized = if (':' in materialId) materialId else "minecraft:$materialId"
        require(normalized.startsWith("minecraft:")) { "$description 必须是原版物品 ID: $materialId" }
        val materialName = normalized.substringAfter(':').uppercase(Locale.ROOT)
        require(XMaterial.matchXMaterial(materialName).isPresent) { "$description 无法解析: $materialId" }
        return normalized
    }

    private fun ConfigurationSection.requiredBedrockIcon(path: String, description: String): String {
        val icon = requiredText(path, description)
        require(icon.startsWith("textures/")) { "$description 必须是 textures/ 开头的材质路径。" }
        return icon
    }

    private fun ConfigurationSection.requiredPositiveDecimal(path: String, description: String): BigDecimal {
        val value = requiredText(path, description).toBigDecimalOrNull()
            ?: error("$description 必须是十进制数。")
        require(value > BigDecimal.ZERO) { "$description 必须大于 0。" }
        return value
    }

    private fun ConfigurationSection.requiredPositiveInt(path: String, description: String): Int {
        val value = requiredText(path, description).toIntOrNull()
            ?: error("$description 必须是整数。")
        require(value > 0) { "$description 必须大于 0。" }
        return value
    }
}
