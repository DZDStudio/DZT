package cn.tj.dzd.mc.dzt.commission

import cn.tj.dzd.mc.dzt.DZT
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XEntityType
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Configuration
import java.math.BigDecimal
import java.util.Locale

private val COMMISSION_ID_PATTERN = Regex("[a-z0-9][a-z0-9_-]{0,63}")
private val MINECRAFT_ID_PATTERN = Regex("minecraft:[a-z0-9_]+")

/** 每日委托的难度池。 */
enum class CommissionDifficulty(
    val configKey: String,
    val displayName: String,
    val dailyCount: Int,
) {
    SIMPLE("simple", "简单", 2),
    NORMAL("normal", "普通", 2),
    HARD("hard", "困难", 1),
}

/** 委托的目标行为类型。 */
enum class CommissionObjectiveType(val configValue: String) {
    SUBMIT_ITEM("submit_item"),
    KILL_ENTITY("kill_entity"),
}

/**
 * 一个仅由插件资源文件定义的委托。
 *
 * [id] 是持久化进度使用的稳定标识，发布后不得随意修改。所有玩家可见的文案和图标都由其余字段控制。
 */
data class CommissionDefinition(
    val id: String,
    val difficulty: CommissionDifficulty,
    val objectiveType: CommissionObjectiveType,
    val targetId: String,
    val targetAmount: Int,
    val reward: BigDecimal,
    val displayName: String,
    val description: List<String>,
    val javaIcon: String,
    val bedrockIcon: String,
) {
    init {
        require(COMMISSION_ID_PATTERN.matches(id)) { "委托 ID 非法: $id" }
        require(MINECRAFT_ID_PATTERN.matches(targetId)) { "委托 $id 的目标 ID 非法: $targetId" }
        require(targetAmount > 0) { "委托 $id 的目标数量必须大于 0。" }
        require(reward > BigDecimal.ZERO) { "委托 $id 的奖励必须大于 0。" }
        require(displayName.isNotBlank()) { "委托 $id 的名称不能为空。" }
        require(description.isNotEmpty() && description.all(String::isNotBlank)) {
            "委托 $id 的介绍不能为空。"
        }
        require(MINECRAFT_ID_PATTERN.matches(javaIcon)) { "委托 $id 的 Java 图标非法: $javaIcon" }
        require(bedrockIcon.startsWith("textures/")) { "委托 $id 的基岩图标必须是 textures/ 路径。" }
    }
}

/**
 * 只读委托目录。
 *
 * 每个难度池至少要包含当天会抽取的委托数量，且 ID 必须在全目录中唯一。
 */
class CommissionCatalog(
    pools: Map<CommissionDifficulty, List<CommissionDefinition>>,
) {
    private val pools: Map<CommissionDifficulty, List<CommissionDefinition>> =
        CommissionDifficulty.entries.associateWith { difficulty ->
            pools[difficulty].orEmpty().toList()
        }

    private val definitionsById: Map<String, CommissionDefinition>

    init {
        CommissionDifficulty.entries.forEach { difficulty ->
            require(this.pools.getValue(difficulty).size >= difficulty.dailyCount) {
                "${difficulty.displayName}委托池至少需要 ${difficulty.dailyCount} 个委托。"
            }
        }

        val definitions = this.pools.values.flatten()
        require(definitions.map(CommissionDefinition::id).distinct().size == definitions.size) {
            "委托 ID 必须在所有难度池中唯一。"
        }
        definitionsById = definitions.associateBy(CommissionDefinition::id)
    }

    /**
     * 读取指定难度池中的全部委托。
     *
     * @param difficulty 要读取的难度。
     * @return 按资源配置顺序排列的不可变委托列表。
     */
    fun pool(difficulty: CommissionDifficulty): List<CommissionDefinition> = pools.getValue(difficulty)

    /**
     * 按稳定 ID 查找委托。
     *
     * @param commissionId `commission.yml` 中定义的委托 ID。
     * @return 未定义时返回 null。
     */
    fun find(commissionId: String): CommissionDefinition? = definitionsById[commissionId]
}

/**
 * 加载插件包内 `commission.yml` 的入口。
 *
 * 配置只从 classpath 读取，绝不会释放到插件数据目录，也没有运行时重载接口；因此仅开发者能通过资源文件修改委托内容。
 */
object CommissionCatalogs {

    /** 首次访问时读取并校验的内嵌目录。 */
    val catalog: CommissionCatalog by lazy(::load)

    /** 在插件启用阶段提前验证资源目录。 */
    @Awake(LifeCycle.ACTIVE)
    fun validatePackagedCatalog() {
        catalog
    }

    private fun load(): CommissionCatalog {
        val content = requireNotNull(DZT::class.java.classLoader.getResourceAsStream("commission.yml")) {
            "插件包中缺少 commission.yml。"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(content)
    }

    /**
     * 解析资源中的 YAML 文本。
     *
     * 该函数仅供同包测试验证格式使用；线上必须通过 [catalog] 访问打包资源。
     */
    internal fun parse(content: String): CommissionCatalog {
        val root = Configuration.loadFromString(content)
        val pools = requireNotNull(root.getConfigurationSection("pools")) {
            "commission.yml 缺少 pools 节点。"
        }

        return CommissionCatalog(
            CommissionDifficulty.entries.associateWith { difficulty ->
                val pool = requireNotNull(pools.getConfigurationSection(difficulty.configKey)) {
                    "commission.yml 缺少 ${difficulty.configKey} 委托池。"
                }
                val ids = pool.getKeys(false).sorted()
                require(ids.isNotEmpty()) { "${difficulty.displayName}委托池不能为空。" }
                ids.map { id -> parseDefinition(id, difficulty, pool) }
            }
        )
    }

    private fun parseDefinition(
        id: String,
        difficulty: CommissionDifficulty,
        pool: ConfigurationSection,
    ): CommissionDefinition {
        require(COMMISSION_ID_PATTERN.matches(id)) { "委托 ID 非法: $id" }
        val section = requireNotNull(pool.getConfigurationSection(id)) {
            "委托 $id 必须是配置节。"
        }
        val objectiveType = section.requiredObjectiveType(id)
        val targetId = section.requiredTarget(id, objectiveType)
        return CommissionDefinition(
            id = id,
            difficulty = difficulty,
            objectiveType = objectiveType,
            targetId = targetId,
            targetAmount = section.requiredPositiveInt("amount", "委托 $id 的目标数量"),
            reward = section.requiredPositiveDecimal("reward", "委托 $id 的奖励"),
            displayName = section.requiredText("name", "委托 $id 的名称"),
            description = section.requiredDescription(id),
            javaIcon = section.requiredMaterial("java-icon", "委托 $id 的 Java 图标"),
            bedrockIcon = section.requiredBedrockIcon("bedrock-icon", "委托 $id 的基岩图标"),
        )
    }

    private fun ConfigurationSection.requiredObjectiveType(commissionId: String): CommissionObjectiveType {
        val value = requiredText("type", "委托 $commissionId 的类型").lowercase(Locale.ROOT)
        return CommissionObjectiveType.entries.firstOrNull { it.configValue == value }
            ?: error("委托 $commissionId 的类型非法: $value；仅支持 submit_item 或 kill_entity。")
    }

    private fun ConfigurationSection.requiredTarget(
        commissionId: String,
        objectiveType: CommissionObjectiveType,
    ): String {
        val target = requiredMinecraftId("target", "委托 $commissionId 的目标")
        when (objectiveType) {
            CommissionObjectiveType.SUBMIT_ITEM -> {
                val materialName = target.substringAfter(':').uppercase(Locale.ROOT)
                require(XMaterial.matchXMaterial(materialName).isPresent) {
                    "委托 $commissionId 的上交物品无法解析: $target"
                }
            }

            CommissionObjectiveType.KILL_ENTITY -> {
                val entityName = target.substringAfter(':').uppercase(Locale.ROOT)
                require(XEntityType.of(entityName).isPresent) {
                    "委托 $commissionId 的击杀实体无法解析: $target"
                }
            }
        }
        return target
    }

    private fun ConfigurationSection.requiredDescription(commissionId: String): List<String> {
        val lines = getStringList("description")
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(lines.isNotEmpty()) { "委托 $commissionId 的 description 至少需要一行非空文本。" }
        return lines
    }

    private fun ConfigurationSection.requiredMaterial(path: String, description: String): String {
        val material = requiredMinecraftId(path, description)
        val materialName = material.substringAfter(':').uppercase(Locale.ROOT)
        require(XMaterial.matchXMaterial(materialName).isPresent) { "$description 无法解析: $material" }
        return material
    }

    private fun ConfigurationSection.requiredMinecraftId(path: String, description: String): String {
        val raw = requiredText(path, description).lowercase(Locale.ROOT)
        val normalized = if (':' in raw) raw else "minecraft:$raw"
        require(MINECRAFT_ID_PATTERN.matches(normalized)) { "$description 必须是原版命名空间 ID: $raw" }
        return normalized
    }

    private fun ConfigurationSection.requiredBedrockIcon(path: String, description: String): String {
        val icon = requiredText(path, description)
        require(icon.startsWith("textures/")) { "$description 必须是 textures/ 开头的材质路径。" }
        return icon
    }

    private fun ConfigurationSection.requiredPositiveInt(path: String, description: String): Int {
        val value = requiredText(path, description).toIntOrNull()
            ?: error("$description 必须是整数。")
        require(value > 0) { "$description 必须大于 0。" }
        return value
    }

    private fun ConfigurationSection.requiredPositiveDecimal(path: String, description: String): BigDecimal {
        val value = requiredText(path, description).toBigDecimalOrNull()
            ?: error("$description 必须是十进制数。")
        require(value > BigDecimal.ZERO) { "$description 必须大于 0。" }
        return value
    }

    private fun ConfigurationSection.requiredText(path: String, description: String): String {
        return getString(path)?.trim()?.takeIf(String::isNotEmpty)
            ?: error("$description 不能为空。")
    }
}
