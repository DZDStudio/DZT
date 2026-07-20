package cn.tj.dzd.mc.dzt.teleport.service

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Resolves a Bukkit world name or namespaced key for platform-bound teleport operations.
 *
 * TabooLib exposes world names but does not provide an equivalent typed [org.bukkit.World] lookup.
 */
internal fun resolveWorld(world: String) = Bukkit.getWorld(world)
    ?: NamespacedKey.fromString(world)?.let { Bukkit.getWorld(it) }
