package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "屏蔽QQToast",
    "屏蔽QQ弹出的Toast提示",
    HookCategory.PURIFY
)
object BlockQQToast : BaseSwitchHookItem() {

    private const val QQ_TOAST = "com.tencent.mobileqq.widget.QQToast"

    override fun onInit() = HostInfo.isQQ && QQ_TOAST.clazz != null

    override fun onHook() {
        QQ_TOAST.clazz
            ?.findMethods {
                name = "show"
                returnType = boolean
            }
            ?.forEach { it.returnConstant(this, false) }
    }
}
