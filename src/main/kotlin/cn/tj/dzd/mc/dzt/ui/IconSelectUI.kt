package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.Icon
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.platform.util.buildItem

/**
 * 图标选择回调。
 *
 * 回调接收打开 UI 的玩家与被选中的图标。
 */
typealias IconSelectCallback = Player.(Icon) -> Unit

/**
 * 跨端图标选择 UI。
 *
 * Java 版玩家使用 [JavaPageableChestUI]，基岩版玩家使用 Floodgate/Geyser 表单。
 */
object IconSelectUI {

    private const val DEFAULT_TITLE = "§l§6选择图标"

    /**
     * 根据玩家客户端类型打开图标选择 UI。
     *
     * @param player 目标玩家。
     * @param title UI 标题。
     * @param icons 可选择的图标列表。
     * @param onSelect 选择图标后的回调。
     */
    fun open(
        player: Player,
        title: String = DEFAULT_TITLE,
        icons: List<Icon> = Icon.entries.toList(),
        onSelect: IconSelectCallback,
    ) {
        if (player.isBePlayer()) {
            openBe(player, title, icons, onSelect)
        } else {
            openJe(player, title, icons, onSelect)
        }
    }

    /**
     * 打开 Java 版图标选择 UI。
     *
     * @param player 目标玩家。
     * @param title 箱子菜单标题。
     * @param icons 可选择的图标列表。
     * @param onSelect 选择图标后的回调。
     */
    fun openJe(
        player: Player,
        title: String = DEFAULT_TITLE,
        icons: List<Icon> = Icon.entries.toList(),
        onSelect: IconSelectCallback,
    ) {
        require(icons.isNotEmpty()) { "图标列表不能为空" }

        val iconByMaterial = icons.associateBy { it.jeMaterial }
        JavaPageableChestUI.open(player, title, icons.map { it.toJeItem() }) {
            onClick { element ->
                val icon = iconByMaterial[element.type] ?: return@onClick
                clicker.foliaCloseInventory()
                player.onSelect(icon)
            }
        }
    }

    /**
     * 打开基岩版图标选择 UI。
     *
     * @param player 目标玩家。
     * @param title 表单标题。
     * @param icons 可选择的图标列表。
     * @param onSelect 选择图标后的回调。
     */
    fun openBe(
        player: Player,
        title: String = DEFAULT_TITLE,
        icons: List<Icon> = Icon.entries.toList(),
        onSelect: IconSelectCallback,
    ) {
        require(icons.isNotEmpty()) { "图标列表不能为空" }

        val form = SimpleForm.builder()
            .title(title)

        icons.forEach { icon ->
            form.button(icon.displayName, FormImage.Type.PATH, icon.beTexturePath)
        }

        form.validResultHandler { response ->
            val icon = icons.getOrNull(response.clickedButtonId()) ?: return@validResultHandler
            player.onSelect(icon)
        }
        player.sendForm(form)
    }

    private fun Icon.toJeItem(): ItemStack {
        return buildItem(jeMaterial) {
            name = "§e$displayName"
            lore += "§7$jeName"
            lore += "§8$beTexturePath"
            lore += "§a点击选择"
        }
    }
}

/**
 * 根据玩家客户端类型打开图标选择 UI。
 *
 * @param title UI 标题。
 * @param icons 可选择的图标列表。
 * @param onSelect 选择图标后的回调。
 */
fun Player.openIconSelectUI(
    title: String = "§l§6选择图标",
    icons: List<Icon> = Icon.entries.toList(),
    onSelect: IconSelectCallback,
) {
    IconSelectUI.open(this, title, icons, onSelect)
}

/**
 * 打开 Java 版图标选择 UI。
 *
 * @param title 箱子菜单标题。
 * @param icons 可选择的图标列表。
 * @param onSelect 选择图标后的回调。
 */
fun Player.openIconSelectJEUI(
    title: String = "§l§6选择图标",
    icons: List<Icon> = Icon.entries.toList(),
    onSelect: IconSelectCallback,
) {
    IconSelectUI.openJe(this, title, icons, onSelect)
}

/**
 * 打开基岩版图标选择 UI。
 *
 * @param title 表单标题。
 * @param icons 可选择的图标列表。
 * @param onSelect 选择图标后的回调。
 */
fun Player.openIconSelectBEUI(
    title: String = "§l§6选择图标",
    icons: List<Icon> = Icon.entries.toList(),
    onSelect: IconSelectCallback,
) {
    IconSelectUI.openBe(this, title, icons, onSelect)
}
