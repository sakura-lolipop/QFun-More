package me.yxp.qfun.hook.chat

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.mobileqq.aio.input.edit.AIOEditText
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.conf.ChatHintConfig
import me.yxp.qfun.hook.base.BaseClickableHookItem
import me.yxp.qfun.ui.components.atoms.DialogTextField
import me.yxp.qfun.ui.components.dialogs.CenterDialogContainer
import me.yxp.qfun.ui.core.compatibility.QFunCenterDialog
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.QQCurrentEnv

/**
 * 输入框增加提示（仅聊天输入框）。
 *
 * 作用域防护：AIOEditText 同时被聊天输入区和消息页搜索条复用（实测泄漏），
 * 因此构造时只挂 attach 监听，挂树瞬间检查祖先链 —— 仅当视图位于
 * com.tencent.mobileqq.aio.* 输入面板内才设置提示，消息页搜索条（chats.* 层级）不命中。
 */
@HookItemAnnotation(
    "输入框增加提示",
    "在聊天输入框显示自定义提示文字，配置留空则不显示",
    HookCategory.CHAT
)
object ChatInputHint : BaseClickableHookItem<ChatHintConfig>(ChatHintConfig.serializer()) {

    override val defaultConfig: ChatHintConfig = ChatHintConfig()

    override fun onHook() {
        AIOEditText::class.java.declaredConstructors.forEach { constructor ->
            constructor.hookAfter(this) { param ->
                val editText = param.thisObject as? EditText ?: return@hookAfter
                editText.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(av: View) {
                        if (!hasAioInputAncestor(av)) return
                        (av as? EditText)?.let(::applyHint)
                    }

                    override fun onViewDetachedFromWindow(av: View) {}
                })
            }
        }
    }

    private fun applyHint(editText: EditText) {
        val hint = config.hintText.ifEmpty { null }
        if (editText.hint != hint) editText.hint = hint
    }

    /** 祖先链中是否存在 aio 输入面板（包名前缀 com.tencent.mobileqq.aio.input）。 */
    private fun hasAioInputAncestor(v: View): Boolean {
        var p: ViewParent = v.parent
        while (p is ViewGroup) {
            if (p.javaClass.name.startsWith("com.tencent.mobileqq.aio.input")) return true
            p = p.parent
        }
        return false
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        val activity = QQCurrentEnv.activity ?: return

        QFunCenterDialog(activity) { dismiss ->
            var hintText by remember { mutableStateOf(config.hintText) }

            CenterDialogContainer(
                title = "输入框提示",
                onDismiss = dismiss,
                onConfirm = {
                    updateConfig(ChatHintConfig(hintText = hintText.trim()))
                    dismiss()
                }
            ) {
                DialogTextField(
                    value = hintText,
                    onValueChange = { hintText = it },
                    label = "提示文字",
                    hint = "默认: 在这里输入你想说的内容"
                )
            }
        }.show()
    }
}
