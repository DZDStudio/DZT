package cn.tj.dzd.mc.dzt.shop

import cn.tj.dzd.mc.dzt.util.foliaRun
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.severe
import taboolib.library.xseries.XMaterial
import java.util.Locale
import java.util.concurrent.CompletableFuture

/**
 * 连接跨端商店 UI 和运行时结算服务的协调器。
 *
 * 该入口将背包操作调度到当前玩家的 Folia 实体线程，UI 不得直接扣款、写入限购记录或发放物品。
 */
object ShopCheckoutCoordinator {

    /**
     * 为指定商品发起购买。
     *
     * @param player 当前在线玩家。
     * @param productId 商品稳定 ID。
     * @param quantity 要购买的物品数量。
     * @return 异步最终结算结果。
     */
    fun purchase(
        player: Player,
        productId: String,
        quantity: Int,
    ): CompletableFuture<ShopCheckoutResult> {
        return ShopService.purchase(
            playerId = player.uniqueId,
            productId = productId,
            quantity = quantity,
            inventory = FoliaShopInventoryPort(player),
        ).thenApply { result ->
            if (result.status == ShopCheckoutStatus.REFUND_FAILED || result.status == ShopCheckoutStatus.COMPENSATION_FAILED) {
                severe(
                    "商店购买补偿失败，请人工核对 DDB 与限购记录。",
                    "玩家 UUID: ${player.uniqueId}",
                    "商品 ID: $productId",
                    "数量: $quantity",
                    "结果: ${result.status}",
                )
            }
            result
        }
    }

    /**
     * 为商品对象发起购买。
     *
     * @param player 当前在线玩家。
     * @param product 已从当前目录解析出的商品。
     * @param quantity 要购买的物品数量。
     * @return 异步最终结算结果。
     */
    fun purchase(
        player: Player,
        product: ShopProduct,
        quantity: Int,
    ): CompletableFuture<ShopCheckoutResult> {
        return purchase(player, product.id, quantity)
    }
}

/**
 * 在指定玩家实体线程执行商店背包检查和发货。
 *
 * TabooLib 没有能同时复现 PlayerInventory 空槽、同类堆叠上限和 [org.bukkit.inventory.Inventory.addItem]
 * 剩余物品语义的可靠替代，因此此处保留原生背包访问，并将全部调用限制在 Folia 实体线程。
 */
private class FoliaShopInventoryPort(private val player: Player) : ShopInventoryPort {

    override fun checkCapacity(product: ShopProduct, quantity: Int): CompletableFuture<ShopInventoryOperation> {
        return onPlayerThread {
            if (!isOnline) {
                ShopInventoryOperation(ShopInventoryStatus.PLAYER_UNAVAILABLE)
            } else {
                runCatching {
                    if (canFit(product, quantity)) {
                        ShopInventoryOperation(ShopInventoryStatus.SUCCESS)
                    } else {
                        ShopInventoryOperation(ShopInventoryStatus.INVENTORY_FULL)
                    }
                }.getOrElse {
                    ShopInventoryOperation(ShopInventoryStatus.FAILED)
                }
            }
        }
    }

    override fun deliver(product: ShopProduct, quantity: Int): CompletableFuture<ShopInventoryOperation> {
        return onPlayerThread {
            if (!isOnline) {
                return@onPlayerThread ShopInventoryOperation(ShopInventoryStatus.PLAYER_UNAVAILABLE)
            }
            runCatching {
                // Must be rechecked after the asynchronous DDB withdrawal.
                if (!canFit(product, quantity)) {
                    return@runCatching ShopInventoryOperation(ShopInventoryStatus.INVENTORY_FULL)
                }
                val leftovers = inventory.addItem(*stacks(product, quantity).toTypedArray())
                if (leftovers.isEmpty()) {
                    ShopInventoryOperation(ShopInventoryStatus.SUCCESS)
                } else {
                    ShopInventoryOperation(ShopInventoryStatus.FAILED)
                }
            }.getOrElse {
                ShopInventoryOperation(ShopInventoryStatus.FAILED)
            }
        }
    }

    private fun Player.canFit(product: ShopProduct, quantity: Int): Boolean {
        if (quantity <= 0) return false
        val prototype = createItem(product, 1)
        val maximum = prototype.maxStackSize.coerceAtLeast(1)
        var capacity = 0L
        inventory.storageContents.forEach { current ->
            when {
                current == null || current.type.isAir -> capacity += maximum.toLong()
                current.isSimilar(prototype) -> {
                    capacity += (current.maxStackSize - current.amount).coerceAtLeast(0).toLong()
                }
            }
        }
        return capacity >= quantity.toLong()
    }

    private fun stacks(product: ShopProduct, quantity: Int): List<ItemStack> {
        require(quantity > 0) { "商品数量必须大于 0。" }
        val maximum = createItem(product, 1).maxStackSize.coerceAtLeast(1)
        var remaining = quantity
        return buildList {
            while (remaining > 0) {
                val amount = minOf(remaining, maximum)
                add(createItem(product, amount))
                remaining -= amount
            }
        }
    }

    private fun createItem(product: ShopProduct, amount: Int): ItemStack {
        val materialName = product.materialId.substringAfter(':').uppercase(Locale.ROOT)
        val material = XMaterial.matchXMaterial(materialName)
            .orElseThrow { IllegalArgumentException("无法解析商店物品: ${product.materialId}") }
        return requireNotNull(material.parseItem()) { "无法创建商店物品: ${product.materialId}" }.apply {
            this.amount = amount
        }
    }

    private fun onPlayerThread(block: Player.() -> ShopInventoryOperation): CompletableFuture<ShopInventoryOperation> {
        val result = CompletableFuture<ShopInventoryOperation>()
        player.foliaRun {
            result.complete(block())
        }.whenComplete { scheduled, error ->
            if (error != null) {
                result.completeExceptionally(error)
            } else if (scheduled != true) {
                result.complete(ShopInventoryOperation(ShopInventoryStatus.PLAYER_UNAVAILABLE))
            }
        }
        return result
    }
}
