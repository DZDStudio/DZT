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
 * 将 Paper Dialog 的文本参数转换为 Adventure 组件。
 *
 * 当文本包含 Bukkit 旧式 `§` 格式码时会先反序列化，避免直接传给 [Component.text] 触发 Adventure 警告。
 */
internal fun paperDialogTextComponent(text: String): Component {
    return if ('§' in text) {
        LegacyComponentSerializer.legacySection().deserialize(text)
    } else {
        Component.text(text)
    }
}

/**
 * 标记 Paper 对话框 DSL 作用域，避免嵌套 block 中误调用外层或其它层级的 API。
 */
@DslMarker
annotation class PaperDialogDsl

/**
 * Paper 对话框 Kotlin DSL 的入口对象，用于创建或直接打开动态对话框。
 */
object PaperDialogUI {
    /**
     * 创建一个动态 Paper 对话框。
     *
     * @param title 显示在对话框顶部的标题文本。
     * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
     */
    fun create(title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
        return paperDialog(title, block)
    }

    /**
     * 创建一个动态 Paper 对话框。
     *
     * @param title 显示在对话框顶部的标题组件。
     * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
     */
    fun create(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
        return paperDialog(title, block)
    }

    /**
     * 创建对话框并立即为指定受众打开。
     *
     * @param audience 接收对话框的受众，通常是 Player。
     * @param title 显示在对话框顶部的标题文本。
     * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
     */
    fun open(audience: Audience, title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
        return audience.openPaperDialog(title, block)
    }

    /**
     * 创建对话框并立即为指定受众打开。
     *
     * @param audience 接收对话框的受众，通常是 Player。
     * @param title 显示在对话框顶部的标题组件。
     * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
     */
    fun open(audience: Audience, title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
        return audience.openPaperDialog(title, block)
    }
}

/**
 * 创建一个动态 Paper 对话框，但不立即打开。
 *
 * @param title 显示在对话框顶部的标题文本。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun paperDialog(title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    return paperDialog(paperDialogTextComponent(title), block)
}

/**
 * 创建一个动态 Paper 对话框，但不立即打开。
 *
 * @param title 显示在对话框顶部的标题组件。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun paperDialog(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    val spec = PaperDialogBuilder(title.asComponent()).apply(block)
    return Dialog.create { factory ->
        spec.buildInto(factory.empty())
    }
}

/**
 * 为当前受众创建并打开一个动态 Paper 对话框。
 *
 * @param title 显示在对话框顶部的标题文本。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun Audience.openPaperDialog(title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    return openPaperDialog(paperDialogTextComponent(title), block)
}

/**
 * 为当前受众创建并打开一个动态 Paper 对话框。
 *
 * @param title 显示在对话框顶部的标题组件。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun Audience.openPaperDialog(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    val dialog = paperDialog(title, block)
    showDialog(dialog)
    return dialog
}

/**
 * 关闭当前受众正在显示的对话框，如果没有打开对话框则不执行额外操作。
 */
fun Audience.closePaperDialog() {
    closeDialog()
}

/**
 * 为当前玩家打开一个动态 Paper 对话框。
 *
 * @param title 显示在对话框顶部的标题文本。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun Player.PaperDialogUI(title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    return openPaperDialog(title, block)
}

/**
 * 为当前玩家打开一个动态 Paper 对话框。
 *
 * @param title 显示在对话框顶部的标题组件。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun Player.PaperDialogUI(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    return openPaperDialog(title, block)
}

/**
 * 创建一个可复用的对话框按钮。
 *
 * @param label 显示在按钮上的文本。
 * @param block 用于配置悬浮提示、宽度和点击动作的 DSL。
 */
fun dialogButton(label: String, block: PaperActionButtonBuilder.() -> Unit = {}): ActionButton {
    return dialogButton(paperDialogTextComponent(label), block)
}

/**
 * 创建一个可复用的对话框按钮。
 *
 * @param label 显示在按钮上的组件。
 * @param block 用于配置悬浮提示、宽度和点击动作的 DSL。
 */
fun dialogButton(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}): ActionButton {
    return PaperActionButtonBuilder(label.asComponent()).apply(block).build()
}

/**
 * 将 Paper 原始响应视图包装为带类型读取辅助方法的对象。
 */
fun DialogResponseView.paperDialogResponse(): PaperDialogResponse {
    return PaperDialogResponse(this)
}

/**
 * 从自定义点击事件中读取对话框响应；如果该点击不是来自对话框，则返回 null。
 */
fun PlayerCustomClickEvent.paperDialogResponse(): PaperDialogResponse? {
    return dialogResponseView?.paperDialogResponse()
}

/**
 * Paper 对话框输入结果的便捷包装。
 *
 * @property view Paper 原始响应视图。
 */
@JvmInline
value class PaperDialogResponse(val view: DialogResponseView) {
    /**
     * 客户端返回的原始 NBT 负载。
     */
    val payload: BinaryTagHolder
        get() = view.payload()

    /**
     * 读取文本输入值。
     *
     * @param key 在对话框输入项中配置的键。
     */
    fun text(key: String): String? {
        return view.getText(key)
    }

    /**
     * 读取必填文本输入值。
     *
     * @param key 在对话框输入项中配置的键。
     * @throws IllegalArgumentException 当该键不存在或对应值不是文本时抛出。
     */
    fun requiredText(key: String): String {
        return text(key) ?: missing(key, "text")
    }

    /**
     * 读取布尔输入值。
     *
     * @param key 在对话框输入项中配置的键。
     */
    fun boolean(key: String): Boolean? {
        return view.getBoolean(key)
    }

    /**
     * 读取必填布尔输入值。
     *
     * @param key 在对话框输入项中配置的键。
     * @throws IllegalArgumentException 当该键不存在或对应值不是布尔值时抛出。
     */
    fun requiredBoolean(key: String): Boolean {
        return boolean(key) ?: missing(key, "boolean")
    }

    /**
     * 读取数值范围输入值。
     *
     * @param key 在对话框输入项中配置的键。
     */
    fun float(key: String): Float? {
        return view.getFloat(key)
    }

    /**
     * 读取必填数值范围输入值。
     *
     * @param key 在对话框输入项中配置的键。
     * @throws IllegalArgumentException 当该键不存在或对应值不是浮点数时抛出。
     */
    fun requiredFloat(key: String): Float {
        return float(key) ?: missing(key, "float")
    }

    /**
     * [text] 的简写。
     *
     * @param key 在对话框输入项中配置的键。
     */
    operator fun get(key: String): String? {
        return text(key)
    }

    private fun missing(key: String, type: String): Nothing {
        throw IllegalArgumentException("对话框响应中缺少键 '$key' 对应的 $type 值")
    }
}

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

/**
 * 对话框输入控件构建器。
 */
@PaperDialogDsl
class PaperDialogInputBuilder internal constructor() {
    private val inputs = mutableListOf<DialogInput>()

    /**
     * 添加 Paper 原始输入项。
     *
     * @param value Paper 输入对象。
     */
    fun raw(value: DialogInput) {
        inputs += value
    }

    /**
     * 添加文本输入框。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在输入框附近的标签文本。
     * @param width 可选输入框宽度；为 null 时保留 Paper 默认值。
     * @param labelVisible 是否显示标签；为 null 时保留 Paper 默认值。
     * @param initial 初始文本；为 null 时保留 Paper 默认值。
     * @param maxLength 最大文本长度；为 null 时保留 Paper 默认值。
     * @param multiline 多行输入配置；为 null 时默认为单行，除非嵌套 block 修改。
     * @param block 额外文本输入配置 DSL。
     */
    fun text(
        key: String,
        label: String,
        width: Int? = null,
        labelVisible: Boolean? = null,
        initial: String? = null,
        maxLength: Int? = null,
        multiline: TextDialogInput.MultilineOptions? = null,
        block: PaperTextDialogInputBuilder.() -> Unit = {}
    ) {
        text(key, paperDialogTextComponent(label), width, labelVisible, initial, maxLength, multiline, block)
    }

    /**
     * 添加文本输入框。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在输入框附近的标签组件。
     * @param width 可选输入框宽度；为 null 时保留 Paper 默认值。
     * @param labelVisible 是否显示标签；为 null 时保留 Paper 默认值。
     * @param initial 初始文本；为 null 时保留 Paper 默认值。
     * @param maxLength 最大文本长度；为 null 时保留 Paper 默认值。
     * @param multiline 多行输入配置；为 null 时默认为单行，除非嵌套 block 修改。
     * @param block 额外文本输入配置 DSL。
     */
    fun text(
        key: String,
        label: ComponentLike,
        width: Int? = null,
        labelVisible: Boolean? = null,
        initial: String? = null,
        maxLength: Int? = null,
        multiline: TextDialogInput.MultilineOptions? = null,
        block: PaperTextDialogInputBuilder.() -> Unit = {}
    ) {
        inputs += PaperTextDialogInputBuilder(key, label.asComponent())
            .apply {
                width?.let { width(it) }
                labelVisible?.let { labelVisible(it) }
                initial?.let { initial(it) }
                maxLength?.let { maxLength(it) }
                multiline?.let { multiline(it) }
                block()
            }
            .build()
    }

    /**
     * 添加布尔开关输入。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在开关附近的标签文本。
     * @param initial 初始开关值；为 null 时保留 Paper 默认值。
     * @param onTrue 值为 true 时用于命令模板替换的文本；为 null 时保留 Paper 默认值。
     * @param onFalse 值为 false 时用于命令模板替换的文本；为 null 时保留 Paper 默认值。
     * @param block 额外布尔输入配置 DSL。
     */
    fun bool(
        key: String,
        label: String,
        initial: Boolean? = null,
        onTrue: String? = null,
        onFalse: String? = null,
        block: PaperBooleanDialogInputBuilder.() -> Unit = {}
    ) {
        bool(key, paperDialogTextComponent(label), initial, onTrue, onFalse, block)
    }

    /**
     * 添加布尔开关输入。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在开关附近的标签组件。
     * @param initial 初始开关值；为 null 时保留 Paper 默认值。
     * @param onTrue 值为 true 时用于命令模板替换的文本；为 null 时保留 Paper 默认值。
     * @param onFalse 值为 false 时用于命令模板替换的文本；为 null 时保留 Paper 默认值。
     * @param block 额外布尔输入配置 DSL。
     */
    fun bool(
        key: String,
        label: ComponentLike,
        initial: Boolean? = null,
        onTrue: String? = null,
        onFalse: String? = null,
        block: PaperBooleanDialogInputBuilder.() -> Unit = {}
    ) {
        inputs += PaperBooleanDialogInputBuilder(key, label.asComponent())
            .apply {
                initial?.let { initial(it) }
                onTrue?.let { onTrue(it) }
                onFalse?.let { onFalse(it) }
                block()
            }
            .build()
    }

    /**
     * 添加数值范围滑块输入。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在滑块附近的标签文本。
     * @param start 滑块最小值。
     * @param end 滑块最大值。
     * @param width 可选滑块宽度；为 null 时保留 Paper 默认值。
     * @param labelFormat 滑块标签的翻译键或格式字符串。
     * @param initial 初始滑块值；为 null 时保留 Paper 默认值。
     * @param step 滑块步长；为 null 时保留 Paper 默认值。
     * @param block 额外数值范围配置 DSL。
     */
    fun numberRange(
        key: String,
        label: String,
        start: Float,
        end: Float,
        width: Int? = null,
        labelFormat: String? = null,
        initial: Float? = null,
        step: Float? = null,
        block: PaperNumberRangeDialogInputBuilder.() -> Unit = {}
    ) {
        numberRange(key, paperDialogTextComponent(label), start, end, width, labelFormat, initial, step, block)
    }

    /**
     * 添加数值范围滑块输入。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在滑块附近的标签组件。
     * @param start 滑块最小值。
     * @param end 滑块最大值。
     * @param width 可选滑块宽度；为 null 时保留 Paper 默认值。
     * @param labelFormat 滑块标签的翻译键或格式字符串。
     * @param initial 初始滑块值；为 null 时保留 Paper 默认值。
     * @param step 滑块步长；为 null 时保留 Paper 默认值。
     * @param block 额外数值范围配置 DSL。
     */
    fun numberRange(
        key: String,
        label: ComponentLike,
        start: Float,
        end: Float,
        width: Int? = null,
        labelFormat: String? = null,
        initial: Float? = null,
        step: Float? = null,
        block: PaperNumberRangeDialogInputBuilder.() -> Unit = {}
    ) {
        inputs += PaperNumberRangeDialogInputBuilder(key, label.asComponent(), start, end)
            .apply {
                width?.let { width(it) }
                labelFormat?.let { labelFormat(it) }
                initial?.let { initial(it) }
                step?.let { step(it) }
                block()
            }
            .build()
    }

    /**
     * 添加单选输入。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在选项附近的标签文本。
     * @param width 可选输入宽度；为 null 时保留 Paper 默认值。
     * @param labelVisible 是否显示标签；为 null 时保留 Paper 默认值。
     * @param block 用于添加选项的 DSL。
     */
    fun singleOption(
        key: String,
        label: String,
        width: Int? = null,
        labelVisible: Boolean? = null,
        block: PaperSingleOptionDialogInputBuilder.() -> Unit
    ) {
        singleOption(key, paperDialogTextComponent(label), width, labelVisible, block)
    }

    /**
     * 添加单选输入。
     *
     * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
     * @param label 显示在选项附近的标签组件。
     * @param width 可选输入宽度；为 null 时保留 Paper 默认值。
     * @param labelVisible 是否显示标签；为 null 时保留 Paper 默认值。
     * @param block 用于添加选项的 DSL。
     */
    fun singleOption(
        key: String,
        label: ComponentLike,
        width: Int? = null,
        labelVisible: Boolean? = null,
        block: PaperSingleOptionDialogInputBuilder.() -> Unit
    ) {
        inputs += PaperSingleOptionDialogInputBuilder(key, label.asComponent())
            .apply {
                width?.let { width(it) }
                labelVisible?.let { labelVisible(it) }
                block()
            }
            .build()
    }

    internal fun build(): List<DialogInput> {
        return inputs.toList()
    }
}

/**
 * 文本输入选项构建器。
 *
 * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
 * @param label 显示在输入框附近的标签组件。
 */
@PaperDialogDsl
class PaperTextDialogInputBuilder internal constructor(
    private val key: String,
    private val label: Component
) {
    private var width: Int? = null
    private var labelVisible: Boolean? = null
    private var initial: String? = null
    private var maxLength: Int? = null
    private var multiline: TextDialogInput.MultilineOptions? = null

    /**
     * 设置文本输入框宽度。
     *
     * @param value 对话框像素单位的宽度。
     */
    fun width(value: Int) {
        width = value
    }

    /**
     * 控制是否显示输入标签。
     *
     * @param value 为 true 时显示标签。
     */
    fun labelVisible(value: Boolean = true) {
        labelVisible = value
    }

    fun hideLabel() {
        labelVisible(false)
    }

    /**
     * 设置初始文本。
     *
     * @param value 初始文本。
     */
    fun initial(value: String) {
        initial = value
    }

    /**
     * 设置允许输入的最大文本长度。
     *
     * @param value 最大字符数。
     */
    fun maxLength(value: Int) {
        maxLength = value
    }

    /**
     * 设置 Paper 原始多行输入选项。
     *
     * @param value 多行选项；为 null 时使用单行或默认行为。
     */
    fun multiline(value: TextDialogInput.MultilineOptions?) {
        multiline = value
    }

    /**
     * 启用多行输入。
     *
     * @param maxLines 可选最大行数。
     * @param height 可选输入框高度，单位为对话框像素。
     */
    fun multiline(maxLines: Int? = null, height: Int? = null) {
        multiline(TextDialogInput.MultilineOptions.create(maxLines, height))
    }

    internal fun build(): TextDialogInput {
        val builder = DialogInput.text(key, label)
        width?.let { builder.width(it) }
        labelVisible?.let { builder.labelVisible(it) }
        initial?.let { builder.initial(it) }
        maxLength?.let { builder.maxLength(it) }
        if (multiline != null) {
            builder.multiline(multiline)
        }
        return builder.build()
    }
}

/**
 * 布尔输入选项构建器。
 *
 * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
 * @param label 显示在开关附近的标签组件。
 */
@PaperDialogDsl
class PaperBooleanDialogInputBuilder internal constructor(
    private val key: String,
    private val label: Component
) {
    private var initial: Boolean? = null
    private var onTrue: String? = null
    private var onFalse: String? = null

    /**
     * 设置初始开关值。
     *
     * @param value 为 true 时初始状态为开启。
     */
    fun initial(value: Boolean = true) {
        initial = value
    }

    /**
     * 设置开关为 true 时用于命令模板替换的文本。
     *
     * @param value 插入命令模板的文本。
     */
    fun onTrue(value: String) {
        onTrue = value
    }

    /**
     * 设置开关为 false 时用于命令模板替换的文本。
     *
     * @param value 插入命令模板的文本。
     */
    fun onFalse(value: String) {
        onFalse = value
    }

    /**
     * 同时设置 true 和 false 的命令模板替换文本。
     *
     * @param onTrue 值为 true 时插入命令模板的文本。
     * @param onFalse 值为 false 时插入命令模板的文本。
     */
    fun templateValues(onTrue: String, onFalse: String) {
        onTrue(onTrue)
        onFalse(onFalse)
    }

    internal fun build(): BooleanDialogInput {
        val builder = DialogInput.bool(key, label)
        initial?.let { builder.initial(it) }
        onTrue?.let { builder.onTrue(it) }
        onFalse?.let { builder.onFalse(it) }
        return builder.build()
    }
}

/**
 * 数值范围输入选项构建器。
 *
 * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
 * @param label 显示在滑块附近的标签组件。
 * @param start 滑块最小值。
 * @param end 滑块最大值。
 */
@PaperDialogDsl
class PaperNumberRangeDialogInputBuilder internal constructor(
    private val key: String,
    private val label: Component,
    private val start: Float,
    private val end: Float
) {
    private var width: Int? = null
    private var labelFormat: String? = null
    private var initial: Float? = null
    private var step: Float? = null

    /**
     * 设置滑块宽度。
     *
     * @param value 对话框像素单位的宽度。
     */
    fun width(value: Int) {
        width = value
    }

    /**
     * 设置滑块标签格式。
     *
     * @param value 翻译键或格式字符串，例如 "%s: %s"。
     */
    fun labelFormat(value: String) {
        labelFormat = value
    }

    /**
     * 设置初始滑块值。
     *
     * @param value 初始数值。
     */
    fun initial(value: Float) {
        initial = value
    }

    /**
     * 设置滑块步长。
     *
     * @param value 正数步长。
     */
    fun step(value: Float) {
        step = value
    }

    internal fun build(): NumberRangeDialogInput {
        val builder = DialogInput.numberRange(key, label, start, end)
        width?.let { builder.width(it) }
        labelFormat?.let { builder.labelFormat(it) }
        initial?.let { builder.initial(it) }
        step?.let { builder.step(it) }
        return builder.build()
    }
}

/**
 * 单选输入选项构建器。
 *
 * @param key 后续在动作或 [PaperDialogResponse] 中读取该输入值时使用的键。
 * @param label 显示在选项附近的标签组件。
 */
@PaperDialogDsl
class PaperSingleOptionDialogInputBuilder internal constructor(
    private val key: String,
    private val label: Component
) {
    private val entries = mutableListOf<SingleOptionDialogInput.OptionEntry>()
    private var width: Int? = null
    private var labelVisible: Boolean? = null

    /**
     * 设置单选输入宽度。
     *
     * @param value 对话框像素单位的宽度。
     */
    fun width(value: Int) {
        width = value
    }

    /**
     * 控制是否显示输入标签。
     *
     * @param value 为 true 时显示标签。
     */
    fun labelVisible(value: Boolean = true) {
        labelVisible = value
    }

    fun hideLabel() {
        labelVisible(false)
    }

    /**
     * 添加一个选项。
     *
     * @param id 该选项被选中时返回的值。
     * @param display 可选显示文本；为 null 时使用 Paper 默认显示。
     * @param initial 为 true 时该选项初始被选中。
     */
    fun option(id: String, display: String? = null, initial: Boolean = false) {
        option(id, display?.let { paperDialogTextComponent(it) }, initial)
    }

    /**
     * 添加一个选项。
     *
     * @param id 该选项被选中时返回的值。
     * @param display 可选显示组件；为 null 时使用 Paper 默认显示。
     * @param initial 为 true 时该选项初始被选中。
     */
    fun option(id: String, display: ComponentLike?, initial: Boolean = false) {
        entries += SingleOptionDialogInput.OptionEntry.create(id, display?.asComponent(), initial)
    }

    /**
     * 添加 Paper 原始选项条目。
     *
     * @param value 原始选项条目。
     */
    fun rawOption(value: SingleOptionDialogInput.OptionEntry) {
        entries += value
    }

    internal fun build(): SingleOptionDialogInput {
        val builder = DialogInput.singleOption(key, label, entries)
        width?.let { builder.width(it) }
        labelVisible?.let { builder.labelVisible(it) }
        return builder.build()
    }
}

/**
 * 对话框动作按钮构建器。
 *
 * @param label 按钮标签组件。
 */
@PaperDialogDsl
class PaperActionButtonBuilder internal constructor(
    private val label: Component
) {
    private var tooltip: Component? = null
    private var width: Int? = null
    private var action: DialogAction? = null

    /**
     * 设置鼠标悬浮提示文本。
     *
     * @param value 提示文本；为 null 时不显示提示。
     */
    fun tooltip(value: String?) {
        tooltip(value?.let { paperDialogTextComponent(it) })
    }

    /**
     * 设置鼠标悬浮提示组件。
     *
     * @param value 提示组件；为 null 时不显示提示。
     */
    fun tooltip(value: ComponentLike?) {
        tooltip = value?.asComponent()
    }

    /**
     * 设置按钮宽度。
     *
     * @param value 对话框像素单位的宽度。
     */
    fun width(value: Int) {
        width = value
    }

    /**
     * 设置 Paper 原始按钮动作。
     *
     * @param value 点击时执行的动作；为 null 时按钮不执行动作。
     */
    fun action(value: DialogAction?) {
        action = value
    }

    /**
     * 移除此按钮的点击动作。
     */
    fun noAction() {
        action(null)
    }

    /**
     * 执行命令模板。可以使用 `$(input_key)` 插入输入值。
     *
     * @param template 命令模板；除非命令本身需要，否则通常不带开头斜杠。
     */
    fun commandTemplate(template: String) {
        action(DialogAction.commandTemplate(template))
    }

    /**
     * [commandTemplate] 的别名。
     *
     * @param template 命令模板；除非命令本身需要，否则通常不带开头斜杠。
     */
    fun command(template: String) {
        commandTemplate(template)
    }

    /**
     * 使用 Adventure 原始点击事件作为静态动作。
     *
     * @param value 要执行的 Adventure 点击事件。
     */
    fun staticClick(value: ClickEvent) {
        action(DialogAction.staticAction(value))
    }

    /**
     * 让玩家执行固定命令。
     *
     * @param command 命令字符串。
     */
    fun runCommand(command: String) {
        staticClick(ClickEvent.runCommand(command))
    }

    /**
     * 在玩家聊天输入框中建议命令。
     *
     * @param command 建议的命令字符串。
     */
    fun suggestCommand(command: String) {
        staticClick(ClickEvent.suggestCommand(command))
    }

    /**
     * 在客户端打开 URL。
     *
     * @param url URL 字符串。较新的客户端要求使用 http 或 https。
     */
    fun openUrl(url: String) {
        staticClick(ClickEvent.openUrl(url))
    }

    /**
     * 在客户端打开 URL。
     *
     * @param url URL 对象。
     */
    fun openUrl(url: URL) {
        staticClick(ClickEvent.openUrl(url))
    }

    /**
     * 将文本复制到玩家剪贴板。
     *
     * @param text 要复制到剪贴板的文本。
     */
    fun copyToClipboard(text: String) {
        staticClick(ClickEvent.copyToClipboard(text))
    }

    /**
     * 在类似书本的上下文中切换页面。
     *
     * @param page 目标页码。
     */
    fun changePage(page: Int) {
        staticClick(ClickEvent.changePage(page))
    }

    /**
     * 点击此按钮时打开另一个对话框。
     *
     * @param dialog 要显示的对话框对象。
     */
    fun showDialog(dialog: Dialog) {
        staticClick(ClickEvent.showDialog(dialog))
    }

    /**
     * 点击此按钮时构建并打开另一个对话框。
     *
     * @param title 嵌套对话框标题。
     * @param block 用于配置嵌套对话框的 DSL。
     */
    fun showDialog(title: String, block: PaperDialogBuilder.() -> Unit = {}) {
        showDialog(paperDialog(title, block))
    }

    /**
     * 点击此按钮时构建并打开另一个对话框。
     *
     * @param title 嵌套对话框标题组件。
     * @param block 用于配置嵌套对话框的 DSL。
     */
    fun showDialog(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}) {
        showDialog(paperDialog(title, block))
    }

    /**
     * 发送由 [id] 标识的自定义点击事件。
     *
     * 当你希望通过 [PlayerCustomClickEvent] 处理点击时使用。
     *
     * @param id 自定义点击标识符。
     * @param additions 随对话框响应一同发送的可选额外 NBT 数据。
     */
    fun customClick(id: Key, additions: BinaryTagHolder? = null) {
        action(DialogAction.customClick(id, additions))
    }

    /**
     * 发送由命名空间和值标识的自定义点击事件。
     *
     * 当你希望通过 [PlayerCustomClickEvent] 处理点击时使用。
     *
     * @param namespace Key 命名空间。
     * @param value Key 的值或路径。
     * @param additions 随对话框响应一同发送的可选额外 NBT 数据。
     */
    fun customClick(namespace: String, value: String, additions: BinaryTagHolder? = null) {
        customClick(Key.key(namespace, value), additions)
    }

    /**
     * 发送带 SNBT 附加数据的自定义点击事件。
     *
     * @param id 自定义点击标识符。
     * @param additionsSnbt 以 SNBT 编码的额外数据。
     */
    fun customClick(id: Key, additionsSnbt: String) {
        customClick(id, BinaryTagHolder.binaryTagHolder(additionsSnbt))
    }

    /**
     * 发送带 SNBT 附加数据的自定义点击事件。
     *
     * @param namespace Key 命名空间。
     * @param value Key 的值或路径。
     * @param additionsSnbt 以 SNBT 编码的额外数据。
     */
    fun customClick(namespace: String, value: String, additionsSnbt: String) {
        customClick(Key.key(namespace, value), additionsSnbt)
    }

    /**
     * 向 Paper 注册内联回调动作。
     *
     * 适合局部、短生命周期处理逻辑；如果需要全局监听，请使用自定义点击事件。
     *
     * @param uses 回调可使用次数；使用 [ClickCallback.UNLIMITED_USES] 表示不限次数。
     * @param lifetime 回调有效时长。
     * @param block 回调逻辑；接收者是对话框响应，参数是点击的受众。
     */
    fun callback(
        uses: Int = 1,
        lifetime: TemporalAmount = ClickCallback.DEFAULT_LIFETIME,
        block: PaperDialogResponse.(Audience) -> Unit
    ) {
        callback(callbackOptions(uses, lifetime), block)
    }

    /**
     * 使用显式回调选项向 Paper 注册内联回调动作。
     *
     * @param options Paper/Adventure 回调选项。
     * @param block 回调逻辑；接收者是对话框响应，参数是点击的受众。
     */
    fun callback(
        options: ClickCallback.Options,
        block: PaperDialogResponse.(Audience) -> Unit
    ) {
        action(DialogAction.customClick(DialogActionCallback { response, audience ->
            PaperDialogResponse(response).block(audience)
        }, options))
    }

    /**
     * 构建 Adventure 回调选项。
     *
     * @param uses 回调可使用次数。
     * @param lifetime 回调有效时长。
     */
    fun callbackOptions(
        uses: Int = 1,
        lifetime: TemporalAmount = ClickCallback.DEFAULT_LIFETIME
    ): ClickCallback.Options {
        return ClickCallback.Options.builder()
            .uses(uses)
            .lifetime(lifetime)
            .build()
    }

    internal fun build(): ActionButton {
        val builder = ActionButton.builder(label)
        tooltip?.let { builder.tooltip(it) }
        width?.let { builder.width(it) }
        builder.action(action)
        return builder.build()
    }
}

/**
 * 确认对话框按钮构建器。
 */
@PaperDialogDsl
class PaperConfirmationBuilder internal constructor() {
    private var yesButton: ActionButton? = null
    private var noButton: ActionButton? = null

    /**
     * 设置确认按钮。
     *
     * @param button 用于确认操作的按钮。
     */
    fun yes(button: ActionButton) {
        yesButton = button
    }

    /**
     * 设置确认按钮。
     *
     * @param label 确认按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun yes(label: String = "Yes", block: PaperActionButtonBuilder.() -> Unit = {}) {
        yes(dialogButton(label, block))
    }

    /**
     * 设置确认按钮。
     *
     * @param label 确认按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun yes(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        yes(dialogButton(label, block))
    }

    /**
     * 设置拒绝按钮。
     *
     * @param button 用于拒绝或取消操作的按钮。
     */
    fun no(button: ActionButton) {
        noButton = button
    }

    /**
     * 设置拒绝按钮。
     *
     * @param label 拒绝按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun no(label: String = "No", block: PaperActionButtonBuilder.() -> Unit = {}) {
        no(dialogButton(label, block))
    }

    /**
     * 设置拒绝按钮。
     *
     * @param label 拒绝按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun no(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        no(dialogButton(label, block))
    }

    internal fun buildYesButton(): ActionButton {
        return yesButton ?: dialogButton("Yes")
    }

    internal fun buildNoButton(): ActionButton {
        return noButton ?: dialogButton("No")
    }
}

/**
 * 多动作对话框类型构建器。
 *
 * @param columns 初始按钮列数。
 */
@PaperDialogDsl
class PaperMultiActionBuilder internal constructor(
    columns: Int
) {
    private val actions = mutableListOf<ActionButton>()
    private var exitAction: ActionButton? = null
    private var columns: Int = columns

    /**
     * 设置动作按钮列数。
     *
     * @param value 列数。
     */
    fun columns(value: Int) {
        columns = value
    }

    /**
     * 添加原始动作按钮。
     *
     * @param button 作为主动作之一显示的按钮。
     */
    fun action(button: ActionButton) {
        actions += button
    }

    /**
     * 添加动作按钮。
     *
     * @param label 按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun action(label: String, block: PaperActionButtonBuilder.() -> Unit = {}) {
        action(dialogButton(label, block))
    }

    /**
     * 添加动作按钮。
     *
     * @param label 按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun action(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        action(dialogButton(label, block))
    }

    /**
     * [action] 的别名。
     *
     * @param label 按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun button(label: String, block: PaperActionButtonBuilder.() -> Unit = {}) {
        action(label, block)
    }

    /**
     * [action] 的别名。
     *
     * @param label 按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun button(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        action(label, block)
    }

    /**
     * 设置可选退出按钮。
     *
     * @param button 退出按钮；为 null 时不设置显式退出动作。
     */
    fun exit(button: ActionButton?) {
        exitAction = button
    }

    /**
     * 设置可选退出按钮。
     *
     * @param label 退出按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun exit(label: String = "Back", block: PaperActionButtonBuilder.() -> Unit = {}) {
        exit(dialogButton(label, block))
    }

    /**
     * 设置可选退出按钮。
     *
     * @param label 退出按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun exit(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        exit(dialogButton(label, block))
    }

    internal fun build(): DialogType {
        return DialogType.multiAction(actions)
            .exitAction(exitAction)
            .columns(columns)
            .build()
    }
}

/**
 * 对话框列表类型构建器。
 *
 * @param columns 初始按钮列数。
 * @param buttonWidth 对话框列表按钮的初始宽度。
 */
@PaperDialogDsl
class PaperDialogListBuilder internal constructor(
    columns: Int,
    buttonWidth: Int
) {
    private val anonymousSpecs = mutableListOf<PaperDialogBuilder>()
    private var dialogs: RegistrySet<Dialog>? = null
    private var exitAction: ActionButton? = null
    private var columns: Int = columns
    private var buttonWidth: Int = buttonWidth

    /**
     * 设置对话框列表按钮列数。
     *
     * @param value 列数。
     */
    fun columns(value: Int) {
        columns = value
    }

    /**
     * 设置每个对话框列表按钮的宽度。
     *
     * @param value 对话框像素单位的按钮宽度。
     */
    fun buttonWidth(value: Int) {
        buttonWidth = value
    }

    /**
     * 设置对话框列表使用的原始注册表集合。
     *
     * @param value 对话框注册表集合。
     */
    fun dialogs(value: RegistrySet<Dialog>) {
        dialogs = value
    }

    /**
     * 使用已经注册的对话框 key。
     *
     * @param keys 带类型的对话框注册表 key。
     */
    fun registered(vararg keys: TypedKey<Dialog>) {
        dialogs(RegistrySet.keySet(RegistryKey.DIALOG, *keys))
    }

    /**
     * 使用已经注册的对话框实例。
     *
     * @param dialogs 由注册表支持的对话框值。
     */
    fun registeredValues(vararg dialogs: Dialog) {
        this.dialogs(RegistrySet.keySetFromValues(RegistryKey.DIALOG, dialogs.asList()))
    }

    /**
     * 使用匿名对话框值。
     *
     * @param dialogs 匿名对话框实例。
     */
    fun anonymousValues(vararg dialogs: Dialog) {
        this.dialogs(RegistrySet.valueSet(RegistryKey.DIALOG, dialogs.asList()))
    }

    /**
     * 向列表添加一个内联匿名对话框。
     *
     * @param title 内联对话框标题文本。
     * @param block 用于配置内联对话框的 DSL。
     */
    fun dialog(title: String, block: PaperDialogBuilder.() -> Unit = {}) {
        dialog(paperDialogTextComponent(title), block)
    }

    /**
     * 向列表添加一个内联匿名对话框。
     *
     * @param title 内联对话框标题组件。
     * @param block 用于配置内联对话框的 DSL。
     */
    fun dialog(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}) {
        anonymousSpecs += PaperDialogBuilder(title.asComponent()).apply(block)
    }

    /**
     * 设置可选退出按钮。
     *
     * @param button 退出按钮；为 null 时不设置显式退出动作。
     */
    fun exit(button: ActionButton?) {
        exitAction = button
    }

    /**
     * 设置可选退出按钮。
     *
     * @param label 退出按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun exit(label: String = "Back", block: PaperActionButtonBuilder.() -> Unit = {}) {
        exit(dialogButton(label, block))
    }

    /**
     * 设置可选退出按钮。
     *
     * @param label 退出按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun exit(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        exit(dialogButton(label, block))
    }

    internal fun build(context: PaperDialogBuildContext): DialogType {
        val dialogSet = dialogs ?: buildAnonymousSet(context)
        require(dialogs == null || anonymousSpecs.isEmpty()) {
            "对话框列表不能同时混用 RegistrySet 和内联匿名对话框"
        }
        return DialogType.dialogList(dialogSet)
            .exitAction(exitAction)
            .columns(columns)
            .buttonWidth(buttonWidth)
            .build()
    }

    private fun buildAnonymousSet(context: PaperDialogBuildContext): RegistrySet<Dialog> {
        val valueSet = context.builder.registryValueSet()
        anonymousSpecs.forEach { spec ->
            valueSet.add { factory ->
                spec.buildInto(factory.empty())
            }
        }
        return valueSet.build()
    }
}

/**
 * server-links 对话框类型构建器。
 */
@PaperDialogDsl
class PaperServerLinksBuilder internal constructor() {
    private var exitAction: ActionButton? = null
    private var columns: Int = 1
    private var buttonWidth: Int = 150

    /**
     * 设置服务器链接列数。
     *
     * @param value 列数。
     */
    fun columns(value: Int) {
        columns = value
    }

    /**
     * 设置每个服务器链接按钮的宽度。
     *
     * @param value 对话框像素单位的按钮宽度。
     */
    fun buttonWidth(value: Int) {
        buttonWidth = value
    }

    /**
     * 设置可选退出按钮。
     *
     * @param button 退出按钮；为 null 时不设置显式退出动作。
     */
    fun exit(button: ActionButton?) {
        exitAction = button
    }

    /**
     * 设置可选退出按钮。
     *
     * @param label 退出按钮标签文本。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun exit(label: String = "Back", block: PaperActionButtonBuilder.() -> Unit = {}) {
        exit(dialogButton(label, block))
    }

    /**
     * 设置可选退出按钮。
     *
     * @param label 退出按钮标签组件。
     * @param block 用于配置悬浮提示、宽度和点击动作的按钮 DSL。
     */
    fun exit(label: ComponentLike, block: PaperActionButtonBuilder.() -> Unit = {}) {
        exit(dialogButton(label, block))
    }

    internal fun build(): DialogType {
        return DialogType.serverLinks(exitAction, columns, buttonWidth)
    }
}
