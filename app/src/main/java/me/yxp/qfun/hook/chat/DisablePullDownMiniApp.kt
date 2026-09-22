package me.yxp.qfun.hook.chat

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.callMethod
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import me.yxp.qfun.utils.reflect.setQzFieldDeep
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "禁用下拉小程序",
    "隐藏消息列表下拉进入小程序的入口，不影响正常下拉刷新",
    HookCategory.CHAT
)
object DisablePullDownMiniApp : BaseSwitchHookItem(), DexKitTask {

    /** 头部"小程序入口可用性"开关位（QQ 9.3.15 混淆字段，版本更新易变） */
    private val STATE_FLAG_FIELDS = listOf("I", "E", "D")

    private var headerCls: Class<*>? = null
    private var stateChangedMethod: Method? = null
    private var initMiniAppEntryMethod: Method? = null

    override fun onInit(): Boolean {
        headerCls = "com.tencent.qqnt.chats.view.MiniOldStyleHeaderNew".clazz
            ?: "com.tencent.qqnt.chats.view.MiniOldStyleHeader".clazz

        stateChangedMethod = headerCls?.let { cls -> findStateChangedMethod(cls) }

        initMiniAppEntryMethod = runCatching { requireMethod("initMiniAppEntry") }.getOrNull()
            ?: "com.tencent.mobileqq.activity.home.Conversation".clazz
                ?.findMethodOrNull { name = "initMiniAppEntryLayout" }

        return headerCls != null || initMiniAppEntryMethod != null
    }

    /**
     * onStateChanged(RefreshLayout, oldState, newState)：release 中方法名被混淆，
     * 优先按参数类型匹配，找不到再退化为 3 参 void 方法（QStory 按参数名匹配，QFun 无此能力）
     */
    private fun findStateChangedMethod(cls: Class<*>): Method? {
        val refreshLayoutCls = listOf(
            "com.scwang.smart.refresh.layout.api.RefreshLayout",
            "android.support.v4.widget.SwipeRefreshLayout"
        ).firstNotNullOfOrNull { name -> name.clazz }

        refreshLayoutCls?.let { rl ->
            cls.findMethodOrNull { paramTypes(rl, int, int) }?.let { return it }
        }
        return cls.findMethodOrNull {
            paramCount = 3
            returnType = void
        }
    }

    override fun onHook() {
        hookHeaderConstructors()
        hookStateChanged()
        hookMiniAppEntry()
    }

    /** Hook #1：下拉头部视图构造后立即收起刷新状态 */
    private fun hookHeaderConstructors() {
        headerCls?.declaredConstructors?.forEach { ctor ->
            ctor.hookAfter(this) { param ->
                runCatching { param.args.getOrNull(0)?.callMethod("finishRefresh") }
            }
        }
    }

    /** Hook #2：状态变化回调后清空小程序入口可用性标志位 */
    private fun hookStateChanged() {
        stateChangedMethod?.hookAfter(this) { param ->
            STATE_FLAG_FIELDS.forEach { name ->
                param.thisObject.setQzFieldDeep(name, false)
            }
        }
    }

    /** Hook #3：消息页小程序入口初始化直接空转 */
    private fun hookMiniAppEntry() {
        initMiniAppEntryMethod?.hookBefore(this) { param ->
            param.result = null
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "initMiniAppEntry" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.activity.home")
            matcher {
                usingStrings("init Mini App, cost=")
            }
        }
    )
}
