package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation(
    "屏蔽QQ秀",
    "屏蔽新版QQ秀头像",
    HookCategory.PURIFY
)
object RemoveQQShow : BaseSwitchHookItem(), DexKitTask {

    override fun onInit() = HostInfo.versionCode >= 11820

    override fun onHook() {

        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码（保留 toClass 缺类抛错语义）
        (runCatching { requireClass("aiAvatarSwitchApi") }.getOrNull()
            ?: "com.tencent.mobileqq.ai.avatar.api.impl.AIAvatarSwitchApiImpl".toClass)
            .getDeclaredMethod(
                "hasAvatarUrlOrInfo",
                Long::class.java
            )
            .hookAfter(this) {
                if (it.args[0] != QQCurrentEnv.currentUin.toLong()) it.result = false
            }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "aiAvatarSwitchApi" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.ai.avatar")
            matcher {
                usingStrings("[isQQShowEnable] uin==")
            }
        }
    )
}
