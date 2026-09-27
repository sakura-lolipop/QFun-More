package me.yxp.qfun.hook.purify

import com.tencent.mobileqq.aio.msg.AIOMsgItem
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "移除表情回应",
    "移除长按出现的表情回应菜单以及消息下方表情回应",
    HookCategory.PURIFY
)
object RemoveEmoReplyMenu : BaseSwitchHookItem(), DexKitTask {

    private lateinit var handleUIState: Method

    private lateinit var getMenuView: Method

    override fun onInit(): Boolean {
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码（保留 toClass 缺类抛错语义）
        val tailComponentClass = runCatching { requireClass("msgTailComponent") }.getOrNull()
            ?: "com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent".toClass
        handleUIState = tailComponentClass
            .findMethod {
                returnType = void
                paramTypes(int, AIOMsgItem::class.java, list)
            }
        val emoReplyApiClass = runCatching { requireClass("emoReplyMenuApi") }.getOrNull()
            ?: "com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl".toClass
        getMenuView = emoReplyApiClass
            .findMethod {
                name =
                    if (HostInfo.versionCode >= 9898) "getSeparateEmoReplyMenuView" else "getEmoReplyMenuView"
            }
        return super.onInit()
    }

    override fun onHook() {
        getMenuView.doNothing(this)
        handleUIState.doNothing(this)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "msgTailComponent" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder.component.msgtail")
            matcher {
                usingStrings("not in EmoReplyWhiteList")
            }
        },
        "emoReplyMenuApi" to FindClass().apply {
            searchPackages("com.tencent.qqnt.aio.api.impl")
            matcher {
                usingStrings("[getSeparateEmoReplyMenuView] context is null")
            }
        }
    )
}
