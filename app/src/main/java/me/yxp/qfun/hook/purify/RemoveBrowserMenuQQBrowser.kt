package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.findMethodOrNull
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation(
    "移除浏览器菜单的QQ浏览器",
    "移除内置浏览器分享菜单中的\"用QQ浏览器打开\"选项",
    HookCategory.PURIFY
)
object RemoveBrowserMenuQQBrowser : BaseSwitchHookItem(), DexKitTask {

    private const val SHARE_MENU_HANDLER =
        "com.tencent.mobileqq.webview.swift.component.SwiftBrowserShareMenuHandler"

    private var shareMenuHandlerClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        shareMenuHandlerClass =
            runCatching { requireClass("shareMenuHandler") }.getOrNull() ?: SHARE_MENU_HANDLER.clazz
        return shareMenuHandlerClass != null
    }

    override fun onHook() {
        val handlerClass = shareMenuHandlerClass ?: return
        // QStory: 定位 public void xxx(Class, long) —— 触发"用QQ浏览器打开"的入口方法
        val target = handlerClass.findMethodOrNull {
            returnType = void
            visibility = public
            paramCount = 2
            paramTypes(obj, long)
        } ?: handlerClass.findMethod {
            returnType = void
            visibility = public
            paramCount = 2
        }
        target.doNothing(this)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "shareMenuHandler" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.webview.swift")
            matcher {
                // 菜单项自身的 jsbridge 标识（9.3.15 双包全 dex 验证唯一）
                usingStrings("menuItem:openWithQQBrowser")
            }
        }
    )
}
