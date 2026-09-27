package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "屏蔽QQToast",
    "屏蔽QQ弹出的Toast提示",
    HookCategory.PURIFY
)
object BlockQQToast : BaseSwitchHookItem(), DexKitTask {

    private const val QQ_TOAST = "com.tencent.mobileqq.widget.QQToast"

    private var toastClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        toastClass = runCatching { requireClass("qqToast") }.getOrNull() ?: QQ_TOAST.clazz
        return toastClass != null
    }

    override fun onHook() {
        toastClass
            ?.findMethods {
                name = "show"
                returnType = boolean
            }
            ?.forEach { it.returnConstant(this, false) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "qqToast" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.widget")
            matcher {
                // QQToast 自己的 MMKV/设置 key（9.3.15 双包全 dex 验证唯一）
                usingStrings(listOf("9225_qqtoast_disable_freewindow_check"), matchType = StringMatchType.Equals)
            }
        }
    )
}
