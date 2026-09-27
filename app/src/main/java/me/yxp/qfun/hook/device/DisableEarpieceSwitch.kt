package me.yxp.qfun.hook.device

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "禁用听语音时听筒来回自动切换",
    "听语音时距离传感器不再自动切换听筒/扬声器，手动切换不受影响",
    HookCategory.DEVICE
)
object DisableEarpieceSwitch : BaseSwitchHookItem(), DexKitTask {

    // DEXKIT-EXEMPT: 旧版 QQ 专用回退分支，9.3.15 包内不存在该类，锚点无法静态验证
    private const val OLD_HELPER =
        "com.tencent.qqnt.audio.play.player.AudioPlayerDeviceHelper"
    private const val OLD_METHOD = "onNearToEarStatusChanged"

    private const val NEW_METHOD = "notifyAllDeviceStatusChanged"

    // DexKit 锚点失效时的硬编码回退（9.3.15 实际走这条或 DexKit 之一）
    private const val NEW_IMPL =
        "com.tencent.mobileqq.qqaudio.audioplayer.impl.AudioDeviceServiceImpl"

    private var newImpl: Class<*>? = null

    override fun onInit(): Boolean {
        newImpl = runCatching { requireClass("newImpl") }.getOrNull() ?: NEW_IMPL.clazz
        return OLD_HELPER.clazz != null || newImpl != null
    }

    override fun onHook() {
        if (!hookOld()) hookNew()
    }

    private fun hookOld(): Boolean {
        val clazz = OLD_HELPER.clazz ?: return false
        val method = clazz.findMethodOrNull {
            name = OLD_METHOD
            paramTypes(int)
        } ?: return false
        method.hookBefore(this) { param -> param.result = null }
        return true
    }

    private fun hookNew(): Boolean {
        val clazz = newImpl ?: return false
        val method = clazz.findMethodOrNull {
            name = NEW_METHOD
            paramTypes(int, boolean)
        } ?: return false
        method.hookBefore(this) { param -> param.result = null }
        return true
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "newImpl" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.qqaudio.audioplayer.impl")
            matcher {
                usingStrings(listOf("notifyAllDeviceStatusChanged: audioDeviceListener="), matchType = StringMatchType.Equals)
            }
        }
    )
}
