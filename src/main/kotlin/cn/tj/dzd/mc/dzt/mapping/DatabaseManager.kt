package cn.tj.dzd.mc.dzt.mapping

import org.bukkit.Bukkit
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.severe
import taboolib.expansion.submitChain
import taboolib.module.configuration.Configuration
import taboolib.module.database.getHost
import taboolib.platform.util.onlinePlayers
import taboolib.platform.util.runTask
import java.sql.SQLException

private val config = Configuration.loadFromFile(releaseResourceFile("Config.yml"))
val host = config.getHost("database")
val dataSource = host.createDataSource()

@Awake
object DatabaseManager {
    @Awake(LifeCycle.ACTIVE)
    fun onActive() {
        submitChain {
            async(period = 600L) {
                checkDatabaseStatus()
            }
        }
    }

    /**
     * 检查数据库状态
     */
    fun checkDatabaseStatus() {
        submitChain {
            async {
                try {
                    dataSource.connection.use { connection ->
                        if (!connection.isValid(5)) {
                            stopServer()
                        }
                    }
                } catch (e: SQLException) {
                    severe("数据库连接异常: ${e.message}")
                    stopServer()
                } catch (e: NoClassDefFoundError) {
                    severe("数据库异常: ${e.message}")
                    stopServer()
                }
            }
        }
    }

    /**
     * 停止服务器
     */
    private fun stopServer() {
        severe("数据库异常，正在终止服务器...")
        submitChain {
            sync {
                for (pl in onlinePlayers) {
                    pl.runTask({
                        pl.kickPlayer("我们无法连接至数据库，请联系运维排查原因。\nQWQ")
                    })
                }
                Bukkit.shutdown()
            }
        }
    }
}