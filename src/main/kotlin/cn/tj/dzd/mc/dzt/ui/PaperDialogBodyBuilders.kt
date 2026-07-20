@file:Suppress("unused")

package cn.tj.dzd.mc.dzt.ui

import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody
import io.papermc.paper.registry.data.dialog.body.PlainMessageDialogBody
import io.papermc.paper.registry.data.dialog.input.BooleanDialogInput
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.input.TextDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import io.papermc.paper.registry.set.RegistrySet
import java.net.URL
import java.time.temporal.TemporalAmount
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.key.Key
import net.kyori.adventure.nbt.api.BinaryTagHolder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 对话框内容元素构建器。
 */
@PaperDialogDsl
class PaperDialogBodyBuilder internal constructor() {
    private val body = mutableListOf<DialogBody>()

    /**
     * 添加 Paper 原始内容元素。
     *
     * @param value Paper 内容元素。
     */
    fun raw(value: DialogBody) {
        body += value
    }

    /**
     * 添加纯文本内容消息。
     *
     * @param contents 消息文本。
     * @param width 可选消息宽度；为 null 时保留 Paper 默认值。
     */
    fun plainMessage(contents: String, width: Int? = null) {
        plainMessage(paperDialogTextComponent(contents), width)
    }

    /**
     * 添加纯组件内容消息。
     *
     * @param contents 消息组件。
     * @param width 可选消息宽度；为 null 时保留 Paper 默认值。
     */
    fun plainMessage(contents: ComponentLike, width: Int? = null) {
        body += if (width == null) {
            DialogBody.plainMessage(contents.asComponent())
        } else {
            DialogBody.plainMessage(contents.asComponent(), width)
        }
    }

    /**
     * [plainMessage] 的别名。
     *
     * @param contents 消息文本。
     * @param width 可选消息宽度；为 null 时保留 Paper 默认值。
     */
    fun plain(contents: String, width: Int? = null) {
        plainMessage(contents, width)
    }

    /**
     * [plainMessage] 的别名。
     *
     * @param contents 消息组件。
     * @param width 可选消息宽度；为 null 时保留 Paper 默认值。
     */
    fun plain(contents: ComponentLike, width: Int? = null) {
        plainMessage(contents, width)
    }

    /**
     * 添加物品预览内容。
     *
     * @param item 显示在对话框中的物品堆；构建时会克隆该物品。
     * @param block 用于配置描述、悬浮提示可见性、物品装饰和尺寸的 DSL。
     */
    fun item(item: ItemStack, block: PaperItemDialogBodyBuilder.() -> Unit = {}) {
        body += PaperItemDialogBodyBuilder(item).apply(block).build()
    }

    internal fun build(): List<DialogBody> {
        return body.toList()
    }
}

/**
 * 物品内容选项构建器。
 *
 * @param item 显示在对话框中的物品堆。
 */
@PaperDialogDsl
class PaperItemDialogBodyBuilder internal constructor(
    private val item: ItemStack
) {
    private var description: PlainMessageDialogBody? = null
    private var showDecorations: Boolean? = null
    private var showTooltip: Boolean? = null
    private var width: Int? = null
    private var height: Int? = null

    /**
     * 设置物品下方的 Paper 原始纯消息描述。
     *
     * @param value 描述内容；为 null 时省略描述。
     */
    fun description(value: PlainMessageDialogBody?) {
        description = value
    }

    /**
     * 设置物品下方的文本描述。
     *
     * @param contents 描述文本。
     * @param width 可选描述宽度；为 null 时保留 Paper 默认值。
     */
    fun description(contents: String, width: Int? = null) {
        description(paperDialogTextComponent(contents), width)
    }

    /**
     * 设置物品下方的组件描述。
     *
     * @param contents 描述组件。
     * @param width 可选描述宽度；为 null 时保留 Paper 默认值。
     */
    fun description(contents: ComponentLike, width: Int? = null) {
        description = if (width == null) {
            DialogBody.plainMessage(contents.asComponent())
        } else {
            DialogBody.plainMessage(contents.asComponent(), width)
        }
    }

    /**
     * 控制是否显示物品数量、耐久等装饰信息。
     *
     * @param value 为 true 时显示物品装饰信息。
     */
    fun showDecorations(value: Boolean = true) {
        showDecorations = value
    }

    fun hideDecorations() {
        showDecorations(false)
    }

    /**
     * 控制鼠标悬浮物品时是否显示提示。
     *
     * @param value 为 true 时显示物品提示。
     */
    fun showTooltip(value: Boolean = true) {
        showTooltip = value
    }

    fun hideTooltip() {
        showTooltip(false)
    }

    /**
     * 设置物品内容宽度。
     *
     * @param value 对话框像素单位的宽度。
     */
    fun width(value: Int) {
        width = value
    }

    /**
     * 设置物品内容高度。
     *
     * @param value 对话框像素单位的高度。
     */
    fun height(value: Int) {
        height = value
    }

    /**
     * 同时设置物品内容宽度和高度。
     *
     * @param width 对话框像素单位的宽度。
     * @param height 对话框像素单位的高度，默认等于 [width]。
     */
    fun size(width: Int, height: Int = width) {
        width(width)
        height(height)
    }

    internal fun build(): ItemDialogBody {
        val builder = DialogBody.item(item.clone())
        description?.let { builder.description(it) }
        showDecorations?.let { builder.showDecorations(it) }
        showTooltip?.let { builder.showTooltip(it) }
        width?.let { builder.width(it) }
        height?.let { builder.height(it) }
        return builder.build()
    }
}

