@file:Suppress("unused")

package cn.tj.dzd.mc.dzt.ui

import cn.tj.dzd.mc.dzt.util.foliaRun
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
import java.util.concurrent.CompletableFuture

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

    /**
     * 创建对话框并为玩家打开。
     *
     * 显示操作会进入玩家所属的 Folia 实体线程；返回值表示已创建的对话框，
     * 不表示客户端已显示成功。需要等待调度结果时使用 [Player.openFoliaPaperDialog]。
     *
     * @param player 接收对话框的玩家。
     * @param title 显示在对话框顶部的标题文本。
     * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
     */
    fun open(player: Player, title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
        return player.openPaperDialog(title, block)
    }

    /**
     * 创建对话框并为玩家打开。
     *
     * @param player 接收对话框的玩家。
     * @param title 显示在对话框顶部的标题组件。
     * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
     */
    fun open(player: Player, title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
        return player.openPaperDialog(title, block)
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
 * 为当前玩家创建并在其 Folia 实体线程打开动态 Paper 对话框。
 *
 * @param title 显示在对话框顶部的标题文本。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 * @return 对话框显示任务；玩家离线或实体调度器失效时完成为 null。
 */
fun Player.openFoliaPaperDialog(
    title: String,
    block: PaperDialogBuilder.() -> Unit = {},
): CompletableFuture<Dialog?> {
    return openFoliaPaperDialog(paperDialogTextComponent(title), block)
}

/**
 * 为当前玩家创建并在其 Folia 实体线程打开动态 Paper 对话框。
 *
 * @param title 显示在对话框顶部的标题组件。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 * @return 对话框显示任务；玩家离线或实体调度器失效时完成为 null。
 */
fun Player.openFoliaPaperDialog(
    title: ComponentLike,
    block: PaperDialogBuilder.() -> Unit = {},
): CompletableFuture<Dialog?> {
    return showFoliaPaperDialog(paperDialog(title, block))
}

/**
 * 为当前玩家创建并打开一个动态 Paper 对话框。
 *
 * 此同步别名保留既有调用方式：它会先创建 [Dialog]，再将实际显示操作调度到玩家实体线程，
 * 因此返回值仅表示已创建的对话框，不表示客户端已经显示成功。
 *
 * @param title 显示在对话框顶部的标题文本。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun Player.openPaperDialog(title: String, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    return openPaperDialog(paperDialogTextComponent(title), block)
}

/**
 * 为当前玩家创建并打开一个动态 Paper 对话框。
 *
 * 此同步别名保留既有调用方式；需要得知显示是否进入调度器时应使用 [openFoliaPaperDialog]。
 *
 * @param title 显示在对话框顶部的标题组件。
 * @param block 用于配置基础属性、内容、输入项和对话框类型的 DSL。
 */
fun Player.openPaperDialog(title: ComponentLike, block: PaperDialogBuilder.() -> Unit = {}): Dialog {
    val dialog = paperDialog(title, block)
    showFoliaPaperDialog(dialog)
    return dialog
}

internal fun Player.showFoliaPaperDialog(dialog: Dialog): CompletableFuture<Dialog?> {
    return foliaRun {
        showDialog(dialog)
    }.thenApply { scheduled ->
        if (scheduled) dialog else null
    }
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
