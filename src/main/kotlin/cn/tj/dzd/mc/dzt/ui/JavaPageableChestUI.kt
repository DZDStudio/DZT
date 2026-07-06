package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.ClickEvent
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem

object JavaPageableChestUI {

    private const val BACK_SLOT = 0
    private const val CLOSE_SLOT = 8
    private const val PREVIOUS_PAGE_SLOT = 48
    private const val NEXT_PAGE_SLOT = 50

    class Scope internal constructor() {
        internal var clickHandler: (ClickEvent.(ItemStack) -> Unit)? = null
        internal var backHandler: (ClickEvent.() -> Unit)? = null

        fun onClick(block: ClickEvent.(element: ItemStack) -> Unit) {
            clickHandler = block
        }

        fun onBack(block: ClickEvent.() -> Unit) {
            backHandler = block
        }
    }

    fun open(
        player: Player,
        title: String,
        items: List<ItemStack>,
        block: Scope.() -> Unit = {}
    ) {
        val scope = Scope().apply(block)
        val snapshot = items.map { it.clone() }

        player.openMenu<PageableChest<ItemStack>>(title) {
            rows(6)
            hidePlayerInventory()

            map(
                "#########",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<#>###"
            )

            set('#', XMaterial.BLACK_STAINED_GLASS_PANE) {
                name = " "
            }

            slotsBy('@')

            elements {
                snapshot
            }

            onGenerate { _, element, _, _ ->
                element.clone()
            }

            onClick { event, element ->
                scope.clickHandler?.invoke(event, element)
            }

            scope.backHandler?.let { handler ->
                set(BACK_SLOT, buildItem(XMaterial.ARROW) {
                    name = "§a返回"
                }) {
                    handler.invoke(this)
                }
            }

            set(CLOSE_SLOT, buildItem(XMaterial.BARRIER) {
                name = "§c关闭"
            }) {
                clicker.foliaCloseInventory()
            }

            setPreviousPage(PREVIOUS_PAGE_SLOT) { _, hasPrevious ->
                buildItem(if (hasPrevious) XMaterial.ARROW else XMaterial.BARRIER) {
                    name = if (hasPrevious) "§a上一页" else "§7第一页"
                }
            }

            setNextPage(NEXT_PAGE_SLOT) { _, hasNext ->
                buildItem(if (hasNext) XMaterial.ARROW else XMaterial.BARRIER) {
                    name = if (hasNext) "§a下一页" else "§7最后一页"
                }
            }
        }
    }
}

fun Player.JavaPageableChestUI(
    title: String,
    items: List<ItemStack>,
    block: JavaPageableChestUI.Scope.() -> Unit = {}
) {
    JavaPageableChestUI.open(this, title, items, block)
}
