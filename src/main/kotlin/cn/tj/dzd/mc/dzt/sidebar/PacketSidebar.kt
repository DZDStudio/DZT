package cn.tj.dzd.mc.dzt.sidebar

import org.bukkit.entity.Player
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Optional

internal data class PacketSidebarState(
    var initialized: Boolean = false,
    var lineCount: Int = 0,
)

internal object PacketSidebar {
    private const val OBJECTIVE_METHOD_ADD = 0
    private const val OBJECTIVE_METHOD_REMOVE = 1
    private val bridge by lazy { NmsSidebarBridge() }

    /**
     * 通过原版记分板包更新玩家侧边栏，避免在 Folia 中调用 Bukkit ScoreboardManager。
     */
    fun update(
        player: Player,
        objectiveName: String,
        title: String,
        lines: List<String>,
        entries: List<String>,
        state: PacketSidebarState,
    ) {
        if (!state.initialized) {
            bridge.send(player, bridge.createObjectivePacket(objectiveName, title, OBJECTIVE_METHOD_REMOVE))
            bridge.send(player, bridge.createObjectivePacket(objectiveName, title, OBJECTIVE_METHOD_ADD))
            bridge.send(player, bridge.createDisplayObjectivePacket(objectiveName, title))
            state.initialized = true
        }

        if (state.lineCount > lines.size) {
            for (index in lines.size until state.lineCount) {
                bridge.send(player, bridge.createResetScorePacket(entryAt(entries, index), objectiveName))
            }
        }

        lines.forEachIndexed { index, line ->
            val score = lines.size - index
            bridge.send(player, bridge.createSetScorePacket(entryAt(entries, index), objectiveName, score, line))
        }

        state.lineCount = lines.size
    }

    private fun entryAt(entries: List<String>, index: Int): String {
        return entries.getOrElse(index) { "dzt_line_$index" }
    }

    private class NmsSidebarBridge {
        private val craftPlayerClass = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer")
        private val craftChatMessageClass = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage")
        private val packetClass = Class.forName("net.minecraft.network.protocol.Packet")
        private val scoreboardClass = Class.forName("net.minecraft.world.scores.Scoreboard")
        private val objectiveClass = Class.forName("net.minecraft.world.scores.Objective")
        private val objectiveCriteriaClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria")
        private val renderTypeClass = Class.forName("net.minecraft.world.scores.criteria.ObjectiveCriteria\$RenderType")
        private val componentClass = Class.forName("net.minecraft.network.chat.Component")
        private val numberFormatClass = Class.forName("net.minecraft.network.chat.numbers.NumberFormat")
        private val displaySlotClass = Class.forName("net.minecraft.world.scores.DisplaySlot")
        private val setObjectivePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetObjectivePacket")
        private val setDisplayObjectivePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket")
        private val setScorePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetScorePacket")
        private val resetScorePacketClass = Class.forName("net.minecraft.network.protocol.game.ClientboundResetScorePacket")

        private val getHandleMethod: Method = craftPlayerClass.getMethod("getHandle")
        private val fromStringOrEmptyMethod: Method = craftChatMessageClass.getMethod("fromStringOrEmpty", String::class.java)
        private val connectionField: Field = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection")
        private val sendPacketMethod: Method = Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl")
            .getMethod("send", packetClass)
        private val scoreboardConstructor: Constructor<*> = scoreboardClass.getConstructor()
        private val objectiveConstructor: Constructor<*> = objectiveClass.getConstructor(
            scoreboardClass,
            String::class.java,
            objectiveCriteriaClass,
            componentClass,
            renderTypeClass,
            Boolean::class.javaPrimitiveType,
            numberFormatClass,
        )
        private val setObjectivePacketConstructor: Constructor<*> = setObjectivePacketClass.getConstructor(
            objectiveClass,
            Int::class.javaPrimitiveType,
        )
        private val setDisplayObjectivePacketConstructor: Constructor<*> = setDisplayObjectivePacketClass.getConstructor(
            displaySlotClass,
            objectiveClass,
        )
        private val setScorePacketConstructor: Constructor<*> = setScorePacketClass.getConstructor(
            String::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Optional::class.java,
            Optional::class.java,
        )
        private val resetScorePacketConstructor: Constructor<*> = resetScorePacketClass.getConstructor(
            String::class.java,
            String::class.java,
        )

        private val dummyCriteria: Any = objectiveCriteriaClass.getField("DUMMY").get(null)
        private val integerRenderType: Any = renderTypeClass.getField("INTEGER").get(null)
        private val sidebarDisplaySlot: Any = displaySlotClass.getField("SIDEBAR").get(null)
        private val blankNumberFormat: Any? = runCatching {
            Class.forName("net.minecraft.network.chat.numbers.BlankFormat").getField("INSTANCE").get(null)
        }.getOrNull()

        fun send(player: Player, packet: Any) {
            val craftPlayer = craftPlayerClass.cast(player)
            val handle = getHandleMethod.invoke(craftPlayer)
            val connection = connectionField.get(handle)
            sendPacketMethod.invoke(connection, packet)
        }

        fun createObjectivePacket(objectiveName: String, title: String, method: Int): Any {
            return setObjectivePacketConstructor.newInstance(createObjective(objectiveName, title), method)
        }

        fun createDisplayObjectivePacket(objectiveName: String, title: String): Any {
            return setDisplayObjectivePacketConstructor.newInstance(sidebarDisplaySlot, createObjective(objectiveName, title))
        }

        fun createSetScorePacket(owner: String, objectiveName: String, score: Int, display: String): Any {
            return setScorePacketConstructor.newInstance(
                owner,
                objectiveName,
                score,
                Optional.of(component(display)),
                numberFormatOptional(),
            )
        }

        fun createResetScorePacket(owner: String, objectiveName: String): Any {
            return resetScorePacketConstructor.newInstance(owner, objectiveName)
        }

        private fun createObjective(objectiveName: String, title: String): Any {
            return objectiveConstructor.newInstance(
                scoreboardConstructor.newInstance(),
                objectiveName,
                dummyCriteria,
                component(title),
                integerRenderType,
                false,
                blankNumberFormat,
            )
        }

        private fun component(text: String): Any {
            return fromStringOrEmptyMethod.invoke(null, text)
        }

        private fun numberFormatOptional(): Optional<*> {
            return if (blankNumberFormat == null) {
                Optional.empty<Any>()
            } else {
                Optional.of(blankNumberFormat)
            }
        }
    }
}
