package cn.tj.dzd.mc.dzt.platform

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * DZT 统一异步任务执行器。
 *
 * 该执行器仅用于数据库、Redis 与外部服务等不操作 Bukkit 实体状态的任务。
 * 玩家、实体和世界操作仍必须通过 Folia 对应的调度器执行。
 */
object DztAsyncExecutor {

    private val threadSequence = AtomicInteger()
    private val lifecycleLock = Any()
    private val pending = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()

    @Volatile
    private var executor: ExecutorService? = null

    @Volatile
    private var disabled = false

    /**
     * 启动异步执行器。该方法是幂等的。
     */
    @Awake(LifeCycle.ACTIVE)
    fun start() {
        disabled = false
        executorService()
    }

    /**
     * 在 DZT 管理的异步线程上计算结果。
     *
     * @param block 不得直接访问 Bukkit 实体或世界状态的任务。
     * @return 承载任务结果的 Future；插件关闭后提交时将异常完成。
     */
    fun <T> supply(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (disabled) {
            future.completeExceptionally(RejectedExecutionException("DZT 异步执行器已停止"))
            return future
        }
        pending += future
        try {
            executorService().execute {
                if (future.isCancelled) {
                    pending -= future
                    return@execute
                }
                try {
                    future.complete(block())
                } catch (error: Throwable) {
                    future.completeExceptionally(error)
                } finally {
                    pending -= future
                }
            }
        } catch (error: RejectedExecutionException) {
            pending -= future
            future.completeExceptionally(error)
        }
        return future
    }

    /**
     * 停止执行器并取消尚未开始的任务。该方法是幂等的。
     */
    @Awake(LifeCycle.DISABLE)
    fun stop() {
        disabled = true
        val current = synchronized(lifecycleLock) {
            executor.also { executor = null }
        } ?: return

        current.shutdownNow()
        pending.forEach { future ->
            future.completeExceptionally(CancellationException("DZT 插件正在关闭"))
        }
        pending.clear()
        runCatching {
            current.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun executorService(): ExecutorService {
        if (disabled) {
            throw RejectedExecutionException("DZT 异步执行器已停止")
        }
        executor?.takeUnless { it.isShutdown }?.let { return it }
        return synchronized(lifecycleLock) {
            if (disabled) {
                throw RejectedExecutionException("DZT 异步执行器已停止")
            }
            executor?.takeUnless { it.isShutdown } ?: createExecutor().also { executor = it }
        }
    }

    private fun createExecutor(): ExecutorService {
        val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(MIN_THREADS, MAX_THREADS)
        return Executors.newFixedThreadPool(threadCount) { task ->
            Thread(task, "dzt-async-${threadSequence.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
    }

    private const val MIN_THREADS = 2
    private const val MAX_THREADS = 8
    private const val SHUTDOWN_TIMEOUT_SECONDS = 3L
}
