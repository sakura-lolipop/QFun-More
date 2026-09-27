package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "屏蔽临时会话弹窗",
    "在没有开启临时会话的情况下查看已删除好友的聊天记录 则不会有开启临时会话弹窗",
    HookCategory.PURIFY
)
object BlockTempSessionPopup : BaseSwitchHookItem(), DexKitTask {

    private const val TEMP_MSG_MANAGER = "com.tencent.mobileqq.managers.TempMsgManager"

    private var tempMsgManagerClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        tempMsgManagerClass =
            runCatching { requireClass("tempMsgManager") }.getOrNull() ?: TEMP_MSG_MANAGER.clazz
        return tempMsgManagerClass != null
    }

    override fun onHook() {
        tempMsgManagerClass
            ?.findMethods {
                name = "v"
            }
            ?.forEach { it.doNothing(this) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "tempMsgManager" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.managers")
            matcher {
                usingStrings(listOf("can't tempMsgInfo, insert a new tempMsgInfo! type="), matchType = StringMatchType.Equals)
            }
        }
    )
}
