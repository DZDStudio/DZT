package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.avatarTarget
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendForm
import cn.tj.dzd.mc.dzt.util.toAvatarUrl
import org.bukkit.entity.Player
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
            loadTargets(
                this,
                uniqueId,
                onlinePlayers.toList(),
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

    private fun loadTargets(
        player: Player,
        viewerId: UUID,
        candidates: List<Player>,
        title: String,
        description: String,
        emptyMessage: String,
        selectLore: String,
        backLabel: String,
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
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
                    openBedrock(this, targets, title, description, emptyMessage, selectLore, backLabel, onBack, onSelect)
                } else {
                    openJava(this, targets, title, description, emptyMessage, selectLore, backLabel, onBack, onSelect)
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
                player.foliaRun {
                    onBack()
                }
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
                    select(this, target, title, description, emptyMessage, selectLore, backLabel, onBack, onSelect)
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
                select(this, target, title, description, emptyMessage, selectLore, backLabel, onBack, onSelect)
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
        val selection = selected.toSelection()
        selection.withOnlinePlayer {
            player.foliaRun {
                onSelect(selection)
            }
        }.whenComplete { scheduled, error ->
            if (error != null || scheduled != true) {
                player.foliaRun {
                    targetUnavailable(this, title, description, emptyMessage, selectLore, backLabel, onBack, onSelect)
                }
            }
        }
    }

    private fun targetUnavailable(
        player: Player,
        title: String,
        description: String,
        emptyMessage: String,
        selectLore: String,
        backLabel: String,
        onBack: OnlinePlayerSelectBackCallback,
        onSelect: OnlinePlayerSelectCallback,
    ) {
        player.sendDZTError("玩家已离线。")
        open(player, title, description, emptyMessage, selectLore, backLabel, onBack, onSelect)
    }
}

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
