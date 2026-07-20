package cn.tj.dzd.mc.dzt.shop.ui

import cn.tj.dzd.mc.dzt.economy.ServiceEconomy
import cn.tj.dzd.mc.dzt.shop.ShopCatalog
import cn.tj.dzd.mc.dzt.shop.ShopCatalogs
import cn.tj.dzd.mc.dzt.shop.ShopCategory
import cn.tj.dzd.mc.dzt.shop.ShopCheckoutCoordinator
import cn.tj.dzd.mc.dzt.shop.ShopProduct
import cn.tj.dzd.mc.dzt.shop.ShopService
import cn.tj.dzd.mc.dzt.ui.MainMenuNavigation
import cn.tj.dzd.mc.dzt.ui.PaperDialogUI
import cn.tj.dzd.mc.dzt.util.foliaCloseInventory
import cn.tj.dzd.mc.dzt.util.foliaRun
import cn.tj.dzd.mc.dzt.util.isBePlayer
import cn.tj.dzd.mc.dzt.util.runForOnlinePlayer
import cn.tj.dzd.mc.dzt.util.sendDZTError
import cn.tj.dzd.mc.dzt.util.sendDZTSuccess
import cn.tj.dzd.mc.dzt.util.sendDZTTip
import cn.tj.dzd.mc.dzt.util.sendForm
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.library.xseries.XMaterial
import taboolib.module.ui.openMenu
import taboolib.module.ui.type.Chest
import taboolib.module.ui.type.PageableChest
import taboolib.platform.util.buildItem
import java.math.BigDecimal
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Java 版与基岩版共用的资源商店界面。
 *
 * 目录和购买结算均由商店领域层管理；本对象只负责按客户端类型展示分类、商品、数量输入和确认界面。
 */
object ShopUI {

    private const val CATEGORY_TITLE = "§l§6资源商店"
    private const val QUANTITY_INPUT_KEY = "shop_quantity"

    private const val BACK_ICON = "textures/ui/box_ride.png"
    private const val CANCEL_ICON = "textures/ui/cancel.png"
    private const val CONFIRM_ICON = "textures/items/emerald.png"

    /**
     * 打开当前商店目录的分类选择页。
     *
     * @param player 要打开商店的玩家。
     */
    fun open(player: Player) {
        player.foliaRun {
            val catalog = runCatching { ShopCatalogs.catalog }.getOrElse { error ->
                sendDZTError("商店目录加载失败：${error.message ?: error.javaClass.simpleName}")
                return@foliaRun
            }

            if (isBePlayer()) {
                openBedrockCategories(this, catalog)
            } else {
                openJavaCategories(this, catalog)
            }
        }
    }

    private fun openJavaCategories(player: Player, catalog: ShopCatalog) {
        player.openMenu<PageableChest<ShopCategory>>(CATEGORY_TITLE) {
            rows(6)
            virtualize()

            map(
                "R###M####",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<#>###"
            )

            onClick(lock = true) {}
            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set('M', XMaterial.CHEST) {
                name = CATEGORY_TITLE
                lore += "§7请选择要购买的资源分类"
            }
            set('R', buildItem(XMaterial.BARREL) {
                name = "§l§e返回主菜单"
            }) {
                MainMenuNavigation.open(player)
            }

            slotsBy('@')
            elements { catalog.categories }
            onGenerate { _, category, _, _ ->
                category.javaItem(
                    displayName = "§6${category.displayName}",
                    loreLines = listOf(
                        "§7${category.products.size} 种商品",
                        "§e点击查看商品",
                    ),
                )
            }
            onClick { _, category ->
                player.foliaCloseInventory().whenComplete { _, _ ->
                    openProducts(player, category)
                }
            }

            setPreviousPage(48) { _, hasPrevious ->
                buildItem(if (hasPrevious) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasPrevious) "§a上一页" else "§7已经是第一页"
                }
            }
            setNextPage(50) { _, hasNext ->
                buildItem(if (hasNext) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasNext) "§a下一页" else "§7已经是最后一页"
                }
            }
        }
    }

    private fun openBedrockCategories(player: Player, catalog: ShopCatalog) {
        val form = SimpleForm.builder()
            .title(CATEGORY_TITLE)
            .content("请选择要购买的资源分类。")
            .button("返回主菜单", FormImage.Type.PATH, BACK_ICON)

        catalog.categories.forEach { category ->
            form.button(
                "${category.displayName}\n§7${category.products.size} 种商品",
                FormImage.Type.PATH,
                category.bedrockIcon,
            )
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            player.foliaRun {
                if (clicked == 0) {
                    MainMenuNavigation.open(this)
                    return@foliaRun
                }

                val category = catalog.categories.getOrNull(clicked - 1) ?: return@foliaRun
                openBedrockProducts(this, category)
            }
        }
        player.sendForm(form)
    }

    private fun openProducts(player: Player, category: ShopCategory) {
        player.foliaRun {
            if (isBePlayer()) {
                openBedrockProducts(this, category)
            } else {
                openJavaProducts(this, category)
            }
        }
    }

    private fun openJavaProducts(player: Player, category: ShopCategory) {
        val title = "§l§6${category.displayName}"
        player.openMenu<PageableChest<ShopProduct>>(title) {
            rows(6)
            virtualize()

            map(
                "R###M####",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "#@@@@@@@#",
                "###<#>###"
            )

            onClick(lock = true) {}
            set('#', XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " }
            set(
                'M',
                category.javaItem(
                    displayName = "§l§6${category.displayName}",
                    loreLines = listOf("§7选择商品后可输入购买数量"),
                ),
            )
            set('R', buildItem(XMaterial.BARREL) {
                name = "§l§e返回分类"
            }) {
                open(player)
            }

            slotsBy('@')
            elements { category.products }
            onGenerate { _, product, _, _ ->
                product.javaItem(
                    displayName = "§f${product.displayName}",
                    loreLines = listOf(
                        "§7单价: §6${formatAmount(product.price)} DDB/个",
                        "§7每日限购: §e${product.dailyLimit} 个",
                        "§e点击购买",
                    ),
                )
            }
            onClick { _, product ->
                player.foliaCloseInventory().whenComplete { _, _ ->
                    selectProduct(player, category, product)
                }
            }

            setPreviousPage(48) { _, hasPrevious ->
                buildItem(if (hasPrevious) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasPrevious) "§a上一页" else "§7已经是第一页"
                }
            }
            setNextPage(50) { _, hasNext ->
                buildItem(if (hasNext) XMaterial.ARROW else XMaterial.GRAY_STAINED_GLASS_PANE) {
                    name = if (hasNext) "§a下一页" else "§7已经是最后一页"
                }
            }
        }
    }

    private fun openBedrockProducts(player: Player, category: ShopCategory) {
        val form = SimpleForm.builder()
            .title("§l§6${category.displayName}")
            .content("请选择要购买的商品。")
            .button("返回分类", FormImage.Type.PATH, BACK_ICON)

        category.products.forEach { product ->
            form.button(
                "${product.displayName}\n§6${formatAmount(product.price)} DDB/个 §7| 每日 ${product.dailyLimit} 个",
                FormImage.Type.PATH,
                product.bedrockIcon,
            )
        }

        form.validResultHandler { response ->
            val clicked = response.clickedButtonId()
            player.foliaRun {
                if (clicked == 0) {
                    open(this)
                    return@foliaRun
                }

                val product = category.products.getOrNull(clicked - 1) ?: return@foliaRun
                selectProduct(this, category, product)
            }
        }
        player.sendForm(form)
    }

    private fun selectProduct(player: Player, category: ShopCategory, product: ShopProduct) {
        val playerId = player.uniqueId
        player.sendDZTTip("正在读取 ${product.displayName} 的剩余限购额度……")
        ShopService.availability(playerId, product.id).whenComplete { availability, error ->
            runForOnlinePlayer(playerId) {
                if (error != null || availability == null) {
                    sendDZTError("读取商品限购额度失败。")
                    openProducts(this, category)
                    return@runForOnlinePlayer
                }

                if (!availability.available) {
                    sendDZTError(availability.message)
                    openProducts(this, category)
                    return@runForOnlinePlayer
                }
                val remaining = availability.remaining

                if (isBePlayer()) {
                    openBedrockQuantity(this, category, product, remaining)
                } else {
                    openJavaQuantity(this, category, product, remaining)
                }
            }
        }
    }

    private fun openJavaQuantity(
        player: Player,
        category: ShopCategory,
        product: ShopProduct,
        remaining: Int,
    ) {
        PaperDialogUI.open(player, "§l§6购买 ${product.displayName}") {
            closeAfterAction()
            body {
                plainMessage(
                    "单价: ${formatAmount(product.price)} DDB/个\n" +
                        "今日剩余可购买: $remaining 个"
                )
            }
            inputs {
                numberRange(
                    key = QUANTITY_INPUT_KEY,
                    label = "购买数量",
                    start = 1f,
                    end = remaining.toFloat(),
                    initial = 1f,
                    step = 1f,
                )
            }
            notice("下一步") {
                callback { audience ->
                    val quantity = float(QUANTITY_INPUT_KEY)?.roundToInt()
                    val selectedPlayer = audience as? Player
                    if (selectedPlayer == null || quantity == null || quantity !in 1..remaining) {
                        selectedPlayer?.sendDZTError("购买数量无效。")
                        return@callback
                    }
                    selectedPlayer.foliaRun {
                        openJavaConfirmation(this, category, product, quantity, remaining)
                    }
                }
            }
        }
    }

    private fun openBedrockQuantity(
        player: Player,
        category: ShopCategory,
        product: ShopProduct,
        remaining: Int,
    ) {
        player.sendForm(
            CustomForm.builder()
                .title("§l§6购买 ${product.displayName}")
                .iconPath(product.bedrockIcon)
                .slider(
                    "${product.displayName}\n" +
                        "§7单价: §6${formatAmount(product.price)} DDB/个\n" +
                        "§7今日剩余可购买: §e$remaining 个\n" +
                        "§f购买数量",
                    1f,
                    remaining.toFloat(),
                    1f,
                    1f,
                )
                .validResultHandler { response ->
                    val quantity = response.asSlider(0).roundToInt()
                    player.foliaRun {
                        if (quantity !in 1..remaining) {
                            sendDZTError("购买数量无效。")
                            openBedrockQuantity(this, category, product, remaining)
                            return@foliaRun
                        }
                        openBedrockConfirmation(this, category, product, quantity, remaining)
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    player.foliaRun {
                        openBedrockProducts(this, category)
                    }
                })
        )
    }

    private fun openJavaConfirmation(
        player: Player,
        category: ShopCategory,
        product: ShopProduct,
        quantity: Int,
        remaining: Int,
    ) {
        val total = totalPrice(product, quantity)
        var handled = false
        player.openMenu<Chest>("§l§6确认购买") {
            rows(3)
            virtualize()

            map(
                "####M####",
                "#  Y N  #",
                "#########"
            )

            onClick(lock = true) {}
            set('#', buildItem(XMaterial.GRAY_STAINED_GLASS_PANE) { name = " " })
            set(
                'M',
                product.javaItem(
                    displayName = "§f${product.displayName} §7x$quantity",
                    loreLines = listOf(
                        "§7单价: §6${formatAmount(product.price)} DDB/个",
                        "§7本次合计: §6${formatAmount(total)} DDB",
                        "§7购买后今日剩余: §e${(remaining - quantity).coerceAtLeast(0)} 个",
                    ),
                ),
            )
            set('Y', buildItem(XMaterial.EMERALD_BLOCK) {
                name = "§a确认购买"
                lore += "§7扣除 ${formatAmount(total)} DDB"
            }) {
                if (handled) {
                    return@set
                }
                handled = true
                player.foliaCloseInventory().whenComplete { _, _ ->
                    checkout(player, category, product, quantity)
                }
            }
            set('N', buildItem(XMaterial.REDSTONE_BLOCK) {
                name = "§c取消"
                lore += "§7返回数量选择"
            }) {
                if (handled) {
                    return@set
                }
                handled = true
                player.foliaCloseInventory().whenComplete { _, _ ->
                    selectProduct(player, category, product)
                }
            }
        }
    }

    private fun openBedrockConfirmation(
        player: Player,
        category: ShopCategory,
        product: ShopProduct,
        quantity: Int,
        remaining: Int,
    ) {
        val total = totalPrice(product, quantity)
        player.sendForm(
            SimpleForm.builder()
                .title("§l§6确认购买")
                .content(
                    "§f商品: §e${product.displayName}\n" +
                        "§f数量: §e$quantity\n" +
                        "§f单价: §6${formatAmount(product.price)} DDB/个\n" +
                        "§f本次合计: §6${formatAmount(total)} DDB\n" +
                        "§f购买后今日剩余: §e${(remaining - quantity).coerceAtLeast(0)} 个"
                )
                .button("确认购买", FormImage.Type.PATH, CONFIRM_ICON)
                .button("取消", FormImage.Type.PATH, CANCEL_ICON)
                .validResultHandler { response ->
                    player.foliaRun {
                        if (response.clickedButtonId() == 0) {
                            checkout(this, category, product, quantity)
                        } else {
                            openBedrockQuantity(this, category, product, remaining)
                        }
                    }
                }
                .closedOrInvalidResultHandler(Runnable {
                    player.foliaRun {
                        openBedrockProducts(this, category)
                    }
                })
        )
    }

    private fun checkout(player: Player, category: ShopCategory, product: ShopProduct, quantity: Int) {
        val playerId = player.uniqueId
        player.sendDZTTip("正在处理 ${product.displayName} x$quantity 的购买……")
        ShopCheckoutCoordinator.purchase(player, product.id, quantity).whenComplete { result, error ->
            runForOnlinePlayer(playerId) {
                if (error != null || result == null) {
                    sendDZTError("购买失败，商店服务发生异常。")
                    openProducts(this, category)
                    return@runForOnlinePlayer
                }

                if (result.successful) {
                    sendDZTSuccess(result.message)
                } else {
                    sendDZTError(result.message)
                }
                openProducts(this, category)
            }
        }
    }

    private fun ShopCategory.javaItem(displayName: String, loreLines: List<String>): ItemStack {
        return styledItem(javaIcon, displayName, loreLines)
    }

    private fun ShopProduct.javaItem(displayName: String, loreLines: List<String>): ItemStack {
        return styledItem(materialId, displayName, loreLines)
    }

    private fun styledItem(materialId: String, displayName: String, loreLines: List<String>): ItemStack {
        return buildItem(resolveXMaterial(materialId)) {
            name = displayName
            loreLines.forEach { line -> lore += line }
        }
    }

    private fun resolveXMaterial(materialId: String): XMaterial {
        val materialName = materialId
            .substringAfter(':', materialId)
            .uppercase(Locale.ROOT)
        return XMaterial.matchXMaterial(materialName)
            .orElse(XMaterial.BARRIER)
    }

    private fun totalPrice(product: ShopProduct, quantity: Int): BigDecimal {
        return product.price.multiply(BigDecimal.valueOf(quantity.toLong()))
    }

    private fun formatAmount(amount: BigDecimal): String {
        return ServiceEconomy.formatAmount(amount)
    }
}
