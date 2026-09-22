package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "禁用日志写入的线程",
    "禁用QQ日志写入线程并拦截日志输出，减少磁盘占用",
    HookCategory.PURIFY
)
object DisableLogWriteThread : BaseSwitchHookItem() {

    private const val LOG_ITEM_MANAGER = "com.tencent.qphone.base.util.QLogItemManager\$WriteHandler"
    private const val Q_LOG = "com.tencent.qphone.base.util.QLog"

    /** QStory: QLog 的 w/i/d/q 等级输出方法 */
    private val logLevelNames = listOf("w", "i", "d", "q")

    override fun onInit() = HostInfo.isQQ && LOG_ITEM_MANAGER.clazz != null

    override fun onHook() {
        // 拦截日志写入线程的初始化（QStory 同款：tryInit 被拦截后同步屏蔽 QLog 输出）
        LOG_ITEM_MANAGER.clazz
            ?.findMethodOrNull {
                name = "tryInit"
            }
            ?.hookBefore(this) { param ->
                hookQLogMethods()
                param.result = null
            }
            ?: run { hookQLogMethods() }
    }

    private fun hookQLogMethods() {
        val logClass = Q_LOG.clazz ?: return
        logLevelNames.forEach { level ->
            logClass.findMethods {
                name = level
                returnType = void
            }.forEach { it.hookBefore(this) { p -> p.result = null } }
        }
    }
}
