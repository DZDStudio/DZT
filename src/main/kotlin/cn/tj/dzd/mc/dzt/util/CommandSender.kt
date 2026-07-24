package cn.tj.dzd.mc.dzt.util

import org.bukkit.entity.Player
import taboolib.common.platform.ProxyCommandSender

/**
 * 安全取得命令发送者对应的 Bukkit 玩家。
 *
 * `ProxyCommandSender.castSafely<T>()` 的泛型在 JVM 上会被擦除为 `Object`，Kotlin 会在调用点再插入
 * 强制转换；因此控制台发送者调用 `castSafely<Player>()` 仍可能抛出 [ClassCastException]。这里直接检查原始
 * Bukkit 发送者类型。只有实际 [Player] 才能使用 Folia 的实体调度器，控制台与命令方块等发送者应返回 null。
 *
 * 此工具刻意不依赖 Folia 工具文件中的游戏注册表常量，使控制台命令处理与单元测试无需初始化 Paper 注册表。
 *
 * @return 原始发送者为 Bukkit 玩家时返回该玩家，否则返回 null。
 */
internal fun ProxyCommandSender.bukkitPlayerOrNull(): Player? = origin as? Player
