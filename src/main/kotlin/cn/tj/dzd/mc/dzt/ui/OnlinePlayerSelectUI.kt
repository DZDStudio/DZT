package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.avatarUrl
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import java.util.UUID

/**
 * 在线玩家选择完成后的回调。
 *
 * 回调接收打开 UI 的玩家与被选中的在线玩家，并在打开者所属的 Folia 实体线程执行。
 */
typealias OnlinePlayerSelectCallback = Player.(Player) -> Unit

/**
 * 在线玩家选择 UI 的返回回调。
 *
 * 回调在打开者所属的 Folia 实体线程执行。
 */
typealias OnlinePlayerSelectBackCallback = Player.() -> Unit

/**
 * 跨端在线玩家选择 UI。
 *
 * Java 版玩家使用头像分页箱子菜单，基岩版玩家使用 Floodgate/Geyser 表单。
 */
object OnlinePlayerSelectUI {

    private const val DEFAULT_TITLE = "§l§6选择在线玩家"

    /**
     * 打开在线玩家选择 UI。
     *
     * 当前玩家始终从候选列表排除。若玩家在点击后已经离线，界面会提示并刷新候选列表。
     *
     * @param player 要打开界面的玩家。
     * @param title 界面标题。
     * @param description 基岩版候选列表上方的说明文字。
     * @param emptyMessage 没有其他在线玩家时显示的文字。
     * @param selectLore Java 版玩家头像物品上的操作说明。
     * @param backLabel 返回按钮文字。
     * @param onBack 点击返回按钮后的回调。
     * @param onSelect 选择在线玩家后的回调。
     */
    fun open(
        player: Player,
        title: String = DEFAULT_TITLE,
        description: String = "请选择一名在线玩家。",
        emptyMessage: String = "当前没有其他在线玩家。",
        selectLore: String = "§7点击选择该玩家",
        backLabel: String = "§l§e返回",
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        player.foliaRun {
            val targets = Bukkit.getOnlinePlayers()
                .asSequence()
                .filter { it.uniqueId != uniqueId }
                .map { OnlinePlayerOption(it.uniqueId, it.name, it.avatarUrl()) }
                .sortedBy { it.name.lowercase() }
                .toList()

            if (isBePlayer()) {
                openBedrock(
                    this,
                    targets,
                    title,
                    description,
                    emptyMessage,
                    selectLore,
                    backLabel,
                    onBack,
                    onSelect,
                )
            } else {
                openJava(
                    this,
                    targets,
                    title,
                    description,
                    emptyMessage,
                    selectLore,
                    backLabel,
                    onBack,
                    onSelect,
                )
            }
        }
    }

    private fun openJava(
        player: Player,
        targets: List<OnlinePlayerOption>,
        title: String,
        description: String,
        emptyMessage: String,
        selectLore: String,
        backLabel: String,
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        player.openMenu<PageableChest<OnlinePlayerOption>>(title) {
            rows(6)
            virtualize()

            map(
                "R###M####",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<#>###"
            )

            onClick(lock = true) {}
            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set('M', XMaterial.YELLOW_STAINED_GLASS_PANE) {
                name = title
                if (targets.isEmpty()) {
                    lore += "§7$emptyMessage"
                } else {
                    lore += "§7$description"
                }
            }
            set('R', buildItem(XMaterial.BARREL) { name = backLabel }) {
                player.onBack()
            }

            slotsBy('@')
            elements { targets }
            onGenerate { _, target, _, _ ->
                buildItem(XMaterial.PLAYER_HEAD) {
                    name = "§6${target.name}"
                    lore += selectLore
                    skullOwner = target.name
                }
            }
            onClick { _, target ->
                player.foliaRun {
                    closeInventory()
                    select(
                        this,
                        target,
                        title,
                        description,
                        emptyMessage,
                        selectLore,
                        backLabel,
                        onBack,
                        onSelect,
                    )
                }
            }

            setPreviousPage(48) { _, hasPrevious ->
                buildItem(if (hasPrevious) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasPrevious) "§a上一页" else "§7已经是第一页"
                }
            }
            setNextPage(50) { _, hasNext ->
                buildItem(if (hasNext) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasNext) "§a下一页" else "§7已经是最后一页"
                }
            }
        }
    }

    private fun openBedrock(
        player: Player,
        targets: List<OnlinePlayerOption>,
        title: String,
        description: String,
        emptyMessage: String,
        selectLore: String,
        backLabel: String,
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        val form = SimpleForm.builder()
            .title(title)
            .content(if (targets.isEmpty()) emptyMessage else description)
            .button(backLabel, FormImage.Type.PATH, "textures/ui/box_ride.png")

        targets.forEach { target ->
            form.button(target.name, FormImage.Type.URL, target.avatarUrl)
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            player.foliaRun {
                if (clicked == 0) {
                    onBack()
                    return@foliaRun
                }

                val target = targets.getOrNull(clicked - 1) ?: return@foliaRun
                select(
                    this,
                    target,
                    title,
                    description,
                    emptyMessage,
                    selectLore,
                    backLabel,
                    onBack,
                    onSelect,
                )
            }
        }
        player.sendForm(form)
    }

    private fun select(
        player: Player,
        selected: OnlinePlayerOption,
        title: String,
        description: String,
        emptyMessage: String,
        selectLore: String,
        backLabel: String,
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        val target = Bukkit.getPlayer(selected.uuid)
        if (target == null || !target.isOnline) {
            player.sendDZTError("玩家已离线。")
            open(
                player,
                title,
                description,
                emptyMessage,
                selectLore,
                backLabel,
                onBack,
                onSelect,
            )
            return
        }

        player.onSelect(target)
    }
}

private data class OnlinePlayerOption(
    val uuid: UUID,
    val name: String,
    val avatarUrl: String,
)
