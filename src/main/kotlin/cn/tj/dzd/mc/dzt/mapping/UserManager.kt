package cn.tj.dzd.mc.dzt.mapping

import cn.tj.dzd.mc.dzt.mapping.tables.UserMapping
import cn.tj.dzd.mc.dzt.mapping.tables.getNickName
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
        kickPlayer("§l§c非常抱歉，我们无法检索到您的 DZD 账户。\n请尝试重新加入服务器或联系运维人员！")
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