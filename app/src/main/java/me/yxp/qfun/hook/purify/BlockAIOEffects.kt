package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.findMethods
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "屏蔽AIO动画特效",
    "屏蔽聊天界面中的关键词彩蛋、表情连击、超级表情等全屏动画",
    HookCategory.PURIFY
)
object BlockAIOEffects : BaseSwitchHookItem(), DexKitTask {

    override fun onInit() = HostInfo.isQQ

    override fun onHook() {
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码（保留 toClass 缺类抛错语义）
        (runCatching { requireClass("aioAnimationContainer") }.getOrNull()
            ?: "com.tencent.mobileqq.aio.animation.AIOAnimationContainer".toClass)
            .findMethods {
                returnType = boolean
                paramTypes(int, int, objArr)
            }
            .forEach { it.returnConstant(this, true) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "aioAnimationContainer" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.animation")
            matcher {
                usingStrings(listOf("[concurrentStartAnimation] create new animator, type: "), matchType = StringMatchType.Equals)
            }
        }
    )
}
