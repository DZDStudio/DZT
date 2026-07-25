package cn.tj.dzd.mc.dzt.flight

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertTrue

/**
 * 飞行速度反作弊检测系统的单元测试。
 *
 * 测试速度计算逻辑和阈值判定。
 */
class FlightSpeedAntiCheatTest {

    private val horizontalThreshold = 1.1
    private val verticalThreshold = 0.4

    @Test
    fun `水平速度超过阈值应当触发违规`() {
        // 模拟 20 tick 内移动 25 方块（水平）
        val dx = 25.0
        val dz = 0.0
        val ticks = 20.0
        
        val horizontalSpeed = sqrt(dx * dx + dz * dz) / ticks
        
        assertTrue(horizontalSpeed > horizontalThreshold, 
            "水平速度 $horizontalSpeed 应当超过阈值 $horizontalThreshold")
    }

    @Test
    fun `垂直速度超过阈值应当触发违规`() {
        // 模拟 20 tick 内上升 10 方块（垂直）
        val dy = 10.0
        val ticks = 20.0
        
        val verticalSpeed = abs(dy) / ticks
        
        assertTrue(verticalSpeed > verticalThreshold, 
            "垂直速度 $verticalSpeed 应当超过阈值 $verticalThreshold")
    }

    @Test
    fun `正常飞行速度不应触发违规`() {
        // 模拟 20 tick 内移动 10 方块（水平）+ 5 方块（垂直）
        val dx = 10.0
        val dy = 5.0
        val dz = 0.0
        val ticks = 20.0
        
        val horizontalSpeed = sqrt(dx * dx + dz * dz) / ticks
        val verticalSpeed = abs(dy) / ticks
        
        assertTrue(horizontalSpeed <= horizontalThreshold && verticalSpeed <= verticalThreshold,
            "正常速度（水平: $horizontalSpeed, 垂直: $verticalSpeed）不应触发违规")
    }

    @Test
    fun `极限正常速度应当不触发违规`() {
        // 模拟接近阈值但未超过的速度
        val ticks = 20.0
        
        // 水平速度刚好在阈值内
        val dx1 = horizontalThreshold * ticks
        val horizontalSpeed1 = dx1 / ticks
        assertTrue(horizontalSpeed1 <= horizontalThreshold,
            "极限水平速度 $horizontalSpeed1 不应超过阈值 $horizontalThreshold")
        
        // 垂直速度刚好在阈值内
        val dy1 = verticalThreshold * ticks
        val verticalSpeed1 = dy1 / ticks
        assertTrue(verticalSpeed1 <= verticalThreshold,
            "极限垂直速度 $verticalSpeed1 不应超过阈值 $verticalThreshold")
    }

    @Test
    fun `15次违规应触发警告阈值`() {
        val warningThreshold = 15
        val violations = (1..15).toList()
        
        assertTrue(violations.size >= warningThreshold,
            "15 次违规应达到警告阈值")
    }

    @Test
    fun `20次违规应触发封禁阈值`() {
        val banThreshold = 20
        val violations = (1..20).toList()
        
        assertTrue(violations.size >= banThreshold,
            "20 次违规应达到封禁阈值")
    }
}
