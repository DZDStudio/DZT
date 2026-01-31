package cn.tj.dzd.mc.dzt.teleport.je

import cn.tj.dzd.mc.dzt.mapping.tables.dtp.DTPBack
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.deleteDTPBack
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.getDTPBackList
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import taboolib.expansion.submitChain
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem

/**
 * 打开返回死亡点菜单
 * @param pl 玩家
 */
fun openBackJEMenu(pl: Player) {
    submitChain {
        val backList = async {
            pl.getDTPBackList()
        }

        sync {
            pl.openMenu<PageableChest<DTPBack>>("返回死亡点") {
                rows(6)
                map(
                    "#########",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "###B#C###"
                )

                set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
                slotsBy('@')
                elements { backList }
                onGenerate { _, element, _, _ ->
                    val time = element.time
                    val world = element.location.world
                    val x = element.location.x
                    val y = element.location.y
                    val z = element.location.z
                    
                    buildItem(XMaterial.RED_BED) {
                        name = "&c死亡记录: $time"
                        lore += "§7世界: §e$world"
                        lore += "§7坐标: §e$x, $y, $z"
                        lore += "§a左键单击传送，Shift+右键单击删除"
                        colored()
                    }
                }
                onClick { event, element ->
                    val time = element.time
                    when (event.clickEvent().click) {
                        ClickType.LEFT -> {
                            pl.teleport(element.location)
                            pl.sendMessage("§a已传送到死亡地点[$time]！")

                            pl.closeInventory()
                        }
                        ClickType.SHIFT_RIGHT -> {
                            submitChain {
                                async {
                                    pl.deleteDTPBack(time)
                                }
                                sync {
                                    pl.sendMessage("§a已删除死亡记录[$time]！")
                                    pl.closeInventory()
                                    openBackJEMenu(pl)
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // 设置下一页按钮
                setNextPage(49) { page, hasNextPage ->
                    buildItem(if (hasNextPage) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                        name = if (hasNextPage) "§a下一页" else "§7没有下一页了"
                        lore += "§7当前页: §e${page + 1}"
                    }
                }

                // 设置上一页按钮
                setPreviousPage(47) { page, hasPreviousPage ->
                    buildItem(if (hasPreviousPage) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                        name = if (hasPreviousPage) "§a上一页" else "§7已经是第一页了"
                        lore += "§7当前页: §e${page + 1}"
                    }
                }
            }
        }
    }
}