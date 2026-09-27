package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "隐藏好友互动标识",
    "隐藏资料卡下方的好友互动标识区域",
    HookCategory.PURIFY
)
object HideFriendInteractIdentify : BaseSwitchHookItem(), DexKitTask {

    private const val INTIMATE_COMPONENT =
        "com.tencent.mobileqq.profilecard.component.ProfileIntimateComponent"

    private var intimateComponentClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        intimateComponentClass =
            runCatching { requireClass("intimateComponent") }.getOrNull() ?: INTIMATE_COMPONENT.clazz
        return intimateComponentClass != null
    }

    override fun onHook() {
        // QStory: 拦截 initComponentViewContainer，阻止互动标识视图容器创建
        intimateComponentClass
            ?.findMethod {
                name = "initComponentViewContainer"
                returnType = void
            }
            ?.doNothing(this)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "intimateComponent" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.profilecard")
            matcher {
                usingStrings(listOf("makeOrRefreshUI fail, card is null"), matchType = StringMatchType.Equals)
            }
        }
    )
}
