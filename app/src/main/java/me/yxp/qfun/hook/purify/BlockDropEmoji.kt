package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "屏蔽掉落小表情",
    "屏蔽一些关键词触发的掉落小表情，重启生效",
    HookCategory.PURIFY
)
object BlockDropEmoji : BaseSwitchHookItem() {

    private val configHelpers = listOf(
        "com.tencent.mobileqq.aio.animation.util.AioAnimationConfigHelper",
        "com.tencent.mobileqq.activity.aio.anim.AioAnimationConfigHelper"
    )

    /** QStory: 拦截动画规则解析，使掉落小表情规则加载为空 */
    private val ruleMethods = listOf(
        "doParseRules",
        "parseRules",
        "parseAnimationRules",
        "loadRules"
    )

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        return configHelpers.any { it.clazz != null }
    }

    override fun onHook() {
        configHelpers.forEach { name ->
            val helperClass = name.clazz ?: return@forEach
            ruleMethods.forEach { method ->
                helperClass.findMethods {
                    this.name = method
                }.forEach { it.doNothing(this) }
            }
        }
    }
}
