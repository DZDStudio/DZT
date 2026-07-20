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

