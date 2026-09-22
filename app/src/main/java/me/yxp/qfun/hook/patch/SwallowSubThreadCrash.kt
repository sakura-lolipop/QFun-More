package me.yxp.qfun.hook.patch

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem

/**
 * 阻止子线程闪退。
 *
 * 本 item 仅承载开关：CrashMonitor.CrashHandler 捕获到非主线程崩溃时读取
 * [isEnable]，开启则记录 swallowed.log 并拦截（进程存活），关闭走原有报告流程。
 */
@HookItemAnnotation(
    "阻止子线程闪退",
    "子线程崩溃时记录日志并拦截，QQ继续运行不再闪退（实验性，崩溃日志仅保留最新的一份）",
    HookCategory.OTHER
)
object SwallowSubThreadCrash : BaseSwitchHookItem() {

    override fun onHook() {
        // 纯开关，无 hook：拦截逻辑在 CrashMonitor.CrashHandler
    }
}
