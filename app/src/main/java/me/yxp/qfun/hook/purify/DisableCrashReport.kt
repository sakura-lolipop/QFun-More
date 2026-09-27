package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "禁用崩溃日志上报",
    "拦截QQ崩溃日志管理器的上报方法，阻止崩溃数据上报",
    HookCategory.PURIFY
)
object DisableCrashReport : BaseSwitchHookItem(), DexKitTask {

    private const val CRASH_MANAGER = "com.tencent.qqperf.monitor.crash.QQCrashReportManager"

    private var crashManagerClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        crashManagerClass =
            runCatching { requireClass("crashManager") }.getOrNull() ?: CRASH_MANAGER.clazz
        return crashManagerClass != null
    }

    override fun onHook() {
        // QStory: hook 全部 public 非静态、void 返回、参数不超过 2 个的方法
        crashManagerClass
            ?.findMethods {
                returnType = void
                isStatic = false
                visibility = public
                paramCount = 2
            }
            ?.forEach { it.doNothing(this) }

        crashManagerClass
            ?.findMethods {
                returnType = void
                isStatic = false
                visibility = public
                paramCount = 1
            }
            ?.forEach { it.doNothing(this) }

        crashManagerClass
            ?.findMethods {
                returnType = void
                isStatic = false
                visibility = public
                paramCount = 0
            }
            ?.forEach { it.doNothing(this) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "crashManager" to FindClass().apply {
            searchPackages("com.tencent.qqperf.monitor.crash")
            matcher {
                usingStrings(listOf("CrashReport init finished."), matchType = StringMatchType.Equals)
            }
        }
    )
}
