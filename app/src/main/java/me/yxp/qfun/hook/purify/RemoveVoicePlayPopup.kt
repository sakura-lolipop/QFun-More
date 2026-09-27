package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation(
    "移除语音播放弹窗",
    "移除语音播放时触发的听筒音量弹窗",
    HookCategory.PURIFY
)
object RemoveVoicePlayPopup : BaseSwitchHookItem(), DexKitTask {

    private const val LOW_VOLUME_LISTENER =
        "com.tencent.mobileqq.aio.helper.PttHelper\$getPhoneLowVolumeListener\$onLowVolume\$1"

    private var lowVolumeListenerClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        lowVolumeListenerClass =
            runCatching { requireClass("lowVolumeListener") }.getOrNull() ?: LOW_VOLUME_LISTENER.clazz
        return lowVolumeListenerClass != null
    }

    override fun onHook() {
        // QQ 9.3.15 实测签名：invoke(Z)V（Kotlin Function1 桥式 invoke(Object) 之外的原始重载）
        lowVolumeListenerClass
            ?.findMethod {
                name = "invoke"
                returnType = void
                paramTypes(boolean)
            }
            ?.doNothing(this)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        // 无字符串常量的 lambda 类：包 + Lambda 父类 + invoke(Z)V 结构定位
        // （9.3.15 双包验证唯一）
        "lowVolumeListener" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.helper")
            matcher {
                superClass("kotlin.jvm.internal.Lambda")
                methods {
                    add {
                        name("invoke")
                        paramTypes("boolean")
                        returnType("void")
                    }
                }
            }
        }
    )
}
