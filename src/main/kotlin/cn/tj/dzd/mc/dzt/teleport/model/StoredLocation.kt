package cn.tj.dzd.mc.dzt.teleport.model

/**
 * 不依赖 Bukkit 的持久化坐标快照。
 *
 * @property world 世界名称或命名空间键。
 * @property x X 坐标。
 * @property y Y 坐标。
 * @property z Z 坐标。
 */
data class StoredLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
)
