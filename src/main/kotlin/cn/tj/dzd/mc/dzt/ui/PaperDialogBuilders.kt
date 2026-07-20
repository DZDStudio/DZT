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
 * 动态 Paper 对话框的根构建器。
 *
 * @param title 显示在对话框顶部的标题。
 */
@PaperDialogDsl
class PaperDialogBuilder internal constructor(
    private val title: Component
) {
    private var externalTitle: Component? = null
    private var canCloseWithEscape: Boolean? = null
    private var pause: Boolean? = null
    private var afterAction: DialogBase.DialogAfterAction? = null
    private val body = mutableListOf<DialogBody>()
    private val inputs = mutableListOf<DialogInput>()
    private var typeFactory: (PaperDialogBuildContext.() -> DialogType)? = null

    /**
     * 设置其它对话框通过按钮打开此对话框时使用的外部标题。
     *
     * @param title 外部标题文本；需要富文本时使用 Component 重载。
     */
    fun externalTitle(title: String) {
        externalTitle(paperDialogTextComponent(title))
    }

    /**
     * 设置其它对话框通过按钮打开此对话框时使用的外部标题。
     *
     * @param title 外部标题组件；传入 null 时保留 Paper 默认行为。
     */
    fun externalTitle(title: ComponentLike?) {
        externalTitle = title?.asComponent()
    }

    /**
     * 控制玩家是否可以使用 Escape 关闭对话框。
     *
     * @param value 为 true 时允许使用 Escape 关闭。
     */
    fun canCloseWithEscape(value: Boolean = true) {
        canCloseWithEscape = value
    }

    /**
     * [canCloseWithEscape] 的别名。
     *
     * @param value 为 true 时允许使用 Escape 关闭。
     */
    fun escapeClose(value: Boolean = true) {
        canCloseWithEscape(value)
    }

    /**
     * 控制该对话框是否暂停单人游戏。
     *
     * @param value 为 true 时请求暂停行为。
     */
    fun pause(value: Boolean = true) {
        pause = value
    }

    /**
     * 设置玩家点击动作后客户端应执行的后续行为。
     *
     * @param action Paper 的 after-action 模式。
     */
    fun afterAction(action: DialogBase.DialogAfterAction) {
        afterAction = action
    }

    fun closeAfterAction() {
        afterAction(DialogBase.DialogAfterAction.CLOSE)
    }

    fun noAfterAction() {
        afterAction(DialogBase.DialogAfterAction.NONE)
    }

    fun waitForResponseAfterAction() {
        afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
    }

    /**
     * 添加一个或多个对话框内容元素。
     *
     * @param block 用于添加消息、物品预览等内容的 DSL。
     */
    fun body(block: PaperDialogBodyBuilder.() -> Unit) {
        body += PaperDialogBodyBuilder().apply(block).build()
    }

    /**
     * 添加一个 Paper 原始内容元素。
     *
     * @param value Paper 内容对象。
     */
    fun body(value: DialogBody) {
        body += value
    }

    /**
     * 添加一个 Paper 原始输入项。
     *
     * @param value Paper 输入对象。
     */
    fun input(value: DialogInput) {
        inputs += value
    }

    /**
     * 添加一个或多个输入控件。
     *
     * @param block 用于添加文本、布尔、数值范围和单选输入的 DSL。
     */
    fun inputs(block: PaperDialogInputBuilder.() -> Unit) {
        inputs += PaperDialogInputBuilder().apply(block).build()
    }

    /**
     * 直接设置对话框类型。
     *
     * @param type Paper 原始对话框类型。
     */
    fun type(type: DialogType) {
        typeFactory = { type }
    }

    /**
     * 使用类型 DSL 设置对话框类型。
     *
     * @param block 用于配置 notice、confirmation、multi-action、dialog-list 或 server-links 的类型 DSL。
     */
    fun type(block: PaperDialogTypeBuilder.() -> Unit) {
        val builder = PaperDialogTypeBuilder().apply(block)
        typeFactory = { builder.build(this) }
    }

    /**
     * 使用带 Paper 默认动作按钮的 notice 对话框。
     */
    fun notice() {
        type(DialogType.notice())
    }

    /**
     * 使用带一个动作按钮的 notice 对话框。
     *
     * @param action 显示在 notice 底部的按钮。
     */
    fun notice(action: ActionButton) {
        type(DialogType.notice(action))
    }

    /**
     * 使用带一个动作按钮的 notice 对话框。
     *
     * @param label 按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun notice(label: String, block: PaperActionButtonBuilder.() -> Unit = {}) {
        notice(dialogButton(label, block))
    }

    /**
     * 使用带一个动作按钮的 notice 对话框。
     *
     * @param label 按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun notice(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        notice(dialogButton(label, block))
    }

    /**
     * 使用确认对话框。
     *
     * @param yesButton 用于确认操作的按钮。
     * @param noButton 用于拒绝或取消操作的按钮。
     */
    fun confirmation(
        yesButton: ActionButton,
        noButton: ActionButton
    ) {
        type(DialogType.confirmation(yesButton, noButton))
    }

    /**
     * 使用带两个内联按钮的确认对话框。
     *
     * @param yesLabel 确认按钮标签。
     * @param noLabel 拒绝按钮标签。
     * @param yes 确认按钮的 DSL。
     * @param no 拒绝按钮的 DSL。
     */
    fun confirmation(
        yesLabel: String = "Yes",
        noLabel: String = "No",
        yes: PaperActionButtonBuilder.() -> Unit = {},
        no: PaperActionButtonBuilder.() -> Unit = {}
    ) {
        confirmation(dialogButton(yesLabel, yes), dialogButton(noLabel, no))
    }

    /**
     * 使用由 yes/no 专用 DSL 配置的确认对话框。
     *
     * @param block 用于配置 yes 和 no 按钮的 DSL。
     */
    fun confirmation(block: PaperConfirmationBuilder.() -> Unit) {
        val builder = PaperConfirmationBuilder().apply(block)
        confirmation(builder.buildYesButton(), builder.buildNoButton())
    }

    /**
     * 使用带多个动作按钮的对话框。
     *
     * @param columns 按钮列数。
     * @param block 用于添加动作按钮和可选退出按钮的 DSL。
     */
    fun multiAction(
        columns: Int = 1,
        block: PaperMultiActionBuilder.() -> Unit
    ) {
        val builder = PaperMultiActionBuilder(columns).apply(block)
        typeFactory = { builder.build() }
    }

    /**
     * 使用展示其它对话框列表的对话框。
     *
     * @param columns 按钮列数。
     * @param buttonWidth 每个对话框列表按钮的宽度。
     * @param block 用于提供内联对话框或注册表对话框的 DSL。
     */
    fun dialogList(
        columns: Int = 1,
        buttonWidth: Int = 150,
        block: PaperDialogListBuilder.() -> Unit
    ) {
        val builder = PaperDialogListBuilder(columns, buttonWidth).apply(block)
        typeFactory = { builder.build(this) }
    }

    /**
     * 使用客户端 server-links 对话框。
     *
     * @param columns 链接列数。
     * @param buttonWidth 每个链接按钮的宽度。
     * @param exitAction 用于退出链接对话框的可选按钮。
     */
    fun serverLinks(
        columns: Int = 1,
        buttonWidth: Int = 150,
        exitAction: ActionButton? = null
    ) {
        type(DialogType.serverLinks(exitAction, columns, buttonWidth))
    }

    /**
     * 使用客户端 server-links 对话框。
     *
     * @param block 用于配置列数、按钮宽度和退出动作的 DSL。
     */
    fun serverLinks(block: PaperServerLinksBuilder.() -> Unit) {
        val builder = PaperServerLinksBuilder().apply(block)
        typeFactory = { builder.build() }
    }

    internal fun buildInto(builder: DialogRegistryEntry.Builder) {
        builder.base(buildBase())
        builder.type(typeFactory?.invoke(PaperDialogBuildContext(builder)) ?: DialogType.notice())
    }

    private fun buildBase(): DialogBase {
        val builder = DialogBase.builder(title)
        externalTitle?.let { builder.externalTitle(it) }
        canCloseWithEscape?.let { builder.canCloseWithEscape(it) }
        pause?.let { builder.pause(it) }
        afterAction?.let { builder.afterAction(it) }
        if (body.isNotEmpty()) {
            builder.body(body)
        }
        if (inputs.isNotEmpty()) {
            builder.inputs(inputs)
        }
        return builder.build()
    }
}

/**
 * 内部构建上下文，传递给需要 Paper 内联注册表构建器的类型构建器。
 */
class PaperDialogBuildContext internal constructor(
    internal val builder: DialogRegistryEntry.Builder
)

/**
 * 对话框类型构建器，可独立于根构建器选择具体类型。
 */
@PaperDialogDsl
class PaperDialogTypeBuilder internal constructor() {
    private var typeFactory: (PaperDialogBuildContext.() -> DialogType)? = null

    /**
     * 设置 Paper 原始对话框类型。
     *
     * @param type Paper 原始对话框类型。
     */
    fun raw(type: DialogType) {
        typeFactory = { type }
    }

    /**
     * 使用带 Paper 默认动作按钮的 notice 对话框。
     */
    fun notice() {
        raw(DialogType.notice())
    }

    /**
     * 使用带一个动作按钮的 notice 对话框。
     *
     * @param action 显示在 notice 底部的按钮。
     */
    fun notice(action: ActionButton) {
        raw(DialogType.notice(action))
    }

    /**
     * 使用带一个动作按钮的 notice 对话框。
     *
     * @param label 按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun notice(label: String, block: PaperActionButtonBuilder.() -> Unit = {}) {
        notice(dialogButton(label, block))
    }

    /**
     * 使用带一个动作按钮的 notice 对话框。
     *
     * @param label 按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun notice(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        notice(dialogButton(label, block))
    }

    /**
     * 使用确认对话框。
     *
     * @param yesButton 用于确认操作的按钮。
     * @param noButton 用于拒绝或取消操作的按钮。
     */
    fun confirmation(yesButton: ActionButton, noButton: ActionButton) {
        raw(DialogType.confirmation(yesButton, noButton))
    }

    /**
     * 使用带两个内联按钮的确认对话框。
     *
     * @param yesLabel 确认按钮标签。
     * @param noLabel 拒绝按钮标签。
     * @param yes 确认按钮的 DSL。
     * @param no 拒绝按钮的 DSL。
     */
    fun confirmation(
        yesLabel: String = "Yes",
        noLabel: String = "No",
        yes: PaperActionButtonBuilder.() -> Unit = {},
        no: PaperActionButtonBuilder.() -> Unit = {}
    ) {
        confirmation(dialogButton(yesLabel, yes), dialogButton(noLabel, no))
    }

    /**
     * 使用由 yes/no 专用 DSL 配置的确认对话框。
     *
     * @param block 用于配置 yes 和 no 按钮的 DSL。
     */
    fun confirmation(block: PaperConfirmationBuilder.() -> Unit) {
        val builder = PaperConfirmationBuilder().apply(block)
        confirmation(builder.buildYesButton(), builder.buildNoButton())
    }

    /**
     * 使用带多个动作按钮的对话框。
     *
     * @param columns 按钮列数。
     * @param block 用于添加动作按钮和可选退出按钮的 DSL。
     */
    fun multiAction(columns: Int = 1, block: PaperMultiActionBuilder.() -> Unit) {
        val builder = PaperMultiActionBuilder(columns).apply(block)
        typeFactory = { builder.build() }
    }

    /**
     * 使用展示其它对话框列表的对话框。
     *
     * @param columns 按钮列数。
     * @param buttonWidth 每个对话框列表按钮的宽度。
     * @param block 用于提供内联对话框或注册表对话框的 DSL。
     */
    fun dialogList(
        columns: Int = 1,
        buttonWidth: Int = 150,
        block: PaperDialogListBuilder.() -> Unit
    ) {
        val builder = PaperDialogListBuilder(columns, buttonWidth).apply(block)
        typeFactory = { builder.build(this) }
    }

    /**
     * 使用客户端 server-links 对话框。
     *
     * @param columns 链接列数。
     * @param buttonWidth 每个链接按钮的宽度。
     * @param exitAction 用于退出链接对话框的可选按钮。
     */
    fun serverLinks(
        columns: Int = 1,
        buttonWidth: Int = 150,
        exitAction: ActionButton? = null
    ) {
        raw(DialogType.serverLinks(exitAction, columns, buttonWidth))
    }

    /**
     * 使用客户端 server-links 对话框。
     *
     * @param block 用于配置列数、按钮宽度和退出动作的 DSL。
     */
    fun serverLinks(block: PaperServerLinksBuilder.() -> Unit) {
        val builder = PaperServerLinksBuilder().apply(block)
        typeFactory = { builder.build() }
    }

    internal fun build(context: PaperDialogBuildContext): DialogType {
        return typeFactory?.invoke(context) ?: DialogType.notice()
    }
}

