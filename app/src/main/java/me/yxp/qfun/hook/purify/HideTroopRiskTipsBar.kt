package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "隐藏群聊风险提醒",
    "移除群聊顶部的风险提醒横幅",
    HookCategory.PURIFY
)
object HideTroopRiskTipsBar : BaseSwitchHookItem() {

    // QStory: 在 com.tencent.mobileqq.troop.tipsbar 包下按类名包含 TroopSecurityTipsBar 动态定位
    private val candidateClasses = listOf(
        "com.tencent.mobileqq.troop.tipsbar.TroopSecurityTipsBar",
        "com.tencent.mobileqq.troop.tipsbar.a.TroopSecurityTipsBar",
        "com.tencent.mobileqq.troop.tipsbar.b.TroopSecurityTipsBar"
    )

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        return candidateClass() != null
    }

    override fun onHook() {
        candidateClass()
            ?.findMethods {
                name = "doOnCreate"
                returnType = void
            }
            ?.forEach { it.doNothing(this) }
    }

    private fun candidateClass() = candidateClasses.firstNotNullOfOrNull { it.clazz }
}
