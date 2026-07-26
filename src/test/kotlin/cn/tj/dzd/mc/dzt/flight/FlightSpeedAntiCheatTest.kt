package cn.tj.dzd.mc.dzt.flight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 飞行速度反作弊分级规则的单元测试。 */
class FlightSpeedAntiCheatTest {

    @Test
    fun `低于常规阈值不记录违规`() {
        assertNull(FlightSpeedViolationPolicy.classify(horizontalSpeed = 1.2, verticalSpeed = 0.7))
    }

    @Test
    fun `常规速度异常进入常规窗口`() {
        assertEquals(
            FlightSpeedViolationLevel.NORMAL,
            FlightSpeedViolationPolicy.classify(horizontalSpeed = 1.21, verticalSpeed = 0.0),
        )
        assertEquals(
            FlightSpeedViolationLevel.NORMAL,
            FlightSpeedViolationPolicy.classify(horizontalSpeed = 0.0, verticalSpeed = 0.71),
        )
    }

    @Test
    fun `高危速度异常优先进入高危窗口`() {
        assertEquals(
            FlightSpeedViolationLevel.SEVERE,
            FlightSpeedViolationPolicy.classify(horizontalSpeed = 2.01, verticalSpeed = 0.0),
        )
        assertEquals(
            FlightSpeedViolationLevel.SEVERE,
            FlightSpeedViolationPolicy.classify(horizontalSpeed = 0.0, verticalSpeed = 1.21),
        )
    }

    @Test
    fun `分级处罚阈值符合预期`() {
        assertEquals(7, FlightSpeedViolationLevel.NORMAL.reminderThreshold)
        assertEquals(10, FlightSpeedViolationLevel.NORMAL.banThreshold)
        assertEquals(2, FlightSpeedViolationLevel.SEVERE.reminderThreshold)
        assertEquals(4, FlightSpeedViolationLevel.SEVERE.banThreshold)
    }
}
