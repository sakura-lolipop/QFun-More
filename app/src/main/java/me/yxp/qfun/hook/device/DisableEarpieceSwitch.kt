package me.yxp.qfun.hook.device

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import me.yxp.qfun.utils.reflect.toClass

@HookItemAnnotation(
    "禁用听语音时听筒来回自动切换",
    "听语音时距离传感器不再自动切换听筒/扬声器，手动切换不受影响",
    HookCategory.DEVICE
)
object DisableEarpieceSwitch : BaseSwitchHookItem() {

    private const val OLD_HELPER =
        "com.tencent.qqnt.audio.play.player.AudioPlayerDeviceHelper"
    private const val OLD_METHOD = "onNearToEarStatusChanged"

    private const val NEW_IMPL =
        "com.tencent.mobileqq.qqaudio.audioplayer.impl.AudioDeviceServiceImpl"
    private const val NEW_METHOD = "notifyAllDeviceStatusChanged"

    override fun onInit(): Boolean = OLD_HELPER.clazz != null || NEW_IMPL.clazz != null

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
        val clazz = NEW_IMPL.clazz ?: return false
        val method = clazz.findMethodOrNull {
            name = NEW_METHOD
            paramTypes(int, boolean)
        } ?: return false
        method.hookBefore(this) { param -> param.result = null }
        return true
    }
}
