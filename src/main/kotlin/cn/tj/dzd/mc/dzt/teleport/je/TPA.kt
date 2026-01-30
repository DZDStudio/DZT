package cn.tj.dzd.mc.dzt.teleport.je

import cn.tj.dzd.mc.dzt.Floodgate.getFloodgatePlayer
import cn.tj.dzd.mc.dzt.teleport.be.openTPAConfirmBEMenu
import org.bukkit.Bukkit.getPlayer
import org.bukkit.entity.Player
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import taboolib.platform.util.onlinePlayers

/**
 * 打开 TPA 菜单
 * @param pl 请求传送的玩家
 */
fun openTPAJEMenu(pl: Player) {
    val onlinePlayerList: List<String> = onlinePlayers.filter { it.name != pl.name }.map { it.name }

    pl.openMenu<PageableChest<String>>("TPA 玩家间传送") {
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

        // 设置可用槽位（通过字符）
        slotsBy('@')

        // 设置元素列表
        elements { onlinePlayerList }

        // 生成每个元素对应的物品
        onGenerate { player, element, index, slot ->
            buildItem(XMaterial.PLAYER_HEAD) {
                name = "&6传送至 $element"
                skullOwner = element
                colored()
            }
        }

        // 元素点击事件
        onClick { _, element ->
            val tpl = getPlayer(element)

            if (tpl == null) {
                pl.sendMessage("§c玩家不存在！")
                return@onClick
            }

            if (tpl.getFloodgatePlayer() == null) {
                openTPAConfirmJEMenu(pl, tpl)
            } else {
                openTPAConfirmBEMenu(pl, tpl)
            }

            pl.closeInventory()
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

/**
 * 打开 TPA 确认菜单
 * @param pl 请求传送的玩家
 * @param tpl 被请求传送的玩家
 */
fun openTPAConfirmJEMenu(pl: Player, tpl: Player) {
    var isReq = false
    pl.sendMessage("§a已向 ${tpl.name} 发送传送请求。")
    tpl.openMenu<Chest>("请求传送") {
        rows(3)

        set(4, buildItem(XMaterial.PLAYER_HEAD) {
            name = "&6${pl.name} 请求传送至您的位置"
            skullOwner = pl.name
            colored()
        }) {}

        set(12, buildItem(XMaterial.DIAMOND) {
            name = "§b同意"
            lore += "§7点击后将同意${pl.name}请求传送至您的位置。"
        }) {
            if (isReq) {
                tpl.closeInventory()
                return@set
            }
            isReq = true

            pl.teleport(tpl.location)
            pl.sendMessage("§a${tpl.name}同意了您的传送请求。")
            tpl.sendMessage("§a已同意${pl.name}的传送请求。")
            tpl.closeInventory()
        }

        set(14, buildItem(XMaterial.REDSTONE) {
            name = "§c拒绝"
            lore += "§7点击后将拒绝${pl.name}请求传送至您的位置。"
        }) {
            if (isReq) {
                tpl.closeInventory()
                return@set
            }
            isReq = true

            pl.sendMessage("§c${tpl.name}拒绝了您的传送请求。")
            tpl.sendMessage("§c已拒绝${pl.name}的传送请求。")
            tpl.closeInventory()
        }

        onClose {
            if (isReq) {
                return@onClose
            }
            isReq = true

            pl.sendMessage("§c${tpl.name}拒绝了您的传送请求。")
            tpl.sendMessage("§c已拒绝${pl.name}的传送请求。")
        }
    }
}