package cn.tj.dzd.mc.dzt.title.ui

import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import cn.tj.dzd.mc.dzt.title.PlayerTitle
import cn.tj.dzd.mc.dzt.title.TitleEquipResult
import cn.tj.dzd.mc.dzt.title.TitleService
import cn.tj.dzd.mc.dzt.ui.MainMenuNavigation
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendDZTTip
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Java 版和基岩版共用的称号选择界面。 */
object TitleUI {

    private const val DESCRIPTION_LINE_LENGTH = 32
    private val beijingZone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val grantedTimeFormatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(beijingZone)
    private val selectionsInProgress = ConcurrentHashMap.newKeySet<UUID>()

    private sealed interface TitleOption {
        data object Unequip : TitleOption
        data class Owned(val title: PlayerTitle) : TitleOption
    }

    /**
     * 读取玩家称号后，根据客户端类型打开 JE 箱子菜单或 BE 表单。
     *
     * @param player 需要打开称号菜单的玩家。
     */
    fun open(player: Player) {
        player.foliaRun {
            loadTitles(uniqueId, isBePlayer())
        }
    }

    private fun loadTitles(uuid: UUID, bedrockPlayer: Boolean) {
        DztAsyncExecutor.supply {
            TitleService.getOwnedTitles(uuid)
        }.whenComplete { titles, error ->
            runForOnlinePlayer(uuid) {
                if (error != null) {
                    sendDZTError("读取称号失败：${error.message ?: error.javaClass.simpleName}")
                    return@runForOnlinePlayer
                }

                if (bedrockPlayer) {
                    openBedrock(this, titles.orEmpty())
                } else {
                    openJava(this, titles.orEmpty())
                }
            }
        }
    }

    private fun openJava(player: Player, titles: List<PlayerTitle>) {
        val options = buildList {
            add(TitleOption.Unequip)
            addAll(titles.map(TitleOption::Owned))
        }

        player.openMenu<PageableChest<TitleOption>>("§l§6称号") {
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
            set('M', XMaterial.NAME_TAG) { name = "§l§6选择称号" }
            set('R', buildItem(XMaterial.BARREL) {
                name = "§l§e返回主菜单"
            }) {
                MainMenuNavigation.open(player)
            }

            slotsBy('@')
            elements { options }
            onGenerate { _, option, _, _ ->
                when (option) {
                    TitleOption.Unequip -> buildItem(XMaterial.BARRIER) {
                        name = "§c不佩戴称号"
                        lore += "§7聊天时不显示称号"
                        lore += "§e点击选择"
                    }
                    is TitleOption.Owned -> buildItem(XMaterial.NAME_TAG) {
                        name = "§f[§r${option.title.displayName}§f]"
                        lore += "§7ID: §f${option.title.id}"
                        formatDescriptionLore(option.title.description).forEach { line ->
                            lore += line
                        }
                        lore += "§7给予时间: §e${formatGrantedTime(option.title.grantedAt)}"
                        lore += "§8时区: 北京时间"
                        lore += if (option.title.equipped) "§a当前佩戴" else "§e点击佩戴"
                    }
                }
            }
            onClick { _, option ->
                when (option) {
                    TitleOption.Unequip -> select(player, null)
                    is TitleOption.Owned -> select(player, option.title.id)
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

    private fun openBedrock(player: Player, titles: List<PlayerTitle>) {
        val equipped = titles.firstOrNull(PlayerTitle::equipped)
        val form = SimpleForm.builder()
            .title("§l§6称号")
            .content(
                equipped?.let { "§7当前佩戴：§f[§r${it.displayName}§f]" }
                    ?: "§7当前未佩戴称号。"
            )
            .button("返回主菜单", FormImage.Type.PATH, "textures/ui/box_ride.png")
            .button("§c不佩戴称号", FormImage.Type.PATH, "textures/ui/cancel.png")

        titles.forEach { title ->
            val marker = if (title.equipped) "§a[当前] §r" else ""
            form.button(
                "$marker${title.displayName}\n§7${formatGrantedTime(title.grantedAt)}",
                FormImage.Type.PATH,
                "textures/items/name_tag.png"
            )
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            player.foliaRun {
                when (clicked) {
                    0 -> MainMenuNavigation.open(this)
                    1 -> select(this, null)
                    else -> {
                        val title = titles.getOrNull(clicked - 2) ?: return@foliaRun
                        openBedrockTitleDetail(this, title)
                    }
                }
            }
        }
        player.sendForm(form)
    }

    private fun openBedrockTitleDetail(player: Player, title: PlayerTitle) {
        val description = title.description.ifBlank { "暂无介绍" }
        val primaryButton = if (title.equipped) "取消佩戴" else "佩戴"

        player.sendForm(
            ModalForm.builder()
                .title("§l§6称号详情")
                .content(
                    "§7称号: §f[§r${title.displayName}§f]\n" +
                        "§7介绍: §f$description\n" +
                        "§7给予时间: §e${formatGrantedTime(title.grantedAt)} §8(北京时间)"
                )
                .button1(primaryButton)
                .button2("返回")
                .validResultHandler { response ->
                    val clicked = response.clickedButtonId()
                    player.foliaRun {
                        if (clicked == 0) {
                            select(this, if (title.equipped) null else title.id)
                        } else {
                            open(this)
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    player.foliaRun {
                        open(this)
                    }
                })
        )
    }

    private fun select(player: Player, titleId: String?) {
        player.foliaRun {
            selectOnPlayerThread(this, titleId)
        }
    }

    private fun selectOnPlayerThread(player: Player, titleId: String?) {
        val uuid = player.uniqueId
        if (!selectionsInProgress.add(uuid)) {
            player.sendDZTTip("称号正在处理中，请稍候。")
            return
        }

        player.foliaCloseInventory()
        DztAsyncExecutor.supply {
            if (titleId == null) {
                TitleService.unequipTitle(uuid)
            } else {
                TitleService.equipTitle(uuid, titleId)
            }
        }.whenComplete { result, error ->
            selectionsInProgress.remove(uuid)
            runForOnlinePlayer(uuid) {
                if (error != null) {
                    sendDZTError("更改佩戴称号失败：${error.message ?: error.javaClass.simpleName}")
                    return@runForOnlinePlayer
                }

                when (result) {
                    TitleEquipResult.EQUIPPED -> sendDZTSuccess("已佩戴称号。")
                    TitleEquipResult.UNEQUIPPED -> sendDZTSuccess("已取消佩戴称号。")
                    TitleEquipResult.ALREADY_EQUIPPED -> sendDZTTip("已在佩戴该称号。")
                    TitleEquipResult.NOT_OWNED -> sendDZTError("你尚未拥有该称号。")
                    TitleEquipResult.FAILED -> sendDZTError("更改佩戴称号失败。")
                    null -> sendDZTError("更改佩戴称号失败。")
                }

                if (result != TitleEquipResult.FAILED) {
                    open(this)
                }
            }
        }
    }

    private fun formatGrantedTime(timestamp: Long): String {
        return grantedTimeFormatter.format(Instant.ofEpochMilli(timestamp))
    }

    private fun formatDescriptionLore(description: String): List<String> {
        if (description.isBlank()) {
            return listOf("§7介绍: §8暂无介绍")
        }

        return buildList {
            add("§7介绍:")
            description.lines().forEach { sourceLine ->
                if (sourceLine.isEmpty()) {
                    add("§f ")
                } else {
                    sourceLine.chunked(DESCRIPTION_LINE_LENGTH).forEach { line ->
                        add("§f$line")
                    }
                }
            }
        }
    }
}
