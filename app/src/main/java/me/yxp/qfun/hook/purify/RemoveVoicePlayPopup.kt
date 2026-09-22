package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod

@HookItemAnnotation(
    "移除语音播放弹窗",
    "移除语音播放时触发的听筒音量弹窗",
    HookCategory.PURIFY
)
object RemoveVoicePlayPopup : BaseSwitchHookItem() {

    private const val LOW_VOLUME_LISTENER =
        "com.tencent.mobileqq.aio.helper.PttHelper\$getPhoneLowVolumeListener\$onLowVolume\$1"

    override fun onInit() = HostInfo.isQQ && LOW_VOLUME_LISTENER.clazz != null

    override fun onHook() {
        // QQ 9.3.15 实测签名：invoke(Z)V（Kotlin Function1 桥式 invoke(Object) 之外的原始重载）
        LOW_VOLUME_LISTENER.clazz
            ?.findMethod {
                name = "invoke"
                returnType = void
                paramTypes(boolean)
            }
            ?.doNothing(this)
    }
}
