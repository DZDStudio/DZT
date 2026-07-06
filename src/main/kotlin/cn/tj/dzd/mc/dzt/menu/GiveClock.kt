package cn.tj.dzd.mc.dzt.menu

import cn.tj.dzd.mc.dzt.menu.ui.Menu
import cn.tj.dzd.mc.dzt.util.TextLogo
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.SubscribeEvent
import taboolib.library.xseries.XMaterial
import taboolib.platform.util.buildItem
import taboolib.platform.util.hasLore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object GiveClock {

    /**
     * 菜单钟在 lore 中使用的标记。
     *
     * 只有材质为钟且带有该 lore 的物品，才会被识别为菜单入口物品。
     */
    const val MENU_CLOCK_LORE = "MENU"

    private val dropActions = setOf(
        InventoryAction.DROP_ALL_CURSOR,
        InventoryAction.DROP_ONE_CURSOR,
        InventoryAction.DROP_ALL_SLOT,
        InventoryAction.DROP_ONE_SLOT
    )

    private val pendingRespawnMenuClock = ConcurrentHashMap.newKeySet<UUID>()

    private fun InventoryClickEvent.isTopInventorySlot(): Boolean {
        return rawSlot in 0 until view.topInventory.size
    }

    private fun InventoryClickEvent.isBottomInventorySlot(): Boolean {
        return clickedInventory == view.bottomInventory
    }

    private fun InventoryClickEvent.isDropClick(): Boolean {
        return click == ClickType.DROP ||
            click == ClickType.CONTROL_DROP ||
            click == ClickType.WINDOW_BORDER_LEFT ||
            click == ClickType.WINDOW_BORDER_RIGHT ||
            action in dropActions
    }

    private fun InventoryClickEvent.hasMenuClockInSwappedSlot(player: Player): Boolean {
        if (click == ClickType.NUMBER_KEY && hotbarButton in 0..8) {
            return player.inventory.getItem(hotbarButton).isMenuClock()
        }

        if (click == ClickType.SWAP_OFFHAND) {
            return player.inventory.itemInOffHand.isMenuClock()
        }

        return false
    }

    private fun InventoryClickEvent.isMovingMenuClockOutOfInventory(player: Player): Boolean {
        if (isDropClick() && (currentItem.isMenuClock() || cursor.isMenuClock())) {
            return true
        }

        if (isTopInventorySlot() && (cursor.isMenuClock() || hasMenuClockInSwappedSlot(player))) {
            return true
        }

        return isBottomInventorySlot() &&
            currentItem.isMenuClock() &&
            action == InventoryAction.MOVE_TO_OTHER_INVENTORY
    }

    /**
     * 创建一个新的菜单钟物品。
     *
     * @return 带有菜单名称、说明 lore 和 [MENU_CLOCK_LORE] 标记的钟。
     */
    fun createMenuClock(): ItemStack {
        return buildItem(XMaterial.CLOCK) {
            name = TextLogo
            lore += "§a使用物品以打开菜单"
            lore += MENU_CLOCK_LORE
            unique()
        }
    }

    /**
     * 判断物品是否是菜单钟。
     *
     * @return 当物品材质为钟且包含 [MENU_CLOCK_LORE] lore 标记时返回 `true`。
     */
    fun ItemStack?.isMenuClock(): Boolean {
        return this != null && type == Material.CLOCK && hasLore(MENU_CLOCK_LORE)
    }

    /**
     * 判断玩家背包中是否已经持有菜单钟。
     *
     * @return 玩家任意背包槽位中存在菜单钟时返回 `true`。
     */
    fun Player.hasMenuClock(): Boolean {
        return inventory.contents.any { it.isMenuClock() }
    }

    /**
     * 在玩家没有菜单钟时给予一个菜单钟。
     *
     * @return 成功给予菜单钟时返回 `true`，玩家已经拥有菜单钟或背包已满时返回 `false`。
     */
    fun Player.giveMenuClockIfAbsent(): Boolean {
        if (hasMenuClock()) {
            return false
        }

        val emptySlot = inventory.firstEmpty()
        if (emptySlot == -1) {
            return false
        }

        inventory.setItem(emptySlot, createMenuClock())
        return true
    }

    /**
     * 玩家进入服务器时补发菜单钟。
     */
    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.giveMenuClockIfAbsent()
    }

    /**
     * 玩家使用菜单钟时打开主菜单。
     */
    @SubscribeEvent
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item

        if (!item.isMenuClock()) {
            return
        }

        event.isCancelled = true
        Menu.run {
            player.openMenu()
        }
    }

    /**
     * 玩家按下 Shift + F 时打开主菜单，并阻止原本的主副手交换。
     */
    @SubscribeEvent
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (!player.isSneaking) {
            return
        }

        event.isCancelled = true
        Menu.run {
            player.openMenu()
        }
    }
}
