package cn.tj.dzd.mc.dzt.data

import taboolib.expansion.DataMapper
import taboolib.expansion.MapperConfig
import taboolib.expansion.mapper
import kotlin.properties.ReadOnlyProperty

private const val BEAN_CACHE_MAXIMUM_SIZE = 10_000
private const val QUERY_CACHE_MAXIMUM_SIZE = 1_000
private const val CACHE_EXPIRE_AFTER_ACCESS_SECONDS = 600L

/**
 * 创建启用 TabooLib L2 双层缓存的数据库映射器。
 *
 * L2 缓存由 Bean Cache 和 Query Cache 组成：
 * - Bean Cache 缓存按实体 ID 查询的结果，写入同一 ID 时细粒度失效。
 * - Query Cache 缓存条件查询结果，任意写操作后按表清空，避免旧查询结果残留。
 *
 * @param source 数据库配置源。
 * @param flags 数据库连接标记。
 * @param clearFlags 是否清空默认连接标记。
 * @param ssl SSL 配置。
 * @param extraConfig 表额外 mapper 配置。
 * @return 启用 L2 缓存的 [DataMapper] 属性委托。
 */
inline fun <reified T> cachedMapper(
    source: Any = DataSource,
    flags: List<String> = emptyList(),
    clearFlags: Boolean = false,
    ssl: String? = null,
    noinline extraConfig: MapperConfig<T>.() -> Unit = {},
): ReadOnlyProperty<Any?, DataMapper<T>> {
    return mapper<T>(source, flags, clearFlags, ssl) {
        enableDztL2Cache()
        extraConfig()
    }
}

/**
 * 为 TabooLib mapper 启用 DZT 统一 L2 缓存策略。
 */
fun <T> MapperConfig<T>.enableDztL2Cache() {
    cache {
        beanCache {
            maximumSize = BEAN_CACHE_MAXIMUM_SIZE
            expireAfterAccess = CACHE_EXPIRE_AFTER_ACCESS_SECONDS
        }
        queryCache {
            maximumSize = QUERY_CACHE_MAXIMUM_SIZE
            expireAfterAccess = CACHE_EXPIRE_AFTER_ACCESS_SECONDS
        }
    }
}
