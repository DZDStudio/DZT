package cn.tj.dzd.mc.dzt.teleport.be

import cn.tj.dzd.mc.dzt.Floodgate.getFloodgatePlayer
import cn.tj.dzd.mc.dzt.teleport.je.openTPAConfirmJEMenu
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import org.geysermc.floodgate.api.player.FloodgatePlayer
import taboolib.platform.util.onlinePlayers

/**
 * 打开 TPA 菜单
 * @param pl 请求传送的玩家
 */
fun openTPABEMenu(pl: Player, fpl: FloodgatePlayer) {
    val onlinePlayerList: List<Player> = onlinePlayers.filter { it.name != pl.name }

    val fm = SimpleForm.builder()
    fm.title("§l§6玩家")
    fm.content("请选择要传送到的玩家：")
    for (player in onlinePlayerList) {
        fm.button(player.name, FormImage.Type.URL, "https://heads-mc.dzd.tj.cn/avatar/${player.name}")
    }
    fm.validResultHandler {
        val tpl = onlinePlayerList[it.clickedButtonId()]
        if (!tpl.isOnline) {
            pl.sendMessage("§c玩家不存在！")
        }
        if (tpl.getFloodgatePlayer() == null) {
            openTPAConfirmJEMenu(pl, tpl)
        } else {
            openTPAConfirmBEMenu(pl, tpl)
        }
    }
    fpl.sendForm(fm)
}

/**
 * 打开 TPA 确认菜单
 * @param pl 请求传送的玩家
 * @param tpl 被请求传送的玩家
 */
fun openTPAConfirmBEMenu(pl: Player, tpl: Player) {
    val tfpl = tpl.getFloodgatePlayer() ?: return
    pl.sendMessage("§a已向 ${tpl.name} 发送传送请求。")
    tfpl.sendForm(ModalForm.builder()
        .title("§l§6请求传送")
        .content("&6${pl.name} 请求传送至您的位置")
        .button1("§b同意")
        .button2("§c拒绝")
        .validResultHandler { response ->
            when (response.clickedButtonId()) {
                0 -> {
                    pl.teleport(tpl.location)
                    pl.sendMessage("§a${tpl.name}同意了您的传送请求。")
                    tpl.sendMessage("§a已同意${pl.name}的传送请求。")
                }
                1 -> {
                    pl.sendMessage("§c${tpl.name}拒绝了您的传送请求。")
                    tpl.sendMessage("§c已拒绝${pl.name}的传送请求。")
                }
            }
        }
    )
}