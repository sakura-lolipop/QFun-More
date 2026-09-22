package me.yxp.qfun.hook.purify

import android.view.ViewGroup
import android.widget.EditText
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod

@HookItemAnnotation(
    "隐藏编辑群昵称装扮",
    "对编辑群昵称界面的装扮布局进行隐藏",
    HookCategory.PURIFY
)
object HideTroopNickDecorate : BaseSwitchHookItem() {

    private const val NICK_SERVICE =
        "com.tencent.mobileqq.activity.editservice.EditTroopMemberNickService"

    override fun onInit() = HostInfo.isQQ && NICK_SERVICE.clazz != null

    override fun onHook() {
        NICK_SERVICE.clazz
            ?.findMethod {
                returnType = void
                paramTypes(ViewGroup::class.java, EditText::class.java, ViewGroup::class.java)
            }
            ?.hookAfter(this) { param ->
                val container = param.args.firstOrNull() as? ViewGroup ?: return@hookAfter
                container.visibility = ViewGroup.GONE
            }
    }
}
