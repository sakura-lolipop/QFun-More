package me.yxp.qfun.hook.social

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.setObject
import me.yxp.qfun.utils.reflect.setObjectByType
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "移除转发数量限制",
    "解除转发联系人时最多选择9人的限制",
    HookCategory.SOCIAL
)
object RemoveForwardLimit : BaseSwitchHookItem(), DexKitTask {

    private lateinit var configClass: Class<*>
    private lateinit var recentClass: Class<*>
    private lateinit var friendClass: Class<*>
    private lateinit var troopClass: Class<*>

    private val isNew: Boolean
        get() = HostInfo.isQQ && HostInfo.versionCode >= 11820

    override fun onInit(): Boolean {

        if (isNew) {
            configClass = requireClass("ChatSelectorConfig")
        } else {
            recentClass = runCatching { requireClass("forwardRecentActivity") }.getOrNull()
                ?: "com.tencent.mobileqq.activity.ForwardRecentActivity".toClass
            friendClass = runCatching { requireClass("forwardFriendListActivity") }.getOrNull()
                ?: "com.tencent.mobileqq.activity.ForwardFriendListActivity".toClass
            troopClass = runCatching { requireClass("forwardTroopListFragment") }.getOrNull()
                ?: "com.tencent.mobileqq.activity.ForwardTroopListFragment".toClass
        }

        return super.onInit()
    }

    override fun onHook() {
        if (isNew) {

            configClass.declaredConstructors
                .filter { it.parameterCount >= 3 }
                .forEach {
                    it.hookBefore(this) { param ->
                        param.args[2] = Int.MAX_VALUE
                    }
                }
        } else {

            recentClass.declaredConstructors.forEach {
                it.hookAfter(this) { param ->
                    param.thisObject.setObject("mForwardTargetMap", FakeMap())
                }
            }
            friendClass.declaredConstructors.forEach {
                it.hookAfter(this) { param ->
                    param.thisObject.setObjectByType<Map<*, *>>(FakeMap())
                }
            }
            troopClass.declaredConstructors.forEach {
                it.hookAfter(this) { param ->
                    param.thisObject.setObjectByType<Map<*, *>>(FakeMap())
                }
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "ChatSelectorConfig" to FindClass().apply {
            matcher {
                usingStrings(listOf("ChatSelectorConfig"), matchType = StringMatchType.Equals)
            }
        },

        // 旧版分支（versionCode < 11820）：三个转发选择器均为稳定名，锚点 9.3.15 双包验证唯一；
        // 旧 QQ 上若锚点失配则落空走硬编码回退，行为不变
        "forwardRecentActivity" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.activity")
            matcher {
                usingStrings(listOf("refreshRecentList, filter deleted friend, uin = "), matchType = StringMatchType.Equals)
            }
        },

        "forwardFriendListActivity" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.activity")
            matcher {
                usingStrings(listOf("forwardOption is null, return."), matchType = StringMatchType.Equals)
            }
        },

        "forwardTroopListFragment" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.activity")
            matcher {
                // choose_* 常量与 ForwardFriendListActivity 共享，
                // 以基类 PublicBaseFragment 区分（Friend 系继承 BaseForwardSelectionActivity）
                superClass("com.tencent.mobileqq.fragment.PublicBaseFragment")
                usingStrings(listOf("choose_fri_multiple_page_type"), matchType = StringMatchType.Equals)
            }
        }
    )

}

class FakeMap : LinkedHashMap<Any, Any>() {
    override val size: Int
        get() {
            val s = super.size
            return if (s == 9) 8 else s
        }
}