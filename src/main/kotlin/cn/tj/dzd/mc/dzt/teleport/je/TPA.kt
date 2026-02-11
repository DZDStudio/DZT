package cn.tj.dzd.mc.dzt.teleport.je

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.mapping.onlineDZDPlayers
import cn.tj.dzd.mc.dzt.teleport.be.openTPAConfirmBEMenu
import cn.tj.dzd.mc.dzt.teleport.openTeleportJEMenu
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem

/**
 * 打开 TPA 菜单
 */
fun openTPAJEMenu(dp: DZDPlayer) {
    val onlinePlayerList: List<DZDPlayer> = onlineDZDPlayers.filter { it.name != dp.name }

    dp.pl.openMenu<PageableChest<DZDPlayer>>("§l§6玩家") {
        rows(6)

        map(
            "R###M####",
            "#@@@@@@@#",
            "#@@@@@@@#",
            "#@@@@@@@#",
            "#@@@@@@@#",
            "###B#C###"
        )

        onClick(lock = true) {}
        set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
        set('M', XMaterial.YELLOW_STAINED_GLASS_PANE) { name = "§l§6玩家" }
        set('R', buildItem(XMaterial.BARREL) { name = "§l§e返回上一页" }) { openTeleportJEMenu(dp) }

        slotsBy('@')
        elements { onlinePlayerList }
        onGenerate { _, element, _, _ ->
            buildItem(XMaterial.PLAYER_HEAD) {
                name = "§6传送至 §r${element.name}"
                skullOwner = element.pl.name
            }
        }
        onClick { _, tdp ->
            if (!tdp.isOnline()) {
                dp.sendError("玩家不存在！")
                return@onClick
            }

            if (dp.isJE()) {
                openTPAConfirmJEMenu(dp, tdp)
            } else {
                openTPAConfirmBEMenu(dp, tdp)
            }

            dp.pl.closeInventory()
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

/**
 * 打开 TPA 确认菜单
 */
fun openTPAConfirmJEMenu(dp: DZDPlayer, tdp: DZDPlayer) {
    var isReq = false
    dp.sendSuccess("已向 ${tdp.name} 发送传送请求。")
    tdp.openMenu<Chest>("请求传送") {
        rows(3)

        map(
            "####M####",
            "#  Y N  #",
            "#########"
        )

        onClick(lock = true) {}
        set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE){ name = " " })

        set('M', buildItem(XMaterial.PLAYER_HEAD) {
            name = "§6玩家 §r${dp.name} §6请求传送至您的位置"
            skullOwner = dp.pl.name
            colored()
        })

        set('Y', buildItem(XMaterial.DIAMOND) {
            name = "§b同意"
            lore += "§7点击后将同意${dp.name}请求传送至您的位置。"
        }) {
            if (isReq) {
                tdp.pl.closeInventory()
                return@set
            }
            isReq = true

            dp.teleport(tdp)
            dp.sendSuccess("${tdp.name} 同意了您的传送请求。")
            tdp.sendSuccess("已同意 ${dp.name} 的传送请求。")
            tdp.pl.closeInventory()
        }

        set('N', buildItem(XMaterial.REDSTONE) {
            name = "§c拒绝"
            lore += "§7点击后将拒绝 ${dp.name} 请求传送至您的位置。"
        }) {
            if (isReq) {
                tdp.closeInventory()
                return@set
            }
            isReq = true

            dp.sendError("${tdp.name} 拒绝了您的传送请求。")
            tdp.sendError("已拒绝 ${dp.name} 的传送请求。")
            tdp.closeInventory()
        }

        onClose {
            if (isReq) {
                return@onClose
            }
            isReq = true

            dp.sendError("${tdp.name} 拒绝了您的传送请求。")
            tdp.sendError("已拒绝 ${dp.name} 的传送请求。")
        }
    }
}