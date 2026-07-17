@file:Suppress("unused")

package cn.tj.dzd.mc.dzt.ui

import io.papermc.paper.dialog.Dialog
import net.kyori.adventure.text.ComponentLike
import org.bukkit.entity.Player

private const val TEXT_INPUT_KEY = "text"
private const val DEFAULT_TITLE = "请输入文字"
private const val DEFAULT_INPUT_LABEL = "内容"
private const val SUBMIT_LABEL = "确定"
private const val CANCEL_LABEL = "取消"

/**
 * 文本提交回调。
 *
 * 回调接收打开 UI 的玩家与玩家提交的文字内容。
 */
typealias PaperDialogTextCallback = Player.(String) -> Unit

/**
 * 基于 [PaperDialogUI] 的文本输入对话框封装。
 *
 * 该 UI 仅包含一个文本输入框，适合需要从玩家处获取一段文字的场景。
 */
object PaperDialogTextUI {

    /**
     * 创建文本输入对话框，但不立即打开。
     *
     * @param title 对话框标题。
     * @param defaultContent 输入框默认内容。
     * @param inputLabel 输入框上方显示的名称。
     * @param onSubmit 玩家点击确认按钮后的文本回调。
     */
    fun create(
        title: String = DEFAULT_TITLE,
        defaultContent: String = "",
        inputLabel: String = DEFAULT_INPUT_LABEL,
        onSubmit: PaperDialogTextCallback,
    ): Dialog {
        return create(paperDialogTextComponent(title), defaultContent, inputLabel, onSubmit)
    }

    /**
     * 创建文本输入对话框，但不立即打开。
     *
     * @param title 对话框标题组件。
     * @param defaultContent 输入框默认内容。
     * @param inputLabel 输入框上方显示的名称。
     * @param onSubmit 玩家点击确认按钮后的文本回调。
     */
    fun create(
        title: ComponentLike,
        defaultContent: String = "",
        inputLabel: String = DEFAULT_INPUT_LABEL,
        onSubmit: PaperDialogTextCallback,
    ): Dialog {
        return paperDialog(title) {
            closeAfterAction()
            inputs {
                text(
                    key = TEXT_INPUT_KEY,
                    label = inputLabel,
                    initial = defaultContent,
                )
            }
            confirmation(
                yesLabel = SUBMIT_LABEL,
                noLabel = CANCEL_LABEL,
                yes = {
                    callback { audience ->
                        val player = audience as? Player ?: return@callback
                        player.onSubmit(text(TEXT_INPUT_KEY).orEmpty())
                    }
                },
                no = {
                    callback { }
                },
            )
        }
    }

    /**
     * 创建文本输入对话框并立即为玩家打开。
     *
     * @param player 目标玩家。
     * @param title 对话框标题。
     * @param defaultContent 输入框默认内容。
     * @param inputLabel 输入框上方显示的名称。
     * @param onSubmit 玩家点击确认按钮后的文本回调。
     */
    fun open(
        player: Player,
        title: String = DEFAULT_TITLE,
        defaultContent: String = "",
        inputLabel: String = DEFAULT_INPUT_LABEL,
        onSubmit: PaperDialogTextCallback,
    ): Dialog {
        return player.openPaperDialogTextUI(title, defaultContent, inputLabel, onSubmit)
    }

    /**
     * 创建文本输入对话框并立即为玩家打开。
     *
     * @param player 目标玩家。
     * @param title 对话框标题组件。
     * @param defaultContent 输入框默认内容。
     * @param inputLabel 输入框上方显示的名称。
     * @param onSubmit 玩家点击确认按钮后的文本回调。
     */
    fun open(
        player: Player,
        title: ComponentLike,
        defaultContent: String = "",
        inputLabel: String = DEFAULT_INPUT_LABEL,
        onSubmit: PaperDialogTextCallback,
    ): Dialog {
        return player.openPaperDialogTextUI(title, defaultContent, inputLabel, onSubmit)
    }
}

/**
 * 为当前玩家打开文本输入对话框。
 *
 * @param title 对话框标题。
 * @param defaultContent 输入框默认内容。
 * @param inputLabel 输入框上方显示的名称。
 * @param onSubmit 玩家点击确认按钮后的文本回调。
 */
fun Player.openPaperDialogTextUI(
    title: String = DEFAULT_TITLE,
    defaultContent: String = "",
    inputLabel: String = DEFAULT_INPUT_LABEL,
    onSubmit: PaperDialogTextCallback,
): Dialog {
    return openPaperDialogTextUI(paperDialogTextComponent(title), defaultContent, inputLabel, onSubmit)
}

/**
 * 为当前玩家打开文本输入对话框。
 *
 * @param title 对话框标题组件。
 * @param defaultContent 输入框默认内容。
 * @param inputLabel 输入框上方显示的名称。
 * @param onSubmit 玩家点击确认按钮后的文本回调。
 */
fun Player.openPaperDialogTextUI(
    title: ComponentLike,
    defaultContent: String = "",
    inputLabel: String = DEFAULT_INPUT_LABEL,
    onSubmit: PaperDialogTextCallback,
): Dialog {
    val dialog = PaperDialogTextUI.create(title, defaultContent, inputLabel, onSubmit)
    showDialog(dialog)
    return dialog
}

/**
 * [openPaperDialogTextUI] 的简写形式。
 *
 * @param title 对话框标题。
 * @param defaultContent 输入框默认内容。
 * @param inputLabel 输入框上方显示的名称。
 * @param onSubmit 玩家点击确认按钮后的文本回调。
 */
fun Player.PaperDialogTextUI(
    title: String = DEFAULT_TITLE,
    defaultContent: String = "",
    inputLabel: String = DEFAULT_INPUT_LABEL,
    onSubmit: PaperDialogTextCallback,
): Dialog {
    return openPaperDialogTextUI(title, defaultContent, inputLabel, onSubmit)
}

/**
 * [openPaperDialogTextUI] 的简写形式。
 *
 * @param title 对话框标题组件。
 * @param defaultContent 输入框默认内容。
 * @param inputLabel 输入框上方显示的名称。
 * @param onSubmit 玩家点击确认按钮后的文本回调。
 */
fun Player.PaperDialogTextUI(
    title: ComponentLike,
    defaultContent: String = "",
    inputLabel: String = DEFAULT_INPUT_LABEL,
    onSubmit: PaperDialogTextCallback,
): Dialog {
    return openPaperDialogTextUI(title, defaultContent, inputLabel, onSubmit)
}
