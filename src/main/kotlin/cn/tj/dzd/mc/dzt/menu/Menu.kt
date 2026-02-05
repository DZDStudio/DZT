package cn.tj.dzd.mc.dzt.menu

import cn.tj.dzd.mc.dzt.Floodgate.getFloodgatePlayer
import cn.tj.dzd.mc.dzt.teleport.openDTPMenu
import org.bukkit.Bukkit.getPlayer
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.common.LifeCycle
import taboolib.common.function.ThrottleFunction
import taboolib.common.function.throttle
import taboolib.common.platform.Awake
import taboolib.common.platform.command.simpleCommand
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.util.isPlayer
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.util.*
import java.util.UUID

@Awake(LifeCycle.ENABLE)
fun registerCommands() {
    simpleCommand("menu") { sender, _ ->
        if (sender.isPlayer()) {
            getPlayer(sender.name)?.openMenu()
        }
    }
}

val throttledActionMap = mutableMapOf<UUID, ThrottleFunction.Singleton>()
/**
 * 打开 DTP 菜单
 */
fun Player.openMenu() {
    if (!throttledActionMap.containsKey(uniqueId)) {
        throttledActionMap[uniqueId] = throttle(500) {
            this.runTask({
                val fpl = getFloodgatePlayer()
                if (fpl == null) {
                    // JE
                    openMenu<Chest>("菜单") {
                        rows(3)

                        set(13, buildItem(XMaterial.ENDER_PEARL) {
                            name = "&6传送"
                            colored()
                        }) {
                            openDTPMenu()
                        }
                    }
                } else {
                    // BE
                    fpl.sendForm(SimpleForm.builder()
                        .title("菜单")
                        .button("传送", FormImage.Type.PATH, "textures/ui/mashup_world.png")
                        .validResultHandler({
                            when (it.clickedButtonId()) {
                                0 -> {
                                    openDTPMenu()
                                }
                            }
                        })
                    )
                }
            })
        }
    }

    throttledActionMap[uniqueId]?.let { it() }
}

val menuItem = buildItem(XMaterial.CLOCK) {
    itemName = "&d菜单"
    lore.add("§a使用物品以打开菜单")
    lore.add("#MENU#")
    shiny()
    unique()
    colored()
}

private object Listener {
    @SubscribeEvent()
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

    // 当玩家使用菜单物品时打开菜单
    @SubscribeEvent()
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