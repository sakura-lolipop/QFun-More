package me.yxp.qfun.hook.chat

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.replaceFirstParam
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

@HookItemAnnotation(
    "解除左滑回复限制",
    "解除部分类型消息无法左滑回复的限制",
    HookCategory.CHAT
)
object UnlockLeftSwipeReply : BaseSwitchHookItem(), DexKitTask {

    private lateinit var setSwipeEnable: Method

    override fun onInit(): Boolean {
        setSwipeEnable = (runCatching { requireClass("leftSwipeHelper") }.getOrNull()
            ?: "com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea.AIOContentLeftSwipeHelper".toClass)
            .findMethod {
                returnType = void
                paramTypes(boolean)
            }
        return super.onInit()
    }

    override fun onHook() {
        setSwipeEnable.replaceFirstParam(true, this)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "leftSwipeHelper" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder.component.leftswipearea")
            matcher {
                usingStrings(listOf("onTouchEvent onLeftSwipeEnd"), matchType = StringMatchType.Equals)
            }
        }
    )
}
