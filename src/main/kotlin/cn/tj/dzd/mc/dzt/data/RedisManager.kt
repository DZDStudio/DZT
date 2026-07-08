package cn.tj.dzd.mc.dzt.data

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.expansion.AlkaidRedis
import taboolib.expansion.RedisMessage
import taboolib.expansion.SingleRedisConnection
import taboolib.expansion.fromConfig
import java.util.concurrent.TimeUnit

/**
 * Redis 连接管理器。
 *
 * 插件启动时会根据 config.yml 的 redis 节点自动创建 AlkaidRedis 连接。
 * 调用方应优先使用本管理器封装的方法执行 Redis 操作，避免重复创建连接池。
 */
object RedisManager {

    @Volatile
    private var connection: SingleRedisConnection? = null

    /**
     * 当前 Redis 连接是否已经创建。
     */
    val isAvailable: Boolean
        get() = connection != null

    /**
     * 获取当前 Redis 连接。
     *
     * @throws IllegalStateException 当 Redis 未启用或尚未完成初始化时抛出。
     */
    val redis: SingleRedisConnection
        get() = connection ?: error("Redis 连接尚未初始化，请检查 redis.enable 配置和插件启动日志。")

    /**
     * 根据 config.yml 自动创建 Redis 连接池。
     */
    @Awake(LifeCycle.LOAD)
    fun start() {
        if (connection != null) {
            return
        }
        val pluginConfig = runCatching { config }
            .getOrElse { error("config.yml 尚未加载，无法创建 Redis 连接。") }
        if (!pluginConfig.getBoolean("redis.enable", true)) {
            info("Redis 未启用，跳过 Redis 客户端初始化。")
            return
        }

        val redisSection = pluginConfig.getConfigurationSection("redis")
            ?: error("config.yml 缺少 redis 配置节点，无法创建 Redis 连接。")
        connection = AlkaidRedis.createDefault {
            it.fromConfig(redisSection)
            it.reconnectDelay(redisSection.getLong("reconnectDelay", 1000L))
        }
        info("Redis 连接已初始化。")
    }

    /**
     * 插件关闭时释放 Redis 连接池资源。
     */
    @Awake(LifeCycle.DISABLE)
    fun stop() {
        connection?.close()
        connection = null
    }

    /**
     * 读取 Redis 字符串值。
     *
     * @param key 键。
     * @return 键对应的字符串值；不存在时返回 null。
     */
    operator fun get(key: String): String? {
        return redis[key]
    }

    /**
     * 写入 Redis 字符串值。
     *
     * @param key 键。
     * @param value 值；传入 null 时删除该键。
     */
    operator fun set(key: String, value: String?) {
        redis[key] = value
    }

    /**
     * 写入 Redis 字符串值并设置过期时间。
     *
     * @param key 键。
     * @param value 值。
     * @param timeout 过期时长。
     * @param timeUnit 过期时长单位。
     */
    fun setEx(key: String, value: String?, timeout: Long, timeUnit: TimeUnit = TimeUnit.SECONDS) {
        redis.setEx(key, value, timeout, timeUnit)
    }

    /**
     * 删除 Redis 键。
     *
     * @param key 键。
     */
    fun delete(key: String) {
        redis.delete(key)
    }

    /**
     * 发布 Redis 订阅消息。
     *
     * @param channel 频道。
     * @param message 消息对象；非字符串对象会使用 TabooLib Configuration 序列化。
     */
    fun publish(channel: String, message: Any) {
        redis.publish(channel, message)
    }

    /**
     * 订阅 Redis 频道。
     *
     * AlkaidRedis 会在线程池中执行订阅逻辑，不会阻塞调用线程。
     *
     * @param channel 频道列表。
     * @param patternMode 是否使用模式订阅。
     * @param handler 消息处理回调。
     */
    fun subscribe(vararg channel: String, patternMode: Boolean = false, handler: RedisMessage.() -> Unit) {
        redis.subscribe(*channel, patternMode = patternMode, func = handler)
    }
}
