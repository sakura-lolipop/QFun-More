package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "屏蔽弹出动画",
    "屏蔽某些特定文本消息弹出的与游戏相关的烦人动画",
    HookCategory.PURIFY
)
object AntiInteractivePop : BaseSwitchHookItem(), DexKitTask {

    override fun onInit() = HostInfo.isQQ

    override fun onHook() {
        listOf(
            "interactivePopManager" to "com.tencent.mobileqq.springhb.interactive.ui.InteractivePopManager",
            "pagEasterEggPopManager" to "com.tencent.mobileqq.aio.animation.pag.PagEasterEggPopManager"
        ).forEach { (key, fallbackName) ->
            // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
            (runCatching { requireClass(key) }.getOrNull() ?: fallbackName.clazz)?.findMethod {
                returnType = void
                paramTypes(
                    // androidx/kotlin 框架类，非 QQ 业务类，稳定名无需迁移
                    "androidx.fragment.app.Fragment".toClass,
                    null,
                    null,
                    "kotlin.jvm.functions.Function0".toClass
                )
            }?.doNothing(this)
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "interactivePopManager" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.springhb.interactive")
            matcher {
                usingStrings(listOf("showInteractivePop playFile isNull!!"), matchType = StringMatchType.Equals)
            }
        },
        "pagEasterEggPopManager" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.animation.pag")
            matcher {
                usingStrings(listOf("showPop playFile isNull"), matchType = StringMatchType.Equals)
            }
        }
    )
}
