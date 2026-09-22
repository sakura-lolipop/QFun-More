package me.yxp.qfun.hook.purify

import android.content.Context
import android.content.Intent
import android.net.Uri
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "去你大爷的QQ浏览器",
    "链接不再跳转QQ浏览器，使用系统浏览器打开（致敬 经典模块:去你大爷的QQ浏览器）",
    HookCategory.PURIFY
)
object FuckQQBrowser : BaseSwitchHookItem() {

    /** QStory 白名单：腾讯系域名的跳转保持原行为 */
    private val whitelistHosts = listOf("qq.com", "tenpay.com", "meeting.tencent.com", "qq-web.cdn-go.cn")

    private const val FLAG_FROM_FQB = "from_fqb"

    override fun onInit() = HostInfo.isQQ

    override fun onHook() {
        listOf("android.content.ContextWrapper", "android.app.Activity")
            .mapNotNull { it.clazz }
            .forEach { clazz ->
                listOf("startActivity", "startActivityForResult").forEach { name ->
                    clazz.findMethods {
                        this.name = name
                    }.forEach { method ->
                        method.hookAfter(this) { param ->
                            val intent = param.args.firstOrNull() as? Intent ?: return@hookAfter
                            if (intent.getBooleanExtra(FLAG_FROM_FQB, false)) return@hookAfter

                            val componentClass = intent.component?.className ?: return@hookAfter
                            if (!componentClass.contains("QQBrowserActivity", ignoreCase = true)) {
                                return@hookAfter
                            }

                            var url = intent.data?.toString() ?: return@hookAfter
                            val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
                            if (host != null && whitelistHosts.any { host == it || host.endsWith(".$it") }) {
                                return@hookAfter
                            }

                            if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
                                url = "http://$url"
                            }

                            val context = param.thisObject as? Context ?: return@hookAfter
                            val newIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(FLAG_FROM_FQB, true)
                            runCatching { context.startActivity(newIntent) }
                            param.result = null
                        }
                    }
                }
            }
    }
}
