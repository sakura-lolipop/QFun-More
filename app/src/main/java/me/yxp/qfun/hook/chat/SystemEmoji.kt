package me.yxp.qfun.hook.chat

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.returnConstant
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "强制使用系统Emoji",
    "屏蔽QQ自带的Emoji表情，强制显示系统Emoji",
    HookCategory.CHAT
)
object SystemEmoji : BaseSwitchHookItem(), DexKitTask {
    override fun onHook() {
        val target = runCatching { requireClass("emotcationConstants") }.getOrNull()
            ?: "com.tencent.mobileqq.text.EmotcationConstants".toClass
        target.findMethod { name = "getSingleEmoji" }.returnConstant(this, -1)
        target.findMethod { name = "getDoubleEmoji" }.returnConstant(this, -1)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "emotcationConstants" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.text")
            matcher {
                usingStrings(listOf("setEmojiMap EMOJI_MAP size="), matchType = StringMatchType.Equals)
            }
        }
    )
}
