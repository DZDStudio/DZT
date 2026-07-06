package cn.tj.dzd.mc.dzt.ui

import org.bukkit.event.inventory.ClickType
import taboolib.module.ui.ClickEvent

/**
 * 读取 TabooLib 菜单点击的 Bukkit 点击类型。
 *
 * TabooLib 的虚拟背包会产生 `VIRTUAL` 动作，此时不能调用 [ClickEvent.clickEvent]；
 * 该接口会同时兼容普通点击与虚拟点击，拖拽事件则返回 null。
 *
 * @return Bukkit 点击类型；拖拽或无法识别时返回 null。
 */
fun ClickEvent.bukkitClickType(): ClickType? {
    return clickEventOrNull()?.click ?: virtualEventOrNull()?.clickType
}
