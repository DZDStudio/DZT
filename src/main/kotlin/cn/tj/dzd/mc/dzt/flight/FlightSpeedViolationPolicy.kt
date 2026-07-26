package cn.tj.dzd.mc.dzt.flight

/** 飞行速度违规的严重等级。 */
internal enum class FlightSpeedViolationLevel(
    val consoleLabel: String,
    val reminderThreshold: Int,
    val banThreshold: Int,
) {
    /** 超过常规速度阈值的违规。 */
    NORMAL(consoleLabel = "常规", reminderThreshold = 7, banThreshold = 10),

    /** 超过高危速度阈值的违规。 */
    SEVERE(consoleLabel = "高危", reminderThreshold = 2, banThreshold = 4),
}

/**
 * 飞行速度检测阈值与分级规则。
 *
 * 高危违规只计入 [FlightSpeedViolationLevel.SEVERE] 窗口，不会同时计入常规窗口，避免一次移动
 * 同时触发两套计数与通知。
 */
internal object FlightSpeedViolationPolicy {

    const val NORMAL_HORIZONTAL_SPEED_THRESHOLD = 1.2
    const val NORMAL_VERTICAL_SPEED_THRESHOLD = 0.7
    const val SEVERE_HORIZONTAL_SPEED_THRESHOLD = 2.0
    const val SEVERE_VERTICAL_SPEED_THRESHOLD = 1.2
    const val WINDOW_MILLIS = 3L * 60L * 1000L

    /**
     * 按本次移动的速度确定违规等级。
     *
     * @param horizontalSpeed 水平速度，单位为方块/tick。
     * @param verticalSpeed 垂直速度绝对值，单位为方块/tick。
     * @return 未超过常规阈值时为 `null`；高危速度优先返回 [FlightSpeedViolationLevel.SEVERE]。
     */
    fun classify(horizontalSpeed: Double, verticalSpeed: Double): FlightSpeedViolationLevel? {
        return when {
            horizontalSpeed > SEVERE_HORIZONTAL_SPEED_THRESHOLD ||
                verticalSpeed > SEVERE_VERTICAL_SPEED_THRESHOLD -> FlightSpeedViolationLevel.SEVERE

            horizontalSpeed > NORMAL_HORIZONTAL_SPEED_THRESHOLD ||
                verticalSpeed > NORMAL_VERTICAL_SPEED_THRESHOLD -> FlightSpeedViolationLevel.NORMAL

            else -> null
        }
    }
}
