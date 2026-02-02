package cn.tj.dzd.mc.dzt.teleport.je

import cn.tj.dzd.mc.dzt.mapping.tables.dtp.DTPHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.addDTPHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.deleteDTPHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.getDTPHomeList
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import taboolib.expansion.submitChain
import taboolib.library.xseries.XMaterial
import taboolib.module.nms.inputSign
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem

/**
 * 打开传送点管理
 * @param pl 玩家
 */
fun openHomeJEMenu(pl: Player) {
    submitChain {
        val homeList = async {
            pl.getDTPHomeList()
        }

        sync {
            pl.openMenu<PageableChest<DTPHome>>("传送点") {
                rows(6)
                map(
                    "####A####",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "#@@@@@@@#",
                    "###B#C###"
                )

                set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
                set('A', buildItem(XMaterial.BOOK) {
                    name = "添加传送点"
                    colored()
                }) {
                    pl.sendMessage("§a请在告示牌第一行输新传送点名称")

                    pl.inputSign(arrayOf("", "^^^^^^^^^^", "||||||||||", "§7在第一行输新传送点名称")) { lines ->
                        val homeName = lines[0].trim()

                        try {
                            pl.addDTPHome(homeName, pl.location)
                            pl.sendMessage("§a已添加传送点[$homeName]！")
                        } catch (e: Exception) {
                            pl.sendMessage("§c" + e.message)
                        }
                        openHomeJEMenu(pl)
                    }
                }
                slotsBy('@')
                elements { homeList }
                onGenerate { _, element, _, _ ->
                    val world = element.location.world
                    val x = element.location.x
                    val y = element.location.y
                    val z = element.location.z

                    buildItem(XMaterial.RED_BED) {
                        name = "&c名称: ${element.name}"
                        world?.let { lore += "§7世界: §e${it.name}" }
                        lore += "§7坐标: §e$x, $y, $z"
                        lore += "§a左键单击传送，Shift+右键单击删除"
                        colored()
                    }
                }

                onClick { event, element ->
                    val name = element.name
                    when (event.clickEvent().click) {
                        ClickType.LEFT -> {
                            pl.teleport(element.location)
                            pl.sendMessage("§a已传送到传送点[$name]！")

                            pl.closeInventory()
                        }
                        ClickType.SHIFT_RIGHT -> {
                            // 异步执行数据库删除操作
                            submitChain {
                                async {
                                    pl.deleteDTPHome(name)
                                }
                                sync {
                                    pl.sendMessage("§a已删除传送点[$name]！")
                                    pl.closeInventory()
                                    openHomeJEMenu(pl)
                                }
                            }
                        }
                        else -> {}
                    }
                }

                setNextPage(49) { page, hasNextPage ->
                    buildItem(if (hasNextPage) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                        name = if (hasNextPage) "§a下一页" else "§7没有下一页了"
                        lore += "§7当前页: §e${page + 1}"
                    }
                }

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