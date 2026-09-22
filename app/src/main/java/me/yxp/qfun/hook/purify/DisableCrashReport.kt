package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "禁用崩溃日志上报",
    "拦截QQ崩溃日志管理器的上报方法，阻止崩溃数据上报",
    HookCategory.PURIFY
)
object DisableCrashReport : BaseSwitchHookItem() {

    private const val CRASH_MANAGER = "com.tencent.qqperf.monitor.crash.QQCrashReportManager"

    override fun onInit() = HostInfo.isQQ && CRASH_MANAGER.clazz != null

    override fun onHook() {
        // QStory: hook 全部 public 非静态、void 返回、参数不超过 2 个的方法
        CRASH_MANAGER.clazz
            ?.findMethods {
                returnType = void
                isStatic = false
                visibility = public
                paramCount = 2
            }
            ?.forEach { it.doNothing(this) }

        CRASH_MANAGER.clazz
            ?.findMethods {
                returnType = void
                isStatic = false
                visibility = public
                paramCount = 1
            }
            ?.forEach { it.doNothing(this) }

        CRASH_MANAGER.clazz
            ?.findMethods {
                returnType = void
                isStatic = false
                visibility = public
                paramCount = 0
            }
            ?.forEach { it.doNothing(this) }
    }
}
