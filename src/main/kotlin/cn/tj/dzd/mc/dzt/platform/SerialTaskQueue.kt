package cn.tj.dzd.mc.dzt.platform

import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException

/**
 * 在 [DztAsyncExecutor] 上保持提交顺序的轻量任务队列。
 *
 * 队列中的前一个任务即使失败，后续任务仍会继续执行。
 */
class SerialTaskQueue {

    private val lock = Any()
    private var tail: CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
    private var closed = false

    /**
     * 按提交顺序执行任务。
     *
     * @param block 不得访问 Bukkit 实体或世界状态的异步任务。
     * @return 任务完成 Future；队列关闭后提交时将异常完成。
     */
    fun submit(block: () -> Unit): CompletableFuture<Unit> = synchronized(lock) {
        if (closed) {
            return@synchronized CompletableFuture.failedFuture(
                RejectedExecutionException("DZT 串行任务队列已关闭")
            )
        }
        tail = tail.handle { _, _ -> Unit }
            .thenCompose {
                DztAsyncExecutor.supply {
                    block()
                    Unit
                }
            }
        tail
    }

    /**
     * 停止接受新任务。已提交任务仍由 DZT 异步执行器管理。
     */
    fun close() {
        synchronized(lock) {
            closed = true
        }
    }
}
