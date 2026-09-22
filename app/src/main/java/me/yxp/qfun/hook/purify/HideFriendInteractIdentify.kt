package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod

@HookItemAnnotation(
    "隐藏好友互动标识",
    "隐藏资料卡下方的好友互动标识区域",
    HookCategory.PURIFY
)
object HideFriendInteractIdentify : BaseSwitchHookItem() {

    private const val INTIMATE_COMPONENT =
        "com.tencent.mobileqq.profilecard.component.ProfileIntimateComponent"

    override fun onInit() = HostInfo.isQQ && INTIMATE_COMPONENT.clazz != null

    override fun onHook() {
        // QStory: 拦截 initComponentViewContainer，阻止互动标识视图容器创建
        INTIMATE_COMPONENT.clazz
            ?.findMethod {
                name = "initComponentViewContainer"
                returnType = void
            }
            ?.doNothing(this)
    }
}
