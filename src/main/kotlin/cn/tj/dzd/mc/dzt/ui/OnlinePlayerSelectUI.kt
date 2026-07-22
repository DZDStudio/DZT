package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.avatarTarget
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendForm
import cn.tj.dzd.mc.dzt.util.toAvatarUrl
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import taboolib.platform.util.onlinePlayers
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 在线玩家选择完成后的回调。
 *
 * 回调接收打开 UI 的玩家与被选中的玩家快照，并在打开者所属的 Folia 实体线程执行。
 * 不会把另一个实体的 [Player] 句柄跨线程暴露给调用方；需要与目标玩家交互时，
 * 请通过 [OnlinePlayerSelection.withOnlinePlayer] 进入其实体线程。
 */
typealias OnlinePlayerSelectCallback = Player.(OnlinePlayerSelection) -> Unit

/**
 * 在线玩家选择 UI 的返回回调。
 *
 * 回调在打开者所属的 Folia 实体线程执行。
 */
typealias OnlinePlayerSelectBackCallback = Player.() -> Unit

/**
 * 在线玩家选择 UI 的可选次操作。
 *
 * Java 版通过 `Shift + 右键` 触发；基岩版在选择玩家后通过独立的操作表单触发。
 * 启用次操作后，基岩版的主操作也会显示在该操作表单中。
 *
 * @property javaLore Java 版玩家头像上显示的次操作说明。
 * @property bedrockPrimaryLabel 基岩版主操作按钮文字。
 * @property bedrockPrimaryIcon 基岩版主操作按钮图标路径。
 * @property bedrockSecondaryLabel 基岩版次操作按钮文字。
 * @property bedrockSecondaryIcon 基岩版次操作按钮图标路径。
 * @property onSelect 次操作回调；在打开者所属的 Folia 实体线程执行。
 */
data class OnlinePlayerSelectSecondaryAction(
    val javaLore: String,
    val bedrockPrimaryLabel: String,
    val bedrockPrimaryIcon: String,
    val bedrockSecondaryLabel: String,
    val bedrockSecondaryIcon: String,
    val onSelect: OnlinePlayerSelectCallback,
)

/**
 * 在线玩家选择结果的不可变快照。
 *
 * 字段均在目标玩家所属的 Folia 实体线程中采样，因此异步流程可以安全地持有该对象，
 * 而无需持有或跨线程读取 Bukkit [Player]。
 *
 * @property uuid 目标玩家 UUID。
 * @property name 目标玩家选择时的名称。
 * @property isBedrock 是否为基岩版客户端。
 */
data class OnlinePlayerSelection(
    val uuid: UUID,
    val name: String,
    val isBedrock: Boolean,
) {
    /**
     * 在该选择结果对应玩家的 Folia 实体线程执行操作。
     *
     * 此方法只将 Bukkit 玩家句柄用于调度，不会在调用方线程读取目标实体状态。
     *
     * @param block 要在目标玩家实体线程执行的操作。
     * @return 操作是否成功进入并完成执行；目标离线或实体调度器失效时完成为 false。
     */
    fun withOnlinePlayer(block: Player.() -> Unit): CompletableFuture<Boolean> {
        return runForOnlinePlayer(uuid, block)
    }
}

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
     * @param secondaryAction 可选次操作；Java 版由 `Shift + 右键` 触发，基岩版通过操作表单选择。
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
        secondaryAction: OnlinePlayerSelectSecondaryAction? = null,
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        open(
            player,
            OnlinePlayerSelectConfig(
                title = title,
                description = description,
                emptyMessage = emptyMessage,
                selectLore = selectLore,
                backLabel = backLabel,
                secondaryAction = secondaryAction,
                onBack = onBack,
                onSelect = onSelect,
            )
        )
    }

    private fun open(player: Player, config: OnlinePlayerSelectConfig) {
        player.foliaRun {
            loadTargets(
                this,
                uniqueId,
                onlinePlayers.toList(),
                config,
            )
        }
    }

    private fun loadTargets(
        player: Player,
        viewerId: UUID,
        candidates: List<Player>,
        config: OnlinePlayerSelectConfig,
    ) {
        val snapshots = candidates.map { candidate ->
            snapshotTarget(candidate, viewerId)
        }

        CompletableFuture.allOf(*snapshots.toTypedArray()).whenComplete { _, _ ->
            val targets = snapshots
                .mapNotNull { it.getNow(null) }
                .sortedBy { it.name.lowercase() }

            player.foliaRun {
                if (isBePlayer()) {
                    openBedrock(this, targets, config)
                } else {
                    openJava(this, targets, config)
                }
            }
        }
    }

    private fun snapshotTarget(candidate: Player, viewerId: UUID): CompletableFuture<OnlinePlayerOption?> {
        val snapshot = CompletableFuture<OnlinePlayerOption?>()
        candidate.foliaRun {
            if (uniqueId == viewerId) {
                snapshot.complete(null)
                return@foliaRun
            }

            snapshot.complete(
                OnlinePlayerOption(
                    uuid = uniqueId,
                    name = name,
                    avatarUrl = avatarTarget().toAvatarUrl(),
                    isBedrock = isBePlayer(),
                )
            )
        }.whenComplete { scheduled, error ->
            if (error != null || scheduled != true) {
                snapshot.complete(null)
            }
        }
        return snapshot
    }

    private fun openJava(
        player: Player,
        targets: List<OnlinePlayerOption>,
        config: OnlinePlayerSelectConfig,
    ) {
        player.openMenu<PageableChest<OnlinePlayerOption>>(config.title) {
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
                name = config.title
                if (targets.isEmpty()) {
                    lore += "§7${config.emptyMessage}"
                } else {
                    lore += "§7${config.description}"
                }
            }
            set('R', buildItem(XMaterial.BARREL) { name = config.backLabel }) {
                player.foliaRun {
                    config.onBack(this)
                }
            }

            slotsBy('@')
            elements { targets }
            onGenerate { _, target, _, _ ->
                buildItem(XMaterial.PLAYER_HEAD) {
                    name = "§6${target.name}"
                    lore += config.selectLore
                    config.secondaryAction?.let { lore += it.javaLore }
                    skullOwner = target.name
                }
            }
            onClick { event, target ->
                val onSelect = if (event.bukkitClickType() == ClickType.SHIFT_RIGHT) {
                    config.secondaryAction?.onSelect ?: config.onSelect
                } else {
                    config.onSelect
                }
                player.foliaRun {
                    closeInventory()
                    select(this, target, config, onSelect)
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
        config: OnlinePlayerSelectConfig,
    ) {
        val form = SimpleForm.builder()
            .title(config.title)
            .content(if (targets.isEmpty()) config.emptyMessage else config.description)
            .button(config.backLabel, FormImage.Type.PATH, "textures/ui/box_ride.png")

        targets.forEach { target ->
            form.button(target.name, FormImage.Type.URL, target.avatarUrl)
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            player.foliaRun {
                if (clicked == 0) {
                    config.onBack(this)
                    return@foliaRun
                }

                val target = targets.getOrNull(clicked - 1) ?: return@foliaRun
                if (config.secondaryAction == null) {
                    select(this, target, config, config.onSelect)
                } else {
                    openBedrockActionSelection(this, target, config)
                }
            }
        }
        player.sendForm(form)
    }

    private fun openBedrockActionSelection(
        player: Player,
        target: OnlinePlayerOption,
        config: OnlinePlayerSelectConfig,
    ) {
        val secondaryAction = config.secondaryAction ?: return
        player.sendForm(
            SimpleForm.builder()
                .title(config.title)
                .content("已选择 ${target.name}，请选择操作。")
                .button(
                    secondaryAction.bedrockPrimaryLabel,
                    FormImage.Type.PATH,
                    secondaryAction.bedrockPrimaryIcon,
                )
                .button(
                    secondaryAction.bedrockSecondaryLabel,
                    FormImage.Type.PATH,
                    secondaryAction.bedrockSecondaryIcon,
                )
                .button("返回玩家列表", FormImage.Type.PATH, "textures/ui/box_ride.png")
                .validResultHandler { response ->
                    player.foliaRun {
                        when (response.clickedButtonId()) {
                            0 -> select(this, target, config, config.onSelect)
                            1 -> select(this, target, config, secondaryAction.onSelect)
                            2 -> open(this, config)
                        }
                    }
                }
        )
    }

    private fun select(
        player: Player,
        selected: OnlinePlayerOption,
        config: OnlinePlayerSelectConfig,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        val selection = selected.toSelection()
        selection.withOnlinePlayer {
            player.foliaRun {
                onSelect(selection)
            }
        }.whenComplete { scheduled, error ->
            if (error != null || scheduled != true) {
                player.foliaRun {
                    targetUnavailable(this, config)
                }
            }
        }
    }

    private fun targetUnavailable(player: Player, config: OnlinePlayerSelectConfig) {
        player.sendDZTError("玩家已离线。")
        open(player, config)
    }
}

private data class OnlinePlayerSelectConfig(
    val title: String,
    val description: String,
    val emptyMessage: String,
    val selectLore: String,
    val backLabel: String,
    val secondaryAction: OnlinePlayerSelectSecondaryAction?,
    val onBack: OnlinePlayerSelectBackCallback,
    val onSelect: OnlinePlayerSelectCallback,
)

private data class OnlinePlayerOption(
    val uuid: UUID,
    val name: String,
    val avatarUrl: String,
    val isBedrock: Boolean,
) {
    fun toSelection(): OnlinePlayerSelection {
        return OnlinePlayerSelection(uuid, name, isBedrock)
    }
}
