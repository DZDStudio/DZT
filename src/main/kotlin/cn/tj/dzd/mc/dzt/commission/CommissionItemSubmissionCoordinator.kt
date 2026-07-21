package cn.tj.dzd.mc.dzt.commission

import cn.tj.dzd.mc.dzt.util.foliaRun
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.severe
import taboolib.library.xseries.XMaterial
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** 上交物品操作的最终状态。 */
enum class CommissionItemSubmissionStatus {
    SUCCESS,
    BUSY,
    COMMISSION_NOT_FOUND,
    NOT_ITEM_SUBMISSION,
    ALREADY_COMPLETED,
    NO_MATCHING_ITEM,
    EXPIRED,
    STORAGE_FAILURE,
    RESTORE_QUEUED,
    RESTORE_FAILURE,
    PLAYER_UNAVAILABLE,
}

/** 上交物品操作的结果。 */
data class CommissionItemSubmissionResult(
    val status: CommissionItemSubmissionStatus,
    val submittedAmount: Int = 0,
    val currentProgress: Int? = null,
    val targetAmount: Int? = null,
)

/**
 * 连接玩家背包与委托进度服务的协调器。
 *
 * 它会先读取仍需上交的数量，再在玩家实体线程精确扣除物品，并仅将数据库实际接受的数量保留；任何未接受
 * 的物品都会尝试按原始 ItemStack 返还。相同玩家同一委托同时只允许一个上交流程。
 */
object CommissionItemSubmissionCoordinator {

    private val activeSubmissions = ConcurrentHashMap.newKeySet<SubmissionKey>()

    /**
     * 为一个上交类委托提交玩家背包中可用的目标物品。
     *
     * @param player 当前在线玩家。
     * @param commissionId 今日委托稳定 ID。
     * @return 异步提交结果。
     */
    fun submit(player: Player, commissionId: String): CompletableFuture<CommissionItemSubmissionResult> {
        val key = SubmissionKey(player.uniqueId, commissionId)
        if (!activeSubmissions.add(key)) {
            return CompletableFuture.completedFuture(
                CommissionItemSubmissionResult(CommissionItemSubmissionStatus.BUSY)
            )
        }

        return CommissionService.prepareItemSubmission(player.uniqueId, commissionId)
            .thenCompose { preparation ->
                when (preparation) {
                    is CommissionItemSubmissionPreparation.Ready -> {
                        submitPrepared(player, key.playerId, preparation.request)
                    }
                    CommissionItemSubmissionPreparation.CommissionNotFound -> completed(
                        CommissionItemSubmissionStatus.COMMISSION_NOT_FOUND
                    )

                    CommissionItemSubmissionPreparation.NotItemSubmission -> completed(
                        CommissionItemSubmissionStatus.NOT_ITEM_SUBMISSION
                    )

                    CommissionItemSubmissionPreparation.AlreadyCompleted -> completed(
                        CommissionItemSubmissionStatus.ALREADY_COMPLETED
                    )
                }
            }
            .whenComplete { _, _ -> activeSubmissions.remove(key) }
    }

    private fun submitPrepared(
        player: Player,
        playerId: UUID,
        request: CommissionItemSubmissionRequest,
    ): CompletableFuture<CommissionItemSubmissionResult> {
        val inventory = FoliaCommissionItemInventoryPort(player, playerId)
        return inventory.take(request.definition, request.remainingAmount).thenCompose { taken ->
            when (taken) {
                CommissionInventoryTakeResult.NoMatchingItem -> completed(CommissionItemSubmissionStatus.NO_MATCHING_ITEM)
                CommissionInventoryTakeResult.PlayerUnavailable -> completed(CommissionItemSubmissionStatus.PLAYER_UNAVAILABLE)
                CommissionInventoryTakeResult.Failed -> completed(CommissionItemSubmissionStatus.STORAGE_FAILURE)
                is CommissionInventoryTakeResult.Taken -> commitTaken(playerId, request, inventory, taken)
            }
        }
    }

    private fun commitTaken(
        playerId: UUID,
        request: CommissionItemSubmissionRequest,
        inventory: FoliaCommissionItemInventoryPort,
        taken: CommissionInventoryTakeResult.Taken,
    ): CompletableFuture<CommissionItemSubmissionResult> {
        return CommissionService.commitItemSubmission(
            playerId = playerId,
            date = request.date,
            commissionId = request.definition.id,
            amount = taken.amount,
        ).handle { commit, error ->
            if (error != null || commit == null) {
                CommissionCommitAttempt.Failed
            } else {
                CommissionCommitAttempt.Committed(commit)
            }
        }.thenCompose { attempt ->
            if (attempt == CommissionCommitAttempt.Failed) {
                return@thenCompose restoreAndBuildResult(
                    playerId,
                    inventory,
                    taken,
                    taken.amount,
                    CommissionItemSubmissionResult(CommissionItemSubmissionStatus.STORAGE_FAILURE),
                )
            }

            val commit = (attempt as CommissionCommitAttempt.Committed).commit
            when (commit) {
                is CommissionItemSubmissionCommit.Applied -> {
                    val rejected = taken.amount - commit.mutation.acceptedAmount
                    restoreAndBuildResult(
                        playerId = playerId,
                        inventory = inventory,
                        taken = taken,
                        restoreAmount = rejected,
                        result = CommissionItemSubmissionResult(
                            status = if (commit.mutation.acceptedAmount > 0) {
                                CommissionItemSubmissionStatus.SUCCESS
                            } else {
                                CommissionItemSubmissionStatus.ALREADY_COMPLETED
                            },
                            submittedAmount = commit.mutation.acceptedAmount,
                            currentProgress = commit.mutation.currentProgress,
                            targetAmount = request.definition.targetAmount,
                        ),
                    )
                }

                CommissionItemSubmissionCommit.Expired -> restoreAndBuildResult(
                    playerId,
                    inventory,
                    taken,
                    taken.amount,
                    CommissionItemSubmissionResult(CommissionItemSubmissionStatus.EXPIRED),
                )

                CommissionItemSubmissionCommit.CommissionNotFound -> restoreAndBuildResult(
                    playerId,
                    inventory,
                    taken,
                    taken.amount,
                    CommissionItemSubmissionResult(CommissionItemSubmissionStatus.COMMISSION_NOT_FOUND),
                )

                CommissionItemSubmissionCommit.NotItemSubmission -> restoreAndBuildResult(
                    playerId,
                    inventory,
                    taken,
                    taken.amount,
                    CommissionItemSubmissionResult(CommissionItemSubmissionStatus.NOT_ITEM_SUBMISSION),
                )

                CommissionItemSubmissionCommit.StorageFailure -> restoreAndBuildResult(
                    playerId,
                    inventory,
                    taken,
                    taken.amount,
                    CommissionItemSubmissionResult(CommissionItemSubmissionStatus.STORAGE_FAILURE),
                )
            }
        }
    }

    private fun restoreAndBuildResult(
        playerId: UUID,
        inventory: FoliaCommissionItemInventoryPort,
        taken: CommissionInventoryTakeResult.Taken,
        restoreAmount: Int,
        result: CommissionItemSubmissionResult,
    ): CompletableFuture<CommissionItemSubmissionResult> {
        if (restoreAmount <= 0) {
            return CompletableFuture.completedFuture(result)
        }
        val restore = refundableItems(taken.items, restoreAmount)
        return CommissionItemReturnService.queueAndDeliver(inventory, taken.commissionId, restore)
            .handle { delivery, error ->
                when {
                    error != null || delivery == CommissionItemReturnDelivery.FAILED -> {
                        severe(
                            "委托上交后无法返还未接受物品，请人工核对。",
                            "玩家 UUID: $playerId",
                            "委托 ID: ${taken.commissionId}",
                            "应返还数量: $restoreAmount",
                            error?.stackTraceToString().orEmpty(),
                        )
                        CommissionItemSubmissionResult(CommissionItemSubmissionStatus.RESTORE_FAILURE)
                    }

                    delivery == CommissionItemReturnDelivery.QUEUED -> {
                        CommissionItemSubmissionResult(CommissionItemSubmissionStatus.RESTORE_QUEUED)
                    }

                    else -> result
                }
            }
    }

    private fun refundableItems(items: List<ItemStack>, amount: Int): List<ItemStack> {
        var remaining = amount
        return buildList {
            items.asReversed().forEach { source ->
                if (remaining <= 0) {
                    return@forEach
                }
                val recovered = source.clone()
                val accepted = minOf(recovered.amount, remaining)
                recovered.amount = accepted
                add(recovered)
                remaining -= accepted
            }
        }.also {
            check(remaining == 0) { "待返还的委托物品数量超出已扣除数量。" }
        }
    }

    private fun completed(status: CommissionItemSubmissionStatus): CompletableFuture<CommissionItemSubmissionResult> {
        return CompletableFuture.completedFuture(CommissionItemSubmissionResult(status))
    }

    private data class SubmissionKey(val playerId: UUID, val commissionId: String)
}

/** 从玩家背包扣除委托物品的结果。 */
internal sealed interface CommissionInventoryTakeResult {
    data class Taken(
        val commissionId: String,
        val amount: Int,
        val items: List<ItemStack>,
    ) : CommissionInventoryTakeResult

    data object NoMatchingItem : CommissionInventoryTakeResult
    data object PlayerUnavailable : CommissionInventoryTakeResult
    data object Failed : CommissionInventoryTakeResult
}

private sealed interface CommissionCommitAttempt {
    data class Committed(val commit: CommissionItemSubmissionCommit) : CommissionCommitAttempt
    data object Failed : CommissionCommitAttempt
}

/** 玩家背包返还尝试的结果。 */
internal sealed interface CommissionInventoryRestoreResult {
    /** 玩家已离线或实体调度器不可用，保留待返还记录。 */
    data object PlayerUnavailable : CommissionInventoryRestoreResult

    /** 背包 API 或物品反序列化失败，保留待返还记录。 */
    data object Failed : CommissionInventoryRestoreResult

    /** 已放入背包；[remainingPayload] 不为 null 时仍有无法容纳的物品。 */
    data class Restored(val remainingPayload: ByteArray?) : CommissionInventoryRestoreResult
}

/**
 * 在玩家所属 Folia 实体线程执行委托背包操作。
 *
 * TabooLib 没有能等价保留 PlayerInventory 的 storageContents 范围、原 ItemStack 组件和 addItem 剩余物品
 * 语义的接口，因此这里集中保留 Bukkit 背包访问；所有操作均通过 [Player.foliaRun] 限制在玩家实体线程。
 */
internal class FoliaCommissionItemInventoryPort(
    private val player: Player,
    /** 玩家 UUID 在创建端口前已由调用方采样，后续异步流程不再读取实体状态。 */
    val playerId: UUID,
) {

    fun take(
        definition: CommissionDefinition,
        maximum: Int,
    ): CompletableFuture<CommissionInventoryTakeResult> {
        if (maximum <= 0) {
            return CompletableFuture.completedFuture(CommissionInventoryTakeResult.NoMatchingItem)
        }
        return onPlayerThread(CommissionInventoryTakeResult.PlayerUnavailable) {
            if (!isOnline) {
                return@onPlayerThread CommissionInventoryTakeResult.PlayerUnavailable
            }
            runCatching {
                val material = resolveMaterial(definition.targetId)
                var remaining = maximum
                val removed = mutableListOf<ItemStack>()
                inventory.storageContents.indices.forEach { slot ->
                    if (remaining <= 0) {
                        return@forEach
                    }
                    val current = inventory.getItem(slot) ?: return@forEach
                    if (!material.isSimilar(current)) {
                        return@forEach
                    }

                    val amount = minOf(current.amount, remaining)
                    val extracted = current.clone().apply { this.amount = amount }
                    removed += extracted
                    if (amount == current.amount) {
                        inventory.setItem(slot, null)
                    } else {
                        current.amount -= amount
                        inventory.setItem(slot, current)
                    }
                    remaining -= amount
                }
                val total = maximum - remaining
                if (total == 0) {
                    CommissionInventoryTakeResult.NoMatchingItem
                } else {
                    CommissionInventoryTakeResult.Taken(definition.id, total, removed)
                }
            }.getOrElse { CommissionInventoryTakeResult.Failed }
        }
    }

    /**
     * 将已经从背包扣除的物品副本放回原玩家背包。
     *
     * 背包空间不足时不掉落到世界，而是返回保真的剩余 NBT 数据供持久化队列在下次登录后重试。
     */
    fun restoreItems(items: List<ItemStack>): CompletableFuture<CommissionInventoryRestoreResult> {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(CommissionInventoryRestoreResult.Restored(null))
        }
        val snapshots = items.map(ItemStack::clone)
        return onPlayerThread(CommissionInventoryRestoreResult.PlayerUnavailable) {
            if (!isOnline) {
                return@onPlayerThread CommissionInventoryRestoreResult.PlayerUnavailable
            }
            runCatching {
                val leftovers = inventory.addItem(*snapshots.toTypedArray())
                CommissionInventoryRestoreResult.Restored(
                    leftovers.values.takeIf { it.isNotEmpty() }?.let(ItemStack::serializeItemsAsBytes)
                )
            }.getOrDefault(CommissionInventoryRestoreResult.Failed)
        }
    }

    /**
     * 反序列化待返还 NBT 数据并尝试放回玩家背包。
     *
     * Paper ItemStack NBT 反序列化和 Bukkit 背包写入都集中在玩家实体线程执行，以满足 Folia 线程约束。
     */
    fun restorePayload(payload: ByteArray): CompletableFuture<CommissionInventoryRestoreResult> {
        return onPlayerThread(CommissionInventoryRestoreResult.PlayerUnavailable) {
            if (!isOnline) {
                return@onPlayerThread CommissionInventoryRestoreResult.PlayerUnavailable
            }
            runCatching {
                val items = ItemStack.deserializeItemsFromBytes(payload)
                val leftovers = inventory.addItem(*items)
                CommissionInventoryRestoreResult.Restored(
                    leftovers.values.takeIf { it.isNotEmpty() }?.let(ItemStack::serializeItemsAsBytes)
                )
            }.getOrDefault(CommissionInventoryRestoreResult.Failed)
        }
    }

    private fun resolveMaterial(targetId: String): XMaterial {
        val name = targetId.substringAfter(':').uppercase(Locale.ROOT)
        return XMaterial.matchXMaterial(name)
            .orElseThrow { IllegalArgumentException("无法解析委托物品: $targetId") }
    }

    private fun <T> onPlayerThread(
        fallback: T,
        block: Player.() -> T,
    ): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        player.foliaRun {
            runCatching { block() }
                .onSuccess(result::complete)
                .onFailure(result::completeExceptionally)
        }.whenComplete { scheduled, error ->
            if (error != null) {
                result.completeExceptionally(error)
            } else if (scheduled != true) {
                result.complete(fallback)
            }
        }
        return result
    }
}
