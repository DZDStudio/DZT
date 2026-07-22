package cn.tj.dzd.mc.dzt.admin.ui

import cn.tj.dzd.mc.dzt.ui.MainMenuNavigation
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelection
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectUI
import cn.tj.dzd.mc.dzt.ui.OnlinePlayerSelectSecondaryAction
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.foliaTeleport
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.platform.compat.checkPermission
import taboolib.platform.util.buildItem

/** Java 箱子菜单与基岩表单共用的管理界面。 */
object AdminUI {

    /** 查看和使用管理菜单所需的权限。 */
    const val PERMISSION = "dzt.admin"

    private const val TITLE = "§l§c管理菜单"
    private const val BACK_ICON = "textures/ui/box_ride.png"
    private const val SPECTATOR_ICON = "textures/items/ender_eye.png"
    private const val SURVIVAL_ICON = "textures/items/iron_sword.png"
    private const val TELEPORT_ICON = "textures/items/ender_pearl.png"

    /**
     * 判断玩家是否可以查看和使用管理菜单。
     *
     * @param player 要检查的玩家。
     * @return 玩家拥有 [PERMISSION] 时返回 `true`。
     */
    fun canUse(player: Player): Boolean {
        return player.checkPermission(PERMISSION)
    }

    /**
     * 为玩家打开管理菜单。
     *
     * 会在玩家实体线程重新校验权限，并根据客户端类型选择 Java 箱子菜单或基岩表单。
     *
     * @param player 要展示界面的在线玩家。
     */
    fun open(player: Player) {
        player.foliaRun {
            if (!requirePermission(this)) {
                return@foliaRun
            }

            if (isBePlayer()) {
                openBedrock(this)
            } else {
                openJava(this)
            }
        }
    }

    private fun openJava(player: Player) {
        val target = targetGameMode(player.gameMode)
        player.openMenu<Chest>(TITLE) {
            rows(3)
            virtualize()

            map(
                "R###M####",
                "#  G T  #",
                "#########",
            )

            onClick(lock = true) {}
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " })
            set('R', buildItem(XMaterial.BARREL) {
                name = "§l§e返回主菜单"
            }) {
                MainMenuNavigation.open(player)
            }
            set('M', buildItem(XMaterial.COMMAND_BLOCK) {
                name = TITLE
            })
            set('G', buildItem(target.javaIcon) {
                name = "§l§6切换为${target.displayName}模式"
                lore += "§7当前模式：§f${gameModeName(player.gameMode)}"
                lore += "§a点击切换"
            }) {
                toggleGameMode(player)
            }
            set('T', buildItem(XMaterial.ENDER_PEARL) {
                name = "§l§6管理员传送"
                lore += "§7传送至在线玩家或将其传送至你"
            }) {
                openTeleportTargetSelection(player)
            }
        }
    }

    private fun openBedrock(player: Player) {
        val current = player.gameMode
        val target = targetGameMode(current)
        player.sendForm(
            SimpleForm.builder()
                .title(TITLE)
                .content("当前模式：${gameModeName(current)}")
                .button("返回主菜单", FormImage.Type.PATH, BACK_ICON)
                .button("切换为${target.displayName}模式", FormImage.Type.PATH, target.bedrockIcon)
                .button("管理员传送", FormImage.Type.PATH, TELEPORT_ICON)
                .validResultHandler { response ->
                    player.foliaRun {
                        when (response.clickedButtonId()) {
                            0 -> MainMenuNavigation.open(this)
                            1 -> toggleGameMode(this)
                            2 -> openTeleportTargetSelection(this)
                        }
                    }
                }
        )
    }

    private fun openTeleportTargetSelection(player: Player) {
        player.foliaRun {
            if (!requirePermission(this)) {
                return@foliaRun
            }

            OnlinePlayerSelectUI.open(
                player = this,
                title = "§l§c管理员传送",
                description = "选择一名在线玩家并执行传送操作。",
                emptyMessage = "当前没有其他在线玩家。",
                selectLore = "§7点击立即传送至该玩家",
                backLabel = "§l§e返回管理菜单",
                secondaryAction = OnlinePlayerSelectSecondaryAction(
                    javaLore = "§eShift + 右键将该玩家传送至你",
                    bedrockPrimaryLabel = "传送至该玩家",
                    bedrockPrimaryIcon = TELEPORT_ICON,
                    bedrockSecondaryLabel = "将该玩家传送至你",
                    bedrockSecondaryIcon = "textures/items/recovery_compass_item.png",
                    onSelect = { target ->
                        teleportTargetToAdmin(this, target)
                    },
                ),
                onBack = {
                    open(this)
                },
                onSelect = { target ->
                    teleportToTarget(this, target)
                },
            )
        }
    }

    private fun teleportToTarget(player: Player, target: OnlinePlayerSelection) {
        val playerId = player.uniqueId
        target.withOnlinePlayer {
            val targetPlayer = this
            player.foliaRun {
                if (!requirePermission(this)) {
                    return@foliaRun
                }

                foliaTeleport(targetPlayer).whenComplete { success, error ->
                    runForOnlinePlayer(playerId) {
                        if (error == null && success == true) {
                            sendDZTSuccess("已传送至 ${target.name}。")
                        } else {
                            sendDZTError("传送失败，目标玩家可能已离线。")
                        }
                    }
                }
            }
        }.whenComplete { available, error ->
            if (error != null || available != true) {
                runForOnlinePlayer(playerId) {
                    sendDZTError("传送失败，目标玩家可能已离线。")
                }
            }
        }
    }

    private fun teleportTargetToAdmin(admin: Player, target: OnlinePlayerSelection) {
        val adminId = admin.uniqueId
        val adminName = admin.name
        target.withOnlinePlayer {
            val targetPlayer = this
            admin.foliaRun {
                if (!requirePermission(this)) {
                    return@foliaRun
                }

                targetPlayer.foliaTeleport(this).whenComplete { success, error ->
                    val teleported = error == null && success == true
                    runForOnlinePlayer(adminId) {
                        if (teleported) {
                            sendDZTSuccess("已将 ${target.name} 传送至你的位置。")
                        } else {
                            sendDZTError("传送失败，目标玩家可能已离线。")
                        }
                    }
                    if (teleported) {
                        target.withOnlinePlayer {
                            sendDZTSuccess("管理员 $adminName 已将你传送至其位置。")
                        }
                    }
                }
            }.whenComplete { scheduled, error ->
                if (error != null || scheduled != true) {
                    runForOnlinePlayer(adminId) {
                        sendDZTError("传送失败，无法执行管理员传送。")
                    }
                }
            }
        }.whenComplete { available, error ->
            if (error != null || available != true) {
                runForOnlinePlayer(adminId) {
                    sendDZTError("传送失败，目标玩家可能已离线。")
                }
            }
        }
    }

    private fun toggleGameMode(player: Player) {
        player.foliaRun {
            if (!requirePermission(this)) {
                return@foliaRun
            }

            val target = targetGameMode(gameMode)
            // TabooLib 未提供设置玩家 GameMode 的等价接口；此处已在玩家实体线程执行。
            gameMode = target.bukkitMode
            sendDZTSuccess("已切换为${target.displayName}模式。")
        }
    }

    private fun requirePermission(player: Player): Boolean {
        if (canUse(player)) {
            return true
        }

        player.foliaCloseInventory()
        player.sendDZTError("你没有权限使用管理菜单。")
        return false
    }

    private fun targetGameMode(current: GameMode): GameModeTarget {
        return if (current == GameMode.SPECTATOR) GameModeTarget.SURVIVAL else GameModeTarget.SPECTATOR
    }

    private fun gameModeName(gameMode: GameMode): String {
        return when (gameMode) {
            GameMode.SURVIVAL -> "生存"
            GameMode.CREATIVE -> "创造"
            GameMode.ADVENTURE -> "冒险"
            GameMode.SPECTATOR -> "旁观"
        }
    }

    private enum class GameModeTarget(
        val bukkitMode: GameMode,
        val displayName: String,
        val javaIcon: XMaterial,
        val bedrockIcon: String,
    ) {
        SPECTATOR(GameMode.SPECTATOR, "旁观", XMaterial.ENDER_EYE, SPECTATOR_ICON),
        SURVIVAL(GameMode.SURVIVAL, "生存", XMaterial.IRON_SWORD, SURVIVAL_ICON),
    }
}
