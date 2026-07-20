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

