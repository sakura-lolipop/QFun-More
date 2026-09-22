package me.yxp.qfun.hook.chat

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import java.lang.reflect.Method

@HookItemAnnotation(
    "去除发送图片限制",
    "解除相册一次最多选择/发送20张图片的上限",
    HookCategory.CHAT
)
object RemoveSendPicLimit : BaseSwitchHookItem() {

    private lateinit var isSelectFull: Method

    override fun onInit(): Boolean {
        val viewModelClass =
            "com.tencent.qqnt.qbasealbum.select.viewmodel.SelectedMediaViewModel".clazz
                ?: return false

        isSelectFull = viewModelClass.findMethod {
            returnType = boolean
            paramCount = 0
        }
        return super.onInit()
    }

    override fun onHook() {
        isSelectFull.returnConstant(this, false)
    }
}
