package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.findMethodOrNull

@HookItemAnnotation(
    "移除浏览器菜单的QQ浏览器",
    "移除内置浏览器分享菜单中的\"用QQ浏览器打开\"选项",
    HookCategory.PURIFY
)
object RemoveBrowserMenuQQBrowser : BaseSwitchHookItem() {

    private const val SHARE_MENU_HANDLER =
        "com.tencent.mobileqq.webview.swift.component.SwiftBrowserShareMenuHandler"

    override fun onInit() = HostInfo.isQQ && SHARE_MENU_HANDLER.clazz != null

    override fun onHook() {
        val handlerClass = SHARE_MENU_HANDLER.clazz ?: return
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
}
