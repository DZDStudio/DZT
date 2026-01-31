package cn.tj.dzd.mc.dzt.mapping

import org.bukkit.Bukkit
import org.bukkit.Location
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.severe
import taboolib.module.configuration.Configuration
import taboolib.module.database.getHost
import taboolib.platform.util.runTask
import java.sql.SQLException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val config = Configuration.loadFromFile(releaseResourceFile("Config.yml"))
val host = config.getHost("database")
val dataSource = host.createDataSource()

object DatabaseManager {
    
    private val isDatabaseOnline = AtomicBoolean(false)
    private val scheduler = Executors.newScheduledThreadPool(1)
    
    init {
        // 初始化时测试数据库连接
        testConnection()
        
        // 启动定时检查任务
        scheduler.scheduleAtFixedRate({
            try {
                if (!testConnection()) {
                    severe("§c数据库连接丢失！正在关闭服务器...")
                    stopServer()
                }
            } catch (e: Exception) {
                severe("§c数据库监控出现异常: ${e.message}")
                stopServer()
            }
        }, 5, 30, TimeUnit.SECONDS) // 每30秒检查一次，延迟5秒开始
    }

    private fun stopServer() {
        Location(Bukkit.getWorlds()[0], 0.0, 0.0, 0.0).runTask({
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "stop")
            Bukkit.shutdown()
        })
    }
    
    /**
     * 测试数据库连接
     */
    private fun testConnection(): Boolean {
        return try {
            val connection = dataSource.connection
            if (connection.isValid(5)) { // 5秒超时验证连接
                connection.close()
                isDatabaseOnline.set(true)
                true
            } else {
                isDatabaseOnline.set(false)
                false
            }
        } catch (e: SQLException) {
            severe("§c数据库连接失败: ${e.message}")
            isDatabaseOnline.set(false)
            false
        } catch (e: Exception) {
            severe("§c数据库连接异常: ${e.message}")
            isDatabaseOnline.set(false)
            false
        }
    }
    
    /**
     * 检查数据库状态
     * @return 如果数据库在线返回true，否则直接关闭服务器
     */
    fun checkDatabaseStatus(): Boolean {
        if (isDatabaseOnline.get()) {
            return true
        } else {
            severe("§c数据库当前不在线！正在关闭服务器以防止数据损坏...")
            stopServer()
            return false
        }
    }
    
    /**
     * 获取数据库连接状态
     */
    fun isDatabaseOnline(): Boolean {
        return isDatabaseOnline.get()
    }
    
    /**
     * 关闭数据库管理器
     */
    fun shutdown() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
        
        // 尝试关闭数据源 - 使用反射或其他方式根据实际类型关闭
        try {
            if (dataSource is AutoCloseable) {
                dataSource.close()
            }
        } catch (e: Exception) {
            severe("§c关闭数据源时发生错误: ${e.message}")
        }
    }
}