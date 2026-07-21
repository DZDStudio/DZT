package cn.tj.dzd.mc.dzt.commission

import cn.tj.dzd.mc.dzt.core.RepositoryResult
import cn.tj.dzd.mc.dzt.data.repository.PersistentCommissionItemReturnRepository
import cn.tj.dzd.mc.dzt.platform.DztAsyncExecutor
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.severe
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** 一条待返还委托物品的持久化快照。 */
data class CommissionPendingItemReturn(
    val returnId: UUID,
    val playerId: UUID,
    val commissionId: String,
    val itemPayload: ByteArray,
    val createdAt: Long,
)

/**
 * 待返还委托物品的仓库端口。
 *
 * 所有实现必须将 [itemPayload] 作为不透明的 Paper ItemStack NBT 数据保存，不能转换为裸材料和数量。
 */
interface CommissionItemReturnRepository {

    /** 登记一条尚未放回背包的物品返还记录。 */
    fun enqueue(record: CommissionPendingItemReturn): RepositoryResult<Unit>

    /** 查询一个玩家全部待返还的物品记录。 */
    fun findAll(playerId: UUID): RepositoryResult<List<CommissionPendingItemReturn>>

    /** 在部分返还后，将记录更新为仍未放入背包的物品。 */
    fun replacePayload(returnId: UUID, payload: ByteArray): RepositoryResult<Unit>

    /** 在全部物品实际放入背包后删除记录。 */
    fun delete(returnId: UUID): RepositoryResult<Unit>
}

/** 已登记返还物品的投递结果。 */
enum class CommissionItemReturnDelivery {
    /** 全部物品已放入玩家背包。 */
    DELIVERED,
    /** 记录已安全保留，等待下次玩家在线且背包有空间时继续投递。 */
    QUEUED,
    /** 记录与即时返还均无法完成，需要管理员核对。 */
    FAILED,
}

/**
 * 委托上交失败后的物品返还服务。
 *
 * 先将物品保真数据写入 Persistent Container，再尝试返还。这样玩家在异步提交期间离线、背包暂时没有
 * 空间或服务器在返还后中断时，至少会保留一条可于后续登录重试的记录；极端中断时宁可重复返还，也不静默
 * 吞掉玩家物品。
 */
object CommissionItemReturnService {

    private val repository: CommissionItemReturnRepository = PersistentCommissionItemReturnRepository
    private val activeDeliveries = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * 登记并尝试立即返还已经从玩家背包扣除的物品。
     *
     * [items] 必须是玩家实体线程中复制出的独立 ItemStack；Paper 的数组 NBT 序列化仅访问这些副本，
     * 不会读取玩家或世界状态。
     *
     * @param inventory 当前玩家的 Folia 背包端口。
     * @param commissionId 导致返还的委托 ID。
     * @param items 要返还的原始物品副本。
     * @return 物品是否已返还或安全排入待返还队列。
     */
    internal fun queueAndDeliver(
        inventory: FoliaCommissionItemInventoryPort,
        commissionId: String,
        items: List<ItemStack>,
    ): CompletableFuture<CommissionItemReturnDelivery> {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(CommissionItemReturnDelivery.DELIVERED)
        }

        val payload = runCatching { ItemStack.serializeItemsAsBytes(items) }
            .getOrElse { error ->
                severe("委托待返还物品序列化失败。", "委托 ID: $commissionId", error.stackTraceToString())
                return inventory.restoreItems(items).thenApply { restored ->
                    if (restored is CommissionInventoryRestoreResult.Restored && restored.remainingPayload == null) {
                        CommissionItemReturnDelivery.DELIVERED
                    } else {
                        CommissionItemReturnDelivery.FAILED
                    }
                }
            }
        val record = CommissionPendingItemReturn(
            returnId = UUID.randomUUID(),
            playerId = inventory.playerId,
            commissionId = commissionId,
            itemPayload = payload,
            createdAt = System.currentTimeMillis(),
        )

        return DztAsyncExecutor.supply {
            repository.enqueue(record)
        }.thenCompose { queued ->
            if (queued is RepositoryResult.Success) {
                deliverRecord(inventory, record)
            } else {
                severe(
                    "委托待返还物品无法写入数据库，正在直接尝试返还。",
                    "玩家 UUID: ${record.playerId}",
                    "委托 ID: ${record.commissionId}",
                )
                inventory.restoreItems(items).thenApply { restored ->
                    if (restored is CommissionInventoryRestoreResult.Restored && restored.remainingPayload == null) {
                        CommissionItemReturnDelivery.DELIVERED
                    } else {
                        CommissionItemReturnDelivery.FAILED
                    }
                }
            }
        }
    }

    /**
     * 在玩家登录后重试投递其全部待返还物品。
     *
     * @param player 已在线的玩家；具体背包修改由内部 Folia 端口调度到实体线程。
     * @return 所有当前记录处理完成后的 Future。
     */
    fun deliverPending(player: Player): CompletableFuture<Unit> {
        val playerId = player.uniqueId
        return DztAsyncExecutor.supply {
            when (val result = repository.findAll(playerId)) {
                is RepositoryResult.Success -> result.value
                RepositoryResult.Failure -> emptyList()
            }
        }.thenCompose { records ->
            records.fold(CompletableFuture.completedFuture(Unit)) { chain, record ->
                chain.thenCompose {
                    deliverRecord(FoliaCommissionItemInventoryPort(player, playerId), record).thenApply { Unit }
                }
            }
        }
    }

    private fun deliverRecord(
        inventory: FoliaCommissionItemInventoryPort,
        record: CommissionPendingItemReturn,
    ): CompletableFuture<CommissionItemReturnDelivery> {
        if (!activeDeliveries.add(record.returnId)) {
            return CompletableFuture.completedFuture(CommissionItemReturnDelivery.QUEUED)
        }

        return inventory.restorePayload(record.itemPayload).thenCompose { restored ->
            when (restored) {
                CommissionInventoryRestoreResult.PlayerUnavailable -> {
                    CompletableFuture.completedFuture(CommissionItemReturnDelivery.QUEUED)
                }

                CommissionInventoryRestoreResult.Failed -> {
                    severe(
                        "委托待返还物品无法投递，记录将保留至下次登录。",
                        "玩家 UUID: ${record.playerId}",
                        "委托 ID: ${record.commissionId}",
                        "返还记录 ID: ${record.returnId}",
                    )
                    CompletableFuture.completedFuture(CommissionItemReturnDelivery.QUEUED)
                }

                is CommissionInventoryRestoreResult.Restored -> {
                    persistDeliveryResult(record, restored.remainingPayload)
                }
            }
        }.whenComplete { _, _ -> activeDeliveries.remove(record.returnId) }
    }

    private fun persistDeliveryResult(
        record: CommissionPendingItemReturn,
        remainingPayload: ByteArray?,
    ): CompletableFuture<CommissionItemReturnDelivery> {
        return DztAsyncExecutor.supply {
            if (remainingPayload == null) {
                repository.delete(record.returnId)
            } else {
                repository.replacePayload(record.returnId, remainingPayload)
            }
        }.thenApply { result ->
            if (result is RepositoryResult.Success) {
                if (remainingPayload == null) {
                    CommissionItemReturnDelivery.DELIVERED
                } else {
                    CommissionItemReturnDelivery.QUEUED
                }
            } else {
                severe(
                    "委托物品已尝试返还，但待返还记录状态无法更新；记录将保留以避免物品丢失。",
                    "玩家 UUID: ${record.playerId}",
                    "委托 ID: ${record.commissionId}",
                    "返还记录 ID: ${record.returnId}",
                )
                CommissionItemReturnDelivery.QUEUED
            }
        }
    }
}
