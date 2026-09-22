package me.yxp.qfun.hook.notice

import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.notification.NotificationFacade
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.reflect.findMethod
import mqq.app.AppRuntime
import java.lang.reflect.Method

@HookItemAnnotation(
    "QQ通知优化",
    "补全通知栏标题与内容：标题缺省时显示群名/好友名，正文缺省时拼接发送者与消息摘要",
    HookCategory.OTHER
)
object EnhanceQQNotification : BaseSwitchHookItem() {

    private lateinit var buildTitle: Method
    private lateinit var buildText: Method

    override fun onInit(): Boolean {
        buildTitle = NotificationFacade::class.java.findMethod {
            returnType = string
            paramTypes(AppRuntime::class.java, RecentContactInfo::class.java)
        }
        buildText = NotificationFacade::class.java.findMethod {
            returnType = string
            paramTypes(string, RecentContactInfo::class.java)
        }
        return super.onInit()
    }

    override fun onHook() {
        buildTitle.hookAfter(this) { param ->
            enhanceTitle(param.result as? String, param.args[1] as RecentContactInfo)
                ?.let { param.result = it }
        }
        buildText.hookAfter(this) { param ->
            enhanceText(param.result as? String, param.args[1] as RecentContactInfo)
                ?.let { param.result = it }
        }
    }

    private fun enhanceTitle(title: String?, info: RecentContactInfo): String? {
        if (!title.isNullOrBlank()) return null
        return when (info.chatType) {
            1 -> info.peerName.ifBlank { info.peerUin.toString() }
            2 -> info.peerName.ifBlank { "群聊 ${info.peerUin}" }
            else -> null
        }.takeIf { !it.isNullOrBlank() }
    }

    private fun enhanceText(text: String?, info: RecentContactInfo): String? {
        if (!text.isNullOrBlank()) return null
        val summary = info.abstractContent
            ?.filterIsInstance<String>()
            ?.joinToString(" ")
            ?.trim()
            .orEmpty()
        if (summary.isBlank()) return null
        return if (info.chatType == 2) {
            val sender = info.sendNickName.ifBlank {
                info.sendMemberName.ifBlank { info.senderUin.toString() }
            }
            "$sender: $summary"
        } else {
            summary
        }
    }
}
