package cn.tj.dzd.mc.dzt.teleport.be

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.mapping.onlineDZDPlayers
import cn.tj.dzd.mc.dzt.teleport.je.openTPAConfirmJEMenu
import cn.tj.dzd.mc.dzt.teleport.openTeleportBEMenu
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage

/**
 * 打开 TPA 菜单
 */
fun openTPABEMenu(dp: DZDPlayer) {
    val onlineDZDPlayersList: List<DZDPlayer> = onlineDZDPlayers//.filter { it.name != dp.pl.name }

    val fm = SimpleForm.builder()
    fm.title("§l§6玩家")
    fm.content("请选择要传送到的玩家：")
    for (dp in onlineDZDPlayersList) {
        fm.button(dp.name, FormImage.Type.URL, "https://heads-mc.dzd.tj.cn/avatar/${dp.pl.name}")
    }
    fm.validResultHandler { result ->
        val tdp = onlineDZDPlayersList[result.clickedButtonId()]
        if (!tdp.isOnline()) {
            dp.sendError("玩家不存在！")
        }
        if (dp.isJE()) {
            openTPAConfirmJEMenu(dp, tdp)
        } else {
            openTPAConfirmBEMenu(dp, tdp)
        }
    }
    fm.closedResultHandler { _ ->
        openTeleportBEMenu(dp)
    }
    dp.sendForm(fm)
}

/**
 * 打开 TPA 确认菜单
 */
fun openTPAConfirmBEMenu(dp: DZDPlayer, tdp: DZDPlayer) {
    dp.sendSuccess("已向 ${tdp.name} 发送传送请求。")
    tdp.sendForm(ModalForm.builder()
        .title("§l§6请求传送")
        .content("§6${dp.name} 请求传送至您的位置")
        .button1("§b同意")
        .button2("§c拒绝")
        .validResultHandler { response ->
            when (response.clickedButtonId()) {
                0 -> {
                    dp.teleport(tdp.location)
                    dp.sendSuccess("${tdp.name}同意了您的传送请求。")
                    tdp.sendSuccess("已同意${dp.name}的传送请求。")
                }
                1 -> {
                    dp.sendError("§c${tdp.name}拒绝了您的传送请求。")
                    tdp.sendError("§c已拒绝${dp.name}的传送请求。")
                }
            }
        }
    )
}