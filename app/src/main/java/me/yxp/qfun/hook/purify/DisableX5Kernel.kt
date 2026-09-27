package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod

@HookItemAnnotation(
    "禁用X5内核",
    "强制QQ内置浏览器使用系统原生WebView，不加载腾讯X5内核",
    HookCategory.PURIFY
)
object DisableX5Kernel : BaseSwitchHookItem() {

    // DEXKIT-EXEMPT: X5 SDK（com.tencent.smtt）公开 API 类，非 QQ 混淆业务类，
    // SDK 命名空间跨版本稳定，无升级失效风险，迁移无收益
    private const val QB_SDK = "com.tencent.smtt.sdk.QbSdk"

    override fun onInit() = HostInfo.isQQ && QB_SDK.clazz != null

    override fun onHook() {
        QB_SDK.clazz
            ?.findMethod {
                name = "getIsSysWebViewForcedByOuter"
                returnType = boolean
            }
            ?.returnConstant(this, true)
    }
}
