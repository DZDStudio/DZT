package cn.tj.dzd.mc.dzt.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the project rule that TabooLib is used whenever it offers a reliable replacement.
 *
 * Bukkit facade calls listed here intentionally remain platform boundaries: service discovery,
 * server shutdown, Paper/Folia metrics, namespaced world resolution, and typed player lookup.
 */
class PlatformBoundaryTest {

    @Test
    fun `raw Bukkit facade calls remain explicitly allowlisted`() {
        val calls = sourceFiles()
            .flatMap { path ->
                BUKKIT_CALL.findAll(Files.readString(path)).map { match ->
                    BukkitCall(relativePath(path), match.groupValues[1])
                }.toList()
            }
            .toSet()

        assertEquals(ALLOWED_BUKKIT_CALLS, calls)
    }

    @Test
    fun `TabooLib replacements do not regress to redundant platform implementations`() {
        val source = sourceFiles().joinToString("\n") { Files.readString(it) }

        REDUNDANT_PATTERNS.forEach { pattern ->
            assertFalse(pattern in source, "Redundant platform implementation returned: $pattern")
        }
        assertTrue(sourceFiles().none { relativePath(it).endsWith("sidebar/PacketSidebar.kt") })
    }

    @Test
    fun `background teleport flows keep Bukkit locations out of persistence tasks`() {
        val homeUi = source("cn/tj/dzd/mc/dzt/teleport/ui/Home.kt")
        val backUi = source("cn/tj/dzd/mc/dzt/teleport/ui/Back.kt")
        val backService = source("cn/tj/dzd/mc/dzt/teleport/service/Back.kt")

        assertTrue("HomeService.getHomeEntryList(ownerId)" in homeUi)
        assertFalse("HomeService.getHomeList(ownerId)" in homeUi)
        assertTrue("HomeService.addHome(ownerId, name, storedLocation, icon)" in homeUi)
        assertFalse("HomeService.addHome(ownerId, name, location, icon)" in homeUi)

        assertTrue("BackService.getBackEntryList(ownerId)" in backUi)
        assertFalse("BackService.getBackRecordList(ownerId)" in backUi)
        assertTrue("BackService.removeBackRecord(ownerId, backTime)" in backUi)
        assertTrue("addBackRecord(ownerId, storedLocation)" in backService)
    }

    @Test
    fun `sidebar player tasks have one atomic owner per session`() {
        val sidebar = source("cn/tj/dzd/mc/dzt/sidebar/Sidebar.kt")

        assertTrue("sidebarTasks.compute(uuid)" in sidebar)
        assertFalse("sidebarTasks.containsKey" in sidebar)
    }

    @Test
    fun `online player reporting snapshots names before asynchronous publish`() {
        val reporting = source("cn/tj/dzd/mc/dzt/luo_yudan/LuoYudan.kt")

        assertTrue("players.map(::snapshotPlayerName)" in reporting)
        assertTrue("val names = nameSnapshots.mapNotNull" in reporting)
        assertFalse("onlinePlayersSnapshot.map { pl -> pl.name }" in reporting)
    }

    private fun sourceFiles(): List<Path> {
        return Files.walk(SOURCE_ROOT).use { paths ->
            paths.filter { it.isRegularFile() && it.toString().endsWith(".kt") }.toList()
        }
    }

    private fun relativePath(path: Path): String {
        return SOURCE_ROOT.relativize(path).toString().replace('\\', '/')
    }

    private fun source(relativePath: String): String {
        return Files.readString(SOURCE_ROOT.resolve(relativePath))
    }

    private data class BukkitCall(val path: String, val method: String)

    private companion object {
        private val SOURCE_ROOT: Path = Path.of("src/main/kotlin")
        private val BUKKIT_CALL = Regex("\\bBukkit\\.(\\w+)")

        private val ALLOWED_BUKKIT_CALLS = setOf(
            BukkitCall("cn/tj/dzd/mc/dzt/data/DatabaseGuard.kt", "shutdown"),
            BukkitCall("cn/tj/dzd/mc/dzt/economy/ServiceEconomy.kt", "getServicesManager"),
            BukkitCall("cn/tj/dzd/mc/dzt/sidebar/Sidebar.kt", "getTPS"),
            BukkitCall("cn/tj/dzd/mc/dzt/teleport/service/Player.kt", "getWorld"),
            BukkitCall("cn/tj/dzd/mc/dzt/util/Folia.kt", "getPlayer"),
        )

        private val REDUNDANT_PATTERNS = listOf(
            "Bukkit.getOnlinePlayers",
            "taboolib.common.platform.function.onlinePlayers",
            "Material.matchMaterial",
            "Registry.MATERIAL",
            "org.bukkit.craftbukkit.entity.CraftPlayer",
            "net.minecraft.network.protocol.game.ClientboundSetObjectivePacket",
        )
    }
}
