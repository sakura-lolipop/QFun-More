package me.yxp.qfun.hook.patch

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass

@HookItemAnnotation(
    "屏蔽热更新",
    "禁用宿主云控热补丁/热更新",
    HookCategory.OTHER,
    "All"
)
object DisableHotPatch : BaseSwitchHookItem() {

    override val isNeedRestart: Boolean = true

    override fun onHook() {

        // DEXKIT-EXEMPT: 启动关键路径，先于 DexKit 扫描（热补丁框架在应用启动早期即生效，
        // 必须用稳定名第一时间挂上，首次运行缓存为空不能漏屏蔽）
        "com.tencent.rfix.lib.download.PatchDownloadTask".clazz
            ?.getDeclaredMethod("run")
            ?.doNothing(this)

        val configClass = "com.tencent.rfix.lib.config.PatchConfig".toClass
        "com.tencent.rfix.lib.engine.PatchEngineBase".clazz
            ?.findMethod {
                returnType = void
                paramTypes(String::class.java, configClass)
            }
            ?.doNothing(this)

        "com.tencent.mobileqq.msf.core.net.patch.PatchReporter".clazz
            ?.declaredMethods
            ?.filter {
                it.name.startsWith("report") && it.returnType == Void.TYPE
            }
            ?.forEach { it.doNothing(this) }


    }


}