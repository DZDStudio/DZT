package cn.tj.dzd.mc.dzt.onebot

import cn.tj.dzd.mc.dzt.util.floodgateApi
import net.kyori.adventure.text.Component
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.warning
import java.util.UUID

/**
 * 在玩家完成登录前执行 OneBot QQ 群名片准入验证。
 *
 * [AsyncPlayerPreLoginEvent] 是在玩家真正加入前拒绝登录所必需的精确 Paper 生命周期事件，
 * TabooLib 没有可保留该拒绝语义的等价事件。该事件本身在异步登录线程触发，
 * 因此只在这里等待 OneBot 的有界异步请求，绝不阻塞 Folia 玩家或区域线程。
 */
internal object OneBotGroupAdmissionListener {

    private const val FALLBACK_UNAVAILABLE_MESSAGE = "QQ 群身份验证暂不可用，请联系运维人员。"

    private val admissionService = QqGroupAdmissionService(SimbotOneBotGroupMemberDirectory)

    /**
     * 登录阶段可用于群名片匹配的玩家名称。
     *
     * [bedrockLeadingCharacter] 仅在名称仍是服务端展示名时使用；已从 Floodgate 取得的真实基岩昵称
     * 本身没有前缀，因而为 null。
     */
    private data class AdmissionPlayerName(
        val value: String,
        val bedrockLeadingCharacter: String?,
    )

    /**
     * 验证待登录玩家是否有群名片包含其游戏名的指定 QQ 群成员。
     *
     * 已被其他插件拒绝的登录不会再调用 OneBot。群验证启用时，配置错误、请求失败、超时
     * 或未找到匹配群名片都会拒绝登录。
     *
     * @param event Paper 异步预登录事件。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPlayerPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return
        }

        val settings = runCatching(QqGroupAdmissionSettings::fromConfig)
            .getOrElse {
                warning("无法读取 OneBot QQ 群准入配置，已拒绝玩家 ${event.name} 的登录。")
                event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text(FALLBACK_UNAVAILABLE_MESSAGE),
                )
                return
            }
        if (!settings.enabled) {
            return
        }

        val playerName = resolveAdmissionPlayerName(event.uniqueId, event.name)
        val result = runCatching {
            admissionService.verifyAsync(
                settings = settings,
                serverPlayerName = playerName.value,
                bedrockLeadingCharacter = playerName.bedrockLeadingCharacter,
            ).join()
        }.getOrElse {
            QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.ONEBOT_UNAVAILABLE)
        }

        if (result !is QqGroupAdmissionResult.Denied) {
            return
        }

        logOperationalDenial(event.name, settings, result.reason)
        val message = when (result.reason) {
            QqGroupAdmissionDenyReason.INVALID_CONFIGURATION,
            QqGroupAdmissionDenyReason.ONEBOT_UNAVAILABLE -> settings.unavailableMessage
            QqGroupAdmissionDenyReason.INVALID_PLAYER_NAME,
            QqGroupAdmissionDenyReason.NO_MATCHING_GROUP_CARD -> settings.deniedMessage
        }
        event.disallow(
            AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
            Component.text(message),
        )
    }

    /**
     * 解析应参与群名片匹配的玩家名称。
     *
     * Floodgate 在处理 Bedrock 登录握手时会登记 [org.geysermc.floodgate.api.player.FloodgatePlayer]。
     * 如果该信息在预登录阶段已可读取，优先使用其 `username`：它是不带 Floodgate 前缀、未被
     * Java 用户名规则替换或截断的真实基岩昵称，也能正确覆盖已绑定的基岩账号。
     *
     * 某些 Floodgate 版本或异常登录流程中，该登记可能尚不可读取；此时对 UUID 格式可识别的
     * 未绑定基岩账号回退为从服务器名称移除第一个字符。Java 玩家始终保留完整服务器名称。
     *
     * @param playerId 待登录玩家 UUID。
     * @param serverPlayerName 服务器在预登录阶段看到的名称。
     * @return 用于 QQ 群名片匹配的名称及必要的首字符移除信息。
     */
    private fun resolveAdmissionPlayerName(
        playerId: UUID,
        serverPlayerName: String,
    ): AdmissionPlayerName {
        val rawBedrockName = runCatching {
            floodgateApi.getPlayer(playerId)?.username
        }.getOrNull()?.takeIf(String::isNotBlank)
        if (rawBedrockName != null) {
            return AdmissionPlayerName(rawBedrockName, null)
        }

        return AdmissionPlayerName(
            value = serverPlayerName,
            bedrockLeadingCharacter = bedrockLeadingCharacterToRemove(playerId, serverPlayerName),
        )
    }

    /**
     * 返回尚未取得真实昵称的基岩版服务器名称中需要移除的第一个字符。
     *
     * `FloodgateApi.isFloodgatePlayer` 的契约只覆盖在线玩家，不能用于预登录阶段；
     * 此处改用可离线识别未绑定基岩账户 UUID 格式的 `isFloodgateId`。根据准入规则，
     * 已识别的基岩版服务器名称一律移除第一个字符。
     *
     * @param playerId 待登录玩家 UUID。
     * @param serverPlayerName 服务器在预登录阶段看到的名称。
     * @return 基岩名称需要移除的第一个字符；Java 或无法识别的玩家返回 null。
     */
    private fun bedrockLeadingCharacterToRemove(playerId: UUID, serverPlayerName: String): String? {
        val isUnlinkedBedrockPlayer = runCatching {
            floodgateApi.isFloodgateId(playerId)
        }.getOrDefault(false)
        if (!isUnlinkedBedrockPlayer) {
            return null
        }

        return serverPlayerName.take(1)
    }

    private fun logOperationalDenial(
        playerName: String,
        settings: QqGroupAdmissionSettings,
        reason: QqGroupAdmissionDenyReason,
    ) {
        when (reason) {
            QqGroupAdmissionDenyReason.INVALID_CONFIGURATION -> {
                warning(
                    "OneBot QQ 群准入配置无效（${settings.validationError()}），已拒绝玩家 $playerName 的登录。",
                )
            }

            QqGroupAdmissionDenyReason.ONEBOT_UNAVAILABLE -> {
                warning("OneBot QQ 群准入请求失败或超时，已拒绝玩家 $playerName 的登录。")
            }

            QqGroupAdmissionDenyReason.INVALID_PLAYER_NAME,
            QqGroupAdmissionDenyReason.NO_MATCHING_GROUP_CARD -> Unit
        }
    }
}
