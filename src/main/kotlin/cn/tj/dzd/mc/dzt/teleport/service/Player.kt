package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.UUID



internal fun resolveWorld(world: String) = Bukkit.getWorld(world)
    ?: NamespacedKey.fromString(world)?.let { Bukkit.getWorld(it) }
