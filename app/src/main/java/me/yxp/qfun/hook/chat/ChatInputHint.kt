package me.yxp.qfun.hook.chat

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
                editText.hint = config.hintText.ifEmpty { null }
            }
        }
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
