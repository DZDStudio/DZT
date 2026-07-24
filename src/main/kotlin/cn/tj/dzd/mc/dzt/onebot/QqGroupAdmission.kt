package cn.tj.dzd.mc.dzt.onebot

import cn.tj.dzd.mc.dzt.data.config
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OneBot 11 群成员查询所需的连接与群组参数。
 *
 * @property groupId 要查询的 QQ 群号。
 * @property apiServer OneBot HTTP API 根地址，不包含具体 action。
 * @property accessToken OneBot access token；为 null 时不发送鉴权头。
 */
internal data class OneBotGroupMemberRequest(
    val groupId: Long,
    val apiServer: String,
    val accessToken: String?,
)

/**
 * 查询 QQ 群成员群名片的端口。
 *
 * 实现负责与 OneBot 服务通信；调用者不应依赖 Simbot 或 OneBot 的响应类型，
 * 以便准入规则能独立测试和替换数据来源。
 */
internal interface OneBotGroupMemberDirectory {

    /**
     * 异步获取指定 QQ 群所有成员的群名片（OneBot `card` 字段）。
     *
     * 空群名片会以空字符串保留；调用者应自行决定是否把它视为匹配项。
     * 调用方可能在登录超时时取消返回的 Future；实现必须将该取消传递给底层 I/O，
     * 避免不可用的 OneBot 请求持续占用连接和协程。
     *
     * @param request OneBot 连接信息和目标群号。
     * @return 成功时承载全部群名片的 Future；HTTP、协议或解析失败时异常完成。
     */
    fun groupMemberCards(request: OneBotGroupMemberRequest): CompletableFuture<List<String>>
}

/**
 * QQ 群名片准入的运行时配置。
 *
 * @property enabled 是否执行 QQ 群验证。
 * @property groupId 要验证的 QQ 群号。
 * @property apiServer OneBot HTTP API 根地址。
 * @property accessToken OneBot access token；允许为空。
 * @property timeoutMillis 一次群成员查询的最长等待时间。
 * @property deniedMessage 未找到匹配群名片时显示的拒绝提示。
 * @property unavailableMessage 配置错误或 OneBot 不可用时显示的拒绝提示。
 */
internal data class QqGroupAdmissionSettings(
    val enabled: Boolean,
    val groupId: Long,
    val apiServer: String,
    val accessToken: String?,
    val timeoutMillis: Long,
    val deniedMessage: String,
    val unavailableMessage: String,
) {

    /**
     * 返回当前启用配置的校验错误；配置可用时返回 null。
     */
    fun validationError(): String? {
        if (groupId <= 0) {
            return "group-id 必须大于 0"
        }
        return connectionValidationError()
    }

    /**
     * 返回 OneBot HTTP 连接配置的校验错误；连接配置可用时返回 null。
     *
     * 该校验不依赖 QQ 群准入是否启用，也不校验准入群号，供发送群消息等其他 OneBot
     * 功能复用同一套连接配置。
     *
     * @return 连接配置错误说明；配置可用时返回 null。
     */
    fun connectionValidationError(): String? {
        if (apiServer.isBlank()) {
            return "api-server 不能为空"
        }
        if (timeoutMillis !in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT_MILLIS) {
            return "timeout-millis 必须介于 $MIN_TIMEOUT_MILLIS 和 $MAX_TIMEOUT_MILLIS"
        }
        return null
    }

    companion object {
        const val MIN_TIMEOUT_MILLIS = 500L
        const val MAX_TIMEOUT_MILLIS = 10_000L

        private const val CONFIG_PATH = "onebot.group-admission"
        private const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        private const val DEFAULT_DENIED_MESSAGE = "请先加入DZD玩家交流群[QQ:747121127]，并将群昵称设置为包含你的游戏名后再进入服务器。"
        private const val DEFAULT_UNAVAILABLE_MESSAGE = "QQ 群身份验证暂不可用，请联系运维人员。"

        /**
         * 从主配置读取当前 QQ 群准入设置。
         *
         * `config.yml` 支持自动重载，因此每次调用都会取得最新配置；
         * access token 不会被记录或暴露到验证结果中。
         *
         * @return 当前配置快照。
         */
        fun fromConfig(): QqGroupAdmissionSettings {
            return QqGroupAdmissionSettings(
                enabled = config.getBoolean("$CONFIG_PATH.enable", false),
                groupId = config.getLong("$CONFIG_PATH.group-id", 0L),
                apiServer = config.getString("$CONFIG_PATH.api-server", "")?.trim().orEmpty(),
                accessToken = config.getString("$CONFIG_PATH.access-token", "")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
                timeoutMillis = config.getLong("$CONFIG_PATH.timeout-millis", DEFAULT_TIMEOUT_MILLIS),
                deniedMessage = configMessage("$CONFIG_PATH.deny-message", DEFAULT_DENIED_MESSAGE),
                unavailableMessage = configMessage("$CONFIG_PATH.unavailable-message", DEFAULT_UNAVAILABLE_MESSAGE),
            )
        }

        private fun configMessage(path: String, fallback: String): String {
            return config.getString(path, fallback)
                ?.takeIf(String::isNotBlank)
                ?: fallback
        }
    }
}

/** QQ 群验证未放行的原因。 */
internal enum class QqGroupAdmissionDenyReason {
    INVALID_CONFIGURATION,
    INVALID_PLAYER_NAME,
    NO_MATCHING_GROUP_CARD,
    ONEBOT_UNAVAILABLE,
}

/** QQ 群验证的结果。 */
internal sealed interface QqGroupAdmissionResult {

    /** 玩家已经满足准入条件，或该功能未启用。 */
    data object Allowed : QqGroupAdmissionResult

    /** 玩家未满足准入条件。 */
    data class Denied(val reason: QqGroupAdmissionDenyReason) : QqGroupAdmissionResult
}

/**
 * 不依赖 Bukkit、Floodgate 或 Simbot 的 QQ 群名片匹配规则。
 */
internal object QqGroupAdmissionPolicy {

    /**
     * 得到用于群名片匹配的游戏名。
     *
     * 基岩版名称传入其首字符时会移除该字符，严格遵循“去除玩家名第一个字符”的规则。
     * 空名称不能参与匹配，避免 `String.contains("")` 意外放行所有玩家。
     *
     * @param serverPlayerName 服务器在登录阶段看到的玩家名称。
     * @param bedrockLeadingCharacter 基岩版名称需要去除的第一个字符；Java 玩家传 null。
     * @return 可用于匹配的名称；为空或仅空白字符时返回 null。
     */
    fun matchPlayerName(serverPlayerName: String, bedrockLeadingCharacter: String?): String? {
        val matchingName = bedrockLeadingCharacter
            ?.takeIf(String::isNotEmpty)
            ?.let(serverPlayerName::removePrefix)
            ?: serverPlayerName
        return matchingName.takeIf(String::isNotBlank)
    }

    /**
     * 判断群名片中是否有任意一项包含玩家名称。
     *
     * 匹配大小写敏感，且只检查 OneBot 的群名片（`card`）而不回退到 QQ 昵称。
     *
     * @param groupCards 待检查的群名片列表。
     * @param playerName 已规范化的非空游戏名。
     * @return 任一群名片包含 [playerName] 时返回 true。
     */
    fun hasMatchingGroupCard(groupCards: Iterable<String>, playerName: String): Boolean {
        return playerName.isNotEmpty() && groupCards.any { card -> card.contains(playerName) }
    }
}

/**
 * 将 QQ 群名片规则与 OneBot 数据目录组合为异步准入检查。
 *
 * 该服务不会访问 Bukkit；调用者可在异步登录阶段等待返回的 Future，
 * 而 Folia 玩家线程不应阻塞等待远程 OneBot 服务。
 */
internal class QqGroupAdmissionService(
    private val groupMemberDirectory: OneBotGroupMemberDirectory,
) {

    /**
     * 发起一次 QQ 群名片准入验证。
     *
     * 配置无效、玩家名为空、OneBot 超时或请求失败均会失败关闭，返回 [QqGroupAdmissionResult.Denied]。
     * 功能未启用时不会发起远程请求。
     *
     * @param settings 当前 QQ 群准入配置。
     * @param serverPlayerName 服务器看到的玩家名称。
     * @param bedrockLeadingCharacter 基岩版名称需要去除的第一个字符；Java 玩家传 null。
     * @return 承载验证结果的 Future。
     */
    fun verifyAsync(
        settings: QqGroupAdmissionSettings,
        serverPlayerName: String,
        bedrockLeadingCharacter: String?,
    ): CompletableFuture<QqGroupAdmissionResult> {
        if (!settings.enabled) {
            return CompletableFuture.completedFuture(QqGroupAdmissionResult.Allowed)
        }
        if (settings.validationError() != null) {
            return CompletableFuture.completedFuture(
                QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.INVALID_CONFIGURATION),
            )
        }

        val playerName = QqGroupAdmissionPolicy.matchPlayerName(serverPlayerName, bedrockLeadingCharacter)
            ?: return CompletableFuture.completedFuture(
                QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.INVALID_PLAYER_NAME),
            )

        val request = OneBotGroupMemberRequest(
            groupId = settings.groupId,
            apiServer = settings.apiServer,
            accessToken = settings.accessToken,
        )
        val groupCardsFuture = runCatching {
            groupMemberDirectory.groupMemberCards(request)
        }.getOrElse {
            return CompletableFuture.completedFuture(oneBotUnavailableResult())
        }

        if (groupCardsFuture.isDone) {
            return groupCardsFuture.handle { groupCards, error ->
                resultFromGroupCards(groupCards, error, playerName)
            }
        }

        return completeWithinTimeout(groupCardsFuture, settings.timeoutMillis, playerName)
    }

    /**
     * 将 OneBot 查询结果限制在登录配置的等待时间内。
     *
     * 不能对 [source] 直接调用 `orTimeout`：该方法只会让 Future 异常完成，
     * 无法通知 Simbot/Ktor 取消仍在进行的 HTTP 请求。这里使用独立的截止 Future，
     * 在截止时间胜出时显式取消 [source]，使目录实现有机会释放底层请求。
     *
     * @param source 正在进行的群成员查询；实现必须将取消传递给底层 I/O。
     * @param timeoutMillis 最长等待时间。
     * @param playerName 已规范化的非空游戏名。
     * @return 承载准入结果的 Future。
     */
    private fun completeWithinTimeout(
        source: CompletableFuture<List<String>>,
        timeoutMillis: Long,
        playerName: String,
    ): CompletableFuture<QqGroupAdmissionResult> {
        val result = CompletableFuture<QqGroupAdmissionResult>()
        val deadline = CompletableFuture<Unit>()
        val completed = AtomicBoolean(false)

        deadline.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
            .whenComplete { _, error ->
                if (error is TimeoutException && completed.compareAndSet(false, true)) {
                    source.cancel(true)
                    result.complete(oneBotUnavailableResult())
                }
            }

        source.whenComplete { groupCards, error ->
            if (completed.compareAndSet(false, true)) {
                // 取消独立 deadline，避免成功请求仍保留一个无用的延迟任务。
                deadline.cancel(false)
                result.complete(resultFromGroupCards(groupCards, error, playerName))
            }
        }
        return result
    }

    /**
     * 将 OneBot 响应或异常转换为准入结果。
     *
     * @param groupCards 成功查询到的群名片；失败时可能为 null。
     * @param error OneBot 请求失败原因；为 null 表示请求成功。
     * @param playerName 已规范化的非空游戏名。
     * @return 对应的准入结果。
     */
    private fun resultFromGroupCards(
        groupCards: List<String>?,
        error: Throwable?,
        playerName: String,
    ): QqGroupAdmissionResult {
        return when {
            error != null -> oneBotUnavailableResult()
            QqGroupAdmissionPolicy.hasMatchingGroupCard(groupCards.orEmpty(), playerName) -> {
                QqGroupAdmissionResult.Allowed
            }

            else -> QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.NO_MATCHING_GROUP_CARD)
        }
    }

    private fun oneBotUnavailableResult(): QqGroupAdmissionResult {
        return QqGroupAdmissionResult.Denied(QqGroupAdmissionDenyReason.ONEBOT_UNAVAILABLE)
    }
}
