package me.yxp.qfun.hook.chat

import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.api.SendMsgListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem

@HookItemAnnotation(
    "发送消息格式化",
    "发送时自动在中英文/数字之间添加空格（pangu 风格排版）",
    HookCategory.CHAT
)
object FormatSendMsg : BaseSwitchHookItem(), SendMsgListener {

    private const val CJK = "\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff"
    private val cjkToWord = Regex("([$CJK])([A-Za-z0-9])")
    private val wordToCjk = Regex("([A-Za-z0-9])([$CJK])")

    override fun onSend(elements: ArrayList<MsgElement>) {
        elements.forEach { element ->
            val textElement = element.textElement ?: return@forEach
            if (textElement.atType != 0) return@forEach

            val content = textElement.content
            if (content.isEmpty()) return@forEach

            val formatted = format(content)
            if (formatted != content) {
                textElement.content = formatted
            }
        }
    }

    private fun format(content: String): String = content
        .replace(cjkToWord, "$1 $2")
        .replace(wordToCjk, "$1 $2")
}
