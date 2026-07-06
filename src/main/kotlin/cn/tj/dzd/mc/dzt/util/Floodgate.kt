package cn.tj.dzd.mc.dzt.util

import org.bukkit.entity.Player
import org.geysermc.cumulus.form.Form
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.form.util.FormBuilder
import org.geysermc.floodgate.api.FloodgateApi

var floodgateApi = FloodgateApi.getInstance()

/**
 * 玩家是否为基岩版玩家
 */
fun Player.isBePlayer(): Boolean {
    return floodgateApi.isFloodgatePlayer(uniqueId)
}

/**
 * 发送表单
 */
fun Player.sendForm(fm: SimpleForm.Builder) {
    floodgateApi.sendForm(uniqueId, fm)
}

/**
 * 发送任意 Cumulus 表单构建器。
 *
 * @param fm 表单构建器。
 * @return 表单是否成功交给 Floodgate 发送。
 */
fun Player.sendForm(fm: FormBuilder<*, *, *>): Boolean {
    return floodgateApi.sendForm(uniqueId, fm)
}

/**
 * 发送已构建的 Cumulus 表单。
 *
 * @param fm 表单实例。
 * @return 表单是否成功交给 Floodgate 发送。
 */
fun Player.sendForm(fm: Form): Boolean {
    return floodgateApi.sendForm(uniqueId, fm)
}
