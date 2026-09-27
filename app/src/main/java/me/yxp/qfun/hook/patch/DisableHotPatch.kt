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

        // DEXKIT-EXEMPT: 保守豁免——本功能与全部 hook 同批在 initCache 成功后加载
        //（首启无缓存时整个模块都不挂 hook，扫描重启后才生效），并无"先于扫描"的时序优势；
        // 但 rfix 框架类是稳定名，锚点化收益趋零，保留硬编码
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