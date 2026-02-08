package cn.tj.dzd.mc.dzt.menu

import cn.tj.dzd.mc.dzt.GeyserUtils.getFloodgatePlayer
import cn.tj.dzd.mc.dzt.menu.be.openMainBEMenu
import cn.tj.dzd.mc.dzt.menu.je.openMainJEMenu
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.function.ThrottleFunction
import taboolib.common.function.throttle
import taboolib.common.platform.event.SubscribeEvent
import taboolib.library.xseries.XMaterial
import taboolib.platform.util.*
import java.util.*

val throttledActionMap = mutableMapOf<UUID, ThrottleFunction.Singleton>()

fun openMenuUI(pl: Player) {
    pl.runTask({
        val fpl = pl.getFloodgatePlayer()
        if (fpl == null) {
            openMainJEMenu(pl)
        } else {
            openMainBEMenu(pl, fpl)
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
        val pl = event.player
        val iv = pl.inventory
        if (!iv.hasItem(1) { item ->
                item.hasLore("#MENU#")
            }){
            pl.giveItem(menuItem)
            pl.sendMessage("§a欢迎来到 DZDGame，已给予您菜单物品!")
        }

        throttledActionMap[pl.uniqueId] = throttle(500) {
            openMenuUI(pl)
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