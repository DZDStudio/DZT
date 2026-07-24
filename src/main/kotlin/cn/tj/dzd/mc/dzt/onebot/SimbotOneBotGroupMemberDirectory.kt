package cn.tj.dzd.mc.dzt.onebot

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import love.forte.simbot.common.id.LongID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.api.GetGroupMemberListApi
import love.forte.simbot.component.onebot.v11.core.api.requestDataAsync
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.warning
import java.util.concurrent.CompletableFuture

/**
 * 由 DZT 统一持有的 Simbot OneBot HTTP 客户端。
 *
 * 群准入和群消息发送复用同一客户端与连接池。客户端的最大请求、连接和套接字时间
 * 均受 [QqGroupAdmissionSettings.MAX_TIMEOUT_MILLIS] 限制；具体功能还可设置更短的等待时间。
 */
internal object SimbotOneBotHttpClient {

    private val httpClient = lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = QqGroupAdmissionSettings.MAX_TIMEOUT_MILLIS
                connectTimeoutMillis = QqGroupAdmissionSettings.MAX_TIMEOUT_MILLIS
                socketTimeoutMillis = QqGroupAdmissionSettings.MAX_TIMEOUT_MILLIS
            }
        }
    }

    /**
     * 获取共享 OneBot HTTP 客户端。
     *
     * @return 已初始化或新建的共享客户端。
     */
    val client: HttpClient
        get() = httpClient.value

    /** 插件禁用时关闭 HTTP 客户端，停止未完成请求并释放连接资源。 */
    @Awake(LifeCycle.DISABLE)
    fun close() {
        if (httpClient.isInitialized()) {
            httpClient.value.close()
        }
    }
}

/**
 * 基于 Simbot OneBot 11 typed API 的 QQ 群成员目录。
 *
 * 只使用 OneBot HTTP API，不启动 Simbot Application 或 WebSocket 事件订阅；
 * 这正好满足登录时读取 `get_group_member_list` 的需求。
 */
internal object SimbotOneBotGroupMemberDirectory : OneBotGroupMemberDirectory {

    /**
     * 使用 Simbot 的 `get_group_member_list` 请求群成员列表，并仅投影出 `card` 群名片字段。
     *
     * @param request OneBot HTTP API 地址、令牌和目标群号。
     * @return 成功时承载群名片列表的 Future；OneBot 返回失败、HTTP 失败或解析失败时异常完成。
    */
    // 登录阶段以 CompletableFuture 组合并取消请求，因此有意使用 Simbot 面向 Java 的异步入口。
    @OptIn(love.forte.simbot.annotations.Api4J::class)
    override fun groupMemberCards(request: OneBotGroupMemberRequest): CompletableFuture<List<String>> {
        val membersFuture = runCatching {
            GetGroupMemberListApi.create(request.groupId.ID)
                .requestDataAsync(
                    client = SimbotOneBotHttpClient.client,
                    host = request.apiServer,
                    accessToken = request.accessToken,
                )
        }.getOrElse { error ->
            logGroupMemberRequestFailure(request, error)
            return CompletableFuture.failedFuture(error)
        }
        val cardsFuture = CompletableFuture<List<String>>()

        membersFuture.whenComplete { members, error ->
            if (error != null) {
                // 超时或调用方主动取消时 cardsFuture 已被取消；不把正常的取消路径误报成连接故障。
                if (!cardsFuture.isCancelled) {
                    logGroupMemberRequestFailure(request, error)
                }
                cardsFuture.completeExceptionally(error)
                return@whenComplete
            }

            runCatching { members.map { it.card } }
                .onSuccess(cardsFuture::complete)
                .onFailure(cardsFuture::completeExceptionally)
        }
        cardsFuture.whenComplete { _, _ ->
            // `thenApply` 的取消不会反向取消父 Future；显式传递取消，
            // 让 Simbot 的 coroutine-backed Future 中止正在执行的 Ktor 请求。
            if (cardsFuture.isCancelled) {
                membersFuture.cancel(true)
            }
        }
        return cardsFuture
    }

    /**
     * 记录一次不会向玩家暴露的 OneBot 请求失败详情。
     *
     * 日志只包含根异常的类型和单行消息，且会替换配置中的 access token，便于排查网络、鉴权
     * 与协议错误而不会泄露敏感连接信息。
     */
    private fun logGroupMemberRequestFailure(request: OneBotGroupMemberRequest, error: Throwable) {
        val rootError = rootCause(error)
        val type = rootError::class.qualifiedName ?: rootError.javaClass.name
        val rawMessage = rootError.message?.replace(Regex("[\\r\\n]+"), " ")?.trim().orEmpty()
        val message = request.accessToken
            ?.takeIf(String::isNotEmpty)
            ?.let { token -> rawMessage.replace(token, "***") }
            ?: rawMessage
        val detail = message.takeIf(String::isNotEmpty)?.let { ": $it" }.orEmpty()
        warning("OneBot get_group_member_list 请求失败（群 ${request.groupId}）：$type$detail")
    }

    private fun rootCause(error: Throwable): Throwable {
        var current = error
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }
}
