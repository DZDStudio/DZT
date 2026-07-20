package cn.tj.dzd.mc.dzt.log

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import java.sql.Timestamp

/**
 * 玩家行为日志持久化端口。
 */
interface PlayerLogRepository {

    /**
     * 写入一条玩家日志。
     *
     * @param type 日志类型。
     * @param message 已裁剪的日志内容。
     * @return 存储操作结果。
     */
    fun append(type: String, message: String): RepositoryResult<Unit>

    /**
     * 删除截止时间之前的日志。
     *
     * @param cutoff 不保留的最新时间边界。
     * @return 被删除的记录数。
     */
    fun deleteBefore(cutoff: Timestamp): RepositoryResult<Int>
}
