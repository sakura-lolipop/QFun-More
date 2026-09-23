package me.yxp.qfun.hook.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.api.MenuClickListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.plugin.bean.MsgData
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.Toasts

@HookItemAnnotation(
    "复制图片链接",
    "图片消息长按菜单出现复制图链项，复制后可直接下载或打开",
    HookCategory.CHAT
)
object CopyPicLink : BaseSwitchHookItem(), MenuClickListener {

    override val menuKey: String get() = "[QFun],$name,复制图链,,"

    override fun accept(msgData: MsgData): Boolean =
        msgData.data.elements.any { it.picElement != null }

    override fun onClick(msgData: MsgData) {

        val links = ChatMediaHelper.picPairs(msgData).map { it.second }
        if (links.isEmpty()) return

        val text = links.joinToString("\n")

        val cm = HostInfo.hostContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(null, text))

        Toasts.toast("已复制到剪切板:$text")
    }
}
