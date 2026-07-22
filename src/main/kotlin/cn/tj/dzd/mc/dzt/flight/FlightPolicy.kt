package cn.tj.dzd.mc.dzt.flight

/**
 * 飞行功能关心的玩家游戏模式。
 *
 * 该枚举隔离 Bukkit 类型，使飞行业务规则可以在不加载服务端 API 的情况下测试。
 */
enum class FlightGameMode {
    /** 生存模式。 */
    SURVIVAL,

    /** 冒险模式。 */
    ADVENTURE,

    /** 创造模式。 */
    CREATIVE,

    /** 旁观模式。 */
    SPECTATOR,
}

/**
 * 玩家飞行开关与扣费的纯业务规则。
 */
object FlightPolicy {

    /**
     * 判断插件是否应接管玩家的 `allowFlight` 状态。
     *
     * 生存与冒险模式由插件根据持久化开关设置 `allowFlight`；创造与旁观模式
     * 使用游戏原生飞行能力，插件不得覆盖其状态。
     *
     * @param gameMode 玩家当前游戏模式。
     * @return 插件应设置 `allowFlight` 时返回 `true`。
     */
    fun managesAllowFlight(gameMode: FlightGameMode): Boolean {
        return gameMode == FlightGameMode.SURVIVAL || gameMode == FlightGameMode.ADVENTURE
    }

    /**
     * 判断本次每 100 tick 执行的飞行检查是否应扣除弟弟币。
     *
     * 仅当飞行开关已开启、玩家当前确实处于飞行状态，且处于生存或冒险模式时扣费。
     * 创造与旁观模式的原生飞行不收费。
     *
     * @param enabled 玩家持久化的飞行开关是否开启。
     * @param actuallyFlying 玩家本次检查时是否正在飞行。
     * @param gameMode 玩家当前游戏模式。
     * @return 本次检查应扣费时返回 `true`。
     */
    fun shouldChargeForFlightCheck(
        enabled: Boolean,
        actuallyFlying: Boolean,
        gameMode: FlightGameMode,
    ): Boolean {
        return enabled && actuallyFlying && managesAllowFlight(gameMode)
    }
}
