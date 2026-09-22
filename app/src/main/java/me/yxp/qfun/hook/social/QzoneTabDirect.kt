package me.yxp.qfun.hook.social

import android.content.Context
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import java.lang.reflect.Method

@HookItemAnnotation(
    "底栏直接打开空间动态",
    "点击底栏动态Tab时直接显示空间动态（QQ 9.x 实测可能静默无效，取决于 QQ 是否还询问该闸门）",
    HookCategory.SOCIAL
)
object QzoneTabDirect : BaseSwitchHookItem() {

    private const val QZONE_API_PROXY = "com.tencent.qzonehub.api.impl.QZoneApiProxyImpl"
    private const val APP_RUNTIME = "mqq.app.AppRuntime"
    private const val MIN_VERSION_CODE = 12290

    private var target: Method? = null

    override fun onInit(): Boolean {
        if (HostInfo.isQQ && HostInfo.versionCode < MIN_VERSION_CODE) return false
        val proxy = QZONE_API_PROXY.clazz ?: return false
        val runtime = APP_RUNTIME.clazz ?: return false
        target = proxy.declaredMethods.firstOrNull {
            it.name == "needShowQzoneFrame" && it.parameterCount == 2 &&
                Context::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                it.parameterTypes[1].isAssignableFrom(runtime)
        }
        if (target == null) {
            // 9.3.15 上 QStory 同样静默失效：类或方法可能已改名/搬家，或 QQ 不再询问此闸门。
            // DexKit 兜底与"聚合页二次跳转"增强见蓝图 qfunory/blueprints/B1B4_空间全家桶.md §2.3
            LogUtils.d("$name: needShowQzoneFrame(Context, AppRuntime) 未找到")
        }
        return target != null
    }

    override fun onHook() {
        target?.hookBefore(this) { param ->
            param.result = true
        }
    }
}
