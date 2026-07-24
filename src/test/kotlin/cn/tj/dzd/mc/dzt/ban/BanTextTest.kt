package cn.tj.dzd.mc.dzt.ban

import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class BanTextTest {

    @Test
    fun `player group announcement contains player reason and decimal hours`() {
        val playerId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val ban = PlayerBan(
            recordId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
            playerId = playerId,
            bannedAt = 1_000L,
            unbanAt = 361_000L,
            reason = "测试原因",
            active = true,
            releasedAt = 0L,
        )

        assertEquals(
            "[封禁公示] 玩家HaiPaya因测试原因封禁0.1小时。",
            BanText.playerGroupAnnouncement("HaiPaya", ban, BigDecimal("0.100")),
        )
    }
}
