package me.yxp.qfun.hook.device

import android.content.Intent
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.toClass

@HookItemAnnotation(
    "禁用QQ公交卡",
    "禁止QQ在后台干扰NFC",
    HookCategory.DEVICE
)
object DisableBusCard : BaseSwitchHookItem() {

    // DEXKIT-EXEMPT: 9.3.15 双包（base+tinker）已无 cooperation.buscard.BuscardHelper，
    // 公交卡功能下线，onInit 恒 false 静默降级——无锚点可建，保留硬编码
    private const val BUSCARD_HELPER = "cooperation.buscard.BuscardHelper"

    override fun onInit(): Boolean = BUSCARD_HELPER.clazz != null

    override fun onHook() {

        val clazz = BUSCARD_HELPER.toClass

        clazz.declaredMethods.forEach { method ->
            if (method.parameterTypes.any { it == Intent::class.java }) {
                method.hookBefore(this) { param -> param.result = null }
            }
        }
    }
}
