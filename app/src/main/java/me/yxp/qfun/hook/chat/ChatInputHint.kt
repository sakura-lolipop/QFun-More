package me.yxp.qfun.hook.chat

import android.view.View
import android.view.ViewGroup
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
import me.yxp.qfun.hook.api.AIOViewUpdateListener
import me.yxp.qfun.hook.base.BaseClickableHookItem
import me.yxp.qfun.ui.components.atoms.DialogTextField
import me.yxp.qfun.ui.components.dialogs.CenterDialogContainer
import me.yxp.qfun.ui.core.compatibility.QFunCenterDialog
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.QQCurrentEnv
import java.util.concurrent.atomic.AtomicBoolean

@HookItemAnnotation(
    "输入框增加提示",
    "在聊天输入框显示自定义提示文字，配置留空则不显示",
    HookCategory.CHAT
)
object ChatInputHint : BaseClickableHookItem<ChatHintConfig>(ChatHintConfig.serializer()), AIOViewUpdateListener {

    override val defaultConfig: ChatHintConfig = ChatHintConfig()

    private val ctorLogged = AtomicBoolean(false)
    private val scanLogged = AtomicBoolean(false)

    private fun applyHint(editText: EditText) {
        val hint = config.hintText.ifEmpty { null } ?: return
        if (editText.hint != hint) editText.hint = hint
    }

    override fun onHook() {
        AIOEditText::class.java.declaredConstructors.forEach { constructor ->
            constructor.hookAfter(this) { param ->
                if (ctorLogged.compareAndSet(false, true)) {
                    LogUtils.d("ChatInputHint: AIOEditText 构造器触发 ${param.thisObject.javaClass.name}")
                }
                (param.thisObject as? EditText)?.let(::applyHint)
            }
        }
    }

    override fun onUpdate(frameLayout: android.widget.FrameLayout, msgRecord: com.tencent.qqnt.kernel.nativeinterface.MsgRecord) {
        if (config.hintText.isEmpty()) return

        // 自诊断 + 重设：遍历聊天窗口 View 树，把 hint 打到所有 EditText 上。
        // 若功能不生效，看日志 "EditText scan" 一行：n=0 即输入框是 Compose 实现，需换占位符供应商方案
        val root = frameLayout.rootView
        val found = StringBuilder()
        var count = 0
        collectEditTexts(root) { et ->
            count++
            if (found.isNotEmpty()) found.append(',')
            found.append(et.javaClass.simpleName.ifEmpty { et.javaClass.name })
            applyHint(et)
        }
        if (scanLogged.compareAndSet(false, true)) {
            LogUtils.d("ChatInputHint: EditText scan n=$count [$found]")
        }
    }

    private fun collectEditTexts(view: View, sink: (EditText) -> Unit) {
        if (view is EditText) {
            sink(view)
            return
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectEditTexts(view.getChildAt(i), sink)
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
