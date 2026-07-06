package cn.tj.dzd.mc.dzt.util

import org.bukkit.Bukkit
import org.bukkit.Location as BukkitLocation
import taboolib.common.util.Location as TabooLocation

/**
 * 将 Bukkit 的 [BukkitLocation] 转换为 TabooLib 的 [TabooLocation]。
 *
 * @return 转换后的 TabooLib 坐标对象，世界名为空时保持为空。
 */
fun BukkitLocation.toTabooLocation(): TabooLocation {
    return TabooLocation(world?.name, x, y, z, yaw, pitch)
}

/**
 * 将 TabooLib 的 [TabooLocation] 转换为 Bukkit 的 [BukkitLocation]。
 *
 * @return 转换后的 Bukkit 坐标对象，世界不存在或为空时世界对象为空。
 */
fun TabooLocation.toBukkitLocation(): BukkitLocation {
    return BukkitLocation(world?.let { Bukkit.getWorld(it) }, x, y, z, yaw, pitch)
}

/**
 * 坐标转换工具类。
 *
 * 用于在 Bukkit 坐标对象与 TabooLib 坐标对象之间进行互相转换。
 */
object LocationUtil {

    /**
     * 将 Bukkit 坐标对象转换为 TabooLib 坐标对象。
     *
     * @param location Bukkit 坐标对象。
     * @return 转换后的 TabooLib 坐标对象。
     */
    fun toTabooLocation(location: BukkitLocation): TabooLocation {
        return location.toTabooLocation()
    }

    /**
     * 将 TabooLib 坐标对象转换为 Bukkit 坐标对象。
     *
     * @param location TabooLib 坐标对象。
     * @return 转换后的 Bukkit 坐标对象。
     */
    fun toBukkitLocation(location: TabooLocation): BukkitLocation {
        return location.toBukkitLocation()
    }
}
