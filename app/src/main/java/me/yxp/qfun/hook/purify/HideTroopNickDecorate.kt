package me.yxp.qfun.hook.purify

import android.view.ViewGroup
import android.widget.EditText
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation(
    "隐藏编辑群昵称装扮",
    "对编辑群昵称界面的装扮布局进行隐藏",
    HookCategory.PURIFY
)
object HideTroopNickDecorate : BaseSwitchHookItem(), DexKitTask {

    private const val NICK_SERVICE =
        "com.tencent.mobileqq.activity.editservice.EditTroopMemberNickService"

    private var nickServiceClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        nickServiceClass =
            runCatching { requireClass("nickService") }.getOrNull() ?: NICK_SERVICE.clazz
        return nickServiceClass != null
    }

    override fun onHook() {
        nickServiceClass
            ?.findMethod {
                returnType = void
                paramTypes(ViewGroup::class.java, EditText::class.java, ViewGroup::class.java)
            }
            ?.hookAfter(this) { param ->
                val container = param.args.firstOrNull() as? ViewGroup ?: return@hookAfter
                container.visibility = ViewGroup.GONE
            }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "nickService" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.activity.editservice")
            matcher {
                usingStrings("initGetTroopNickRule EditMode isTroopNick")
            }
        }
    )
}
