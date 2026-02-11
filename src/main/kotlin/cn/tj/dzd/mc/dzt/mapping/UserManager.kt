package cn.tj.dzd.mc.dzt.mapping

import cn.tj.dzd.mc.dzt.mapping.tables.SettingMapping
import cn.tj.dzd.mc.dzt.mapping.tables.UserMapping
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.server.ServerListPingEvent
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.floodgate.api.FloodgateApi
import org.geysermc.floodgate.api.player.FloodgatePlayer
import org.geysermc.geyser.api.GeyserApi
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.info
import taboolib.expansion.submitChain
import taboolib.module.ui.Menu
import taboolib.module.ui.openMenu
import taboolib.platform.util.runTask

private val floodgateApi: FloodgateApi = FloodgateApi.getInstance()
private val geyserApi = GeyserApi.api()

private val DZDPlayerCache = mutableMapOf<String, DZDPlayer>()

private object Listener {
    @SubscribeEvent
    fun onServerListPing(event: ServerListPingEvent) {
        event.motd = "§l§3D§cZ§3D§5G§ea§1m§ce§r\n§fQQ: §9747§d121§c127"

        val maintenance = SettingMapping.getSetting("维护")
        if (maintenance != null && maintenance.bool) {
            event.motd = "§l§e「维护中」§r\n${maintenance.text}"
        }
    }

    @SubscribeEvent
    fun onAsyncPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        val maintenance = SettingMapping.getSetting("维护")
        if (maintenance != null && maintenance.bool && event.name != "HaiPaya") {
            event.loginResult = AsyncPlayerPreLoginEvent.Result.KICK_OTHER
            event.kickMessage = "§l§e「维护中」§r\n\n${maintenance.text}"
        }
    }

    @SubscribeEvent
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val pl = event.player

        // 注册用户
        UserMapping.addUser(pl.name)

        // 获取 UID
        val uid = UserMapping.getUID(pl.name)
        if (uid == null) {
            pl.kickPlayer("§l§c非常抱歉，我们无法获取您的 DZD 用户标识符。\n请尝试重新加入服务器或联系运维人员！")
            throw Exception("缓存 ${pl.name}[${pl.uniqueId}] 的 DZD 用户标识符失败")
        }
        info("${pl.name}[${pl.uniqueId}] UID 获取成功 => $uid")

        // 缓存玩家对象
        val dp = DZDPlayer(uid, pl)
        DZDPlayerCache[pl.name] = dp
        info("${pl.name}[${pl.uniqueId}] DZD 玩家对象缓存成功 => ${dp.name}")

        // 更新昵称
        pl.setDisplayName(dp.name)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val pl = event.player
        // 移除玩家对象缓存
        DZDPlayerCache.remove(pl.name)
    }
}

/**
 * 获取 DZD 玩家
 * @return DZDPlayer
 */
fun Player.getDZDPlayer(): DZDPlayer {
    if (DZDPlayerCache.containsKey(name)) {
        return DZDPlayerCache[name]!!
    } else {
        kickPlayer("§l§c非常抱歉，我们无法检索到您的 DZD 玩家对象。\n请尝试重新加入服务器或联系运维人员！")
        throw Exception("检索 ${name}[${uniqueId}] 的 DZD 玩家对象失败")
    }
}

/**
 * 在线 DZD 玩家
 */
val onlineDZDPlayers: List<DZDPlayer>
    get() = DZDPlayerCache.values.toList()

/**
 * DZD 玩家
 * @param uid 用户标识符
 * @param pl 玩家
 */
class DZDPlayer(
    /**
     * 用户标识符
     */
    val uid: Number,

    /**
     * 玩家
     */
    val pl: Player
) {
    /**
     * 玩家名称
     */
    val name: String = UserMapping.getNickName(uid) ?: pl.name

    /**
     * Floodgate 玩家
     */
    val floodgatePlayer: FloodgatePlayer? = floodgateApi.getPlayer(pl.uniqueId)

    /**
     * Geyser 玩家
     */
    val geyserConnection = geyserApi.connectionByUuid(pl.uniqueId)

    /**
     * 玩家位置 (可能存在 Folia 安全性问题)
     */
    val location get() = pl.location

    /**
     * 玩家是否在线
     */
    fun isOnline(): Boolean {
        return pl.isOnline
    }

    /**
     * 判断是否为 Java 版客户端
     */
    fun isJE(): Boolean {
        return floodgatePlayer == null
    }

    /**
     * 获取延迟
     */
    fun getPing(): Int {
        return geyserConnection?.ping() ?: pl.ping
    }

    /**
     * 发送消息
     * @param message 消息
     */
    fun send(message: String) {
        pl.sendMessage("[§l§3D§cZ§3D§5G§ea§1m§ce§r] $message")
    }

    /**
     * 发送成功消息
     * @param message 消息
     */
    fun sendSuccess(message: String) {
        pl.sendMessage("[§l§3D§cZ§3D§5G§ea§1m§ce§r]§a $message")
    }

    /**
     * 发送错误消息
     * @param message 错误消息
     */
    fun sendError(message: String) {
        pl.sendMessage("[§l§3D§cZ§3D§5G§ea§1m§ce§r]§c $message")
    }

    /**
     * 以玩家身份执行命令
     * @param command 命令
     */
    fun exCommand(command: String) {
        submitChain {
            pl.runTask({
                pl.performCommand(command)
            })
        }
    }

    /**
     * 传送到目标位置
     * @param location 目标位置
     */
    fun teleport(location: Location) {
        submitChain {
            pl.runTask({
                pl.teleport(location)
            })
        }
    }

    /**
     * 传送到目标 DZD 玩家
     * @param tpl 目标位置
     */
    fun teleport(tpl: DZDPlayer) {
        submitChain {
            tpl.pl.runTask({
                val tl = tpl.pl.location
                pl.runTask({
                    pl.teleport(tl)
                })
            })
        }
    }

    /**
     * 获取玩家位置
     */
    fun getLocation(callback: (Location) -> Unit) {
        runTask({
            callback(pl.location)
        })
    }

    /**
     * 在玩家所在位置执行一个任务（Folia 安全）
     *
     * @param executor 任务
     * @param useScheduler 在非 Folia 环境下是否使用调度器（默认 true）
     */
    fun runTask(executor: Runnable, useScheduler: Boolean = true) {
        pl.runTask(executor, useScheduler)
    }

    /**
     * 给予物品
     * @param item 物品
     */
    fun giveItem(item: ItemStack) {
        submitChain {
            pl.runTask({
                pl.inventory.addItem(item)
            })
        }
    }

    /**
     * 关闭玩家背包
     */
    fun closeInventory() {
        pl.runTask({
            pl.closeInventory()
        })
    }

    /**
     * 构建一个菜单并为玩家打开
     * @param title 菜单标题
     * @param builder 菜单构建器
     */
    inline fun <reified T : Menu> openMenu(title: String = "chest", crossinline builder: T.() -> Unit) {
        pl.runTask({
            pl.openMenu(title, builder)
        })
    }

    /**
     * 发送表单
     * @param formBuilder 表单构建器
     */
    fun sendForm(formBuilder: SimpleForm.Builder) {
        if (floodgatePlayer == null) throw Exception("为玩家 ${name}[${uid}] 发送表单失败：不是 Floodgate 玩家")
        pl.runTask({
            floodgatePlayer.sendForm(formBuilder)
        })
    }

    /**
     * 发送表单
     * @param formBuilder 表单构建器
     */
    fun sendForm(formBuilder: ModalForm.Builder) {
        if (floodgatePlayer == null) throw Exception("为玩家 ${name}[${uid}] 发送表单失败：不是 Floodgate 玩家")
        pl.runTask({
            floodgatePlayer.sendForm(formBuilder)
        })
    }

    /**
     * 发送表单
     * @param formBuilder 表单构建器
     */
    fun sendForm(formBuilder: CustomForm.Builder) {
        if (floodgatePlayer == null) throw Exception("为玩家 ${name}[${uid}] 发送表单失败：不是 Floodgate 玩家")
        pl.runTask({
            floodgatePlayer.sendForm(formBuilder)
        })
    }
}