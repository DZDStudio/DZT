package cn.tj.dzd.mc.dzt.core

/**
 * 持久化端口的通用结果。
 *
 * 基础设施层应在返回 [Failure] 前统一记录原始异常，避免业务层依赖数据库异常类型。
 */
sealed interface RepositoryResult<out T> {

    /** 持久化操作成功。 */
    data class Success<T>(val value: T) : RepositoryResult<T>

    /** 持久化操作失败。 */
    data object Failure : RepositoryResult<Nothing>
}
