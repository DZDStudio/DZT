package cn.tj.dzd.mc.dzt.mapping

import cn.tj.dzd.mc.dzt.mapping.Tables.UserMapping
import cn.tj.dzd.mc.dzt.mapping.Tables.getNickName
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

val UIDCache = mutableMapOf<String, Number>()

fun Player.getUID(): Number {
    if (UIDCache.containsKey(name)) {
        return UIDCache[name]!!
    } else {
        throw Exception("用户不存在")
    }
}

private object UserManagerListener {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        // 注册用户
        UserMapping.addUser(event.name)

        // 缓存 UID
        UIDCache[event.name] = UserMapping.getUID(event.name) ?: -1
    }

    @SubscribeEvent()
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val pl = event.player
        pl.setDisplayName(pl.getNickName())
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        // 移除 UID 缓存
        UIDCache.remove(event.player.name)
    }
}