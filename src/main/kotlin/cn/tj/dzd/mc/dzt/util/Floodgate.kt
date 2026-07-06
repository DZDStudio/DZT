package cn.tj.dzd.mc.dzt.util

import org.bukkit.entity.Player
import org.geysermc.geyser.api.GeyserApi
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
 * 获取玩家网络延迟。
 *
 * Java 玩家使用 Bukkit/Paper 的 ping；基岩版玩家优先使用 Geyser 会话 ping，
 * 避免只看到 Floodgate/Geyser 到 Java 服务端之间的本地延迟。
 *
 * @return 玩家延迟，单位为毫秒；Geyser 会话不可用时回退到 Bukkit/Paper ping。
 */
fun Player.networkPing(): Int {
    if (!isBePlayer()) {
        return ping
    }

    return runCatching {
        GeyserApi.api().connectionByUuid(uniqueId)?.ping()
    }.getOrNull() ?: ping
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
