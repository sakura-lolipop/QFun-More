package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "屏蔽临时会话弹窗",
    "在没有开启临时会话的情况下查看已删除好友的聊天记录 则不会有开启临时会话弹窗",
    HookCategory.PURIFY
)
object BlockTempSessionPopup : BaseSwitchHookItem() {

    private const val TEMP_MSG_MANAGER = "com.tencent.mobileqq.managers.TempMsgManager"

    override fun onInit() = HostInfo.isQQ && TEMP_MSG_MANAGER.clazz != null

    override fun onHook() {
        TEMP_MSG_MANAGER.clazz
            ?.findMethods {
                name = "v"
            }
            ?.forEach { it.doNothing(this) }
    }
}
