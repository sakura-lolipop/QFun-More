package me.yxp.qfun.hook.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.api.MenuClickListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.plugin.bean.MsgData
import me.yxp.qfun.ui.components.atoms.DialogTextField
import me.yxp.qfun.ui.components.dialogs.CenterDialogContainer
import me.yxp.qfun.ui.core.compatibility.QFunCenterDialog
import me.yxp.qfun.utils.qq.MsgTool
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.qq.Toasts

@HookItemAnnotation(
    "复制/发送卡片",
    "长按卡片消息可复制卡片JSON，修改后可重新发送到当前会话",
    HookCategory.CHAT
)
object CopySendCard : BaseSwitchHookItem(), MenuClickListener {

    override val menuKey: String = "[QFun],$name,复制/发送卡片,,11,4"

    override fun onClick(msgData: MsgData) {
        val msgRecord = msgData.data
        val cardJson = msgRecord.elements
            .firstOrNull { it.arkElement != null }
            ?.arkElement?.bytesData

        if (cardJson.isNullOrEmpty()) {
            Toasts.toast("未找到卡片数据")
            return
        }
        showCardDialog(msgRecord, cardJson)
    }

    private fun showCardDialog(msgRecord: MsgRecord, cardJson: String) {
        val activity = QQCurrentEnv.activity ?: return

        QFunCenterDialog(activity) { dismiss ->
            var cardText by remember { mutableStateOf(cardJson) }

            CenterDialogContainer(
                title = "卡片JSON",
                onDismiss = {
                    copyToClipboard(activity, cardText)
                    Toasts.toast("已复制")
                    dismiss()
                },
                onConfirm = {
                    runCatching {
                        MsgTool.sendCard(
                            msgRecord.peerUin.toString(),
                            cardText,
                            msgRecord.chatType
                        )
                        Toasts.qqToast(2, "已发送")
                    }.onFailure {
                        Toasts.toast("发送失败，请检查JSON格式")
                    }
                    dismiss()
                },
                confirmText = "发送",
                dismissText = "复制"
            ) {
                DialogTextField(
                    value = cardText,
                    onValueChange = { cardText = it },
                    label = "卡片JSON",
                    singleLine = false
                )
            }
        }.show()
    }

    private fun copyToClipboard(context: Context, text: String) {
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("ark", text))
        }
    }
}
