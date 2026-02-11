package cn.tj.dzd.mc.dzt.menu

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.mapping.getDZDPlayer
import cn.tj.dzd.mc.dzt.menu.be.openMainBEMenu
import cn.tj.dzd.mc.dzt.menu.je.openMainJEMenu
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.function.ThrottleFunction
import taboolib.common.function.throttle
import taboolib.common.platform.event.SubscribeEvent
import taboolib.expansion.submitChain
import taboolib.library.xseries.XMaterial
import taboolib.platform.util.buildItem
import taboolib.platform.util.hasItem
import taboolib.platform.util.hasLore
import taboolib.platform.util.runTask
import java.util.*

val throttledActionMap = mutableMapOf<UUID, ThrottleFunction.Singleton>()

fun openMenuUI(dp: DZDPlayer) {
    dp.pl.runTask({
        submitChain {
            if (dp.isJE()) {
                sync { openMainJEMenu(dp) }
            } else {
                sync { openMainBEMenu(dp) }
            }
        }
    })
}

/**
 * 打开 DTP 菜单
 */
fun Player.openMenu() {
    throttledActionMap[uniqueId]?.let { it() }
}

val menuItem = buildItem(XMaterial.CLOCK) {
    itemName = "&d菜单"
    lore.add("§a使用物品以打开菜单")
    lore.add("#MENU#")
    unique()
    colored()
}

private object Listener {
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val dp = event.player.getDZDPlayer()
        val iv = dp.pl.inventory
        if (!iv.hasItem(1) { item ->
                item.hasLore("#MENU#")
            }){
            dp.giveItem(menuItem)
            dp.sendSuccess("欢迎来到 DZDGame，已给予您菜单物品!")
        }

        throttledActionMap[dp.pl.uniqueId] = throttle(500) {
            openMenuUI(dp)
        }
    }

    @SubscribeEvent
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val pl = event.player
        val it = event.item

        if (it != null && it.hasLore("#MENU#")) {
            pl.openMenu()
            event.isCancelled = true
        }
    }

    @SubscribeEvent
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val pl = event.player

        throttledActionMap.remove(pl.uniqueId)
    }
}