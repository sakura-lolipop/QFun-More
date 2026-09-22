package me.yxp.qfun.hook.social

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import me.yxp.qfun.utils.reflect.getQzFieldDeepOrNull
import me.yxp.qfun.utils.reflect.setQzFieldDeep
import java.lang.reflect.Method

@HookItemAnnotation(
    "允许查看异常资料卡",
    "忽略账号的异常状态，使其能够正常查看资料卡",
    HookCategory.SOCIAL
)
object AllowAbnormalProfileCard : BaseSwitchHookItem() {

    /** 服务端风控码：201/202 = 账号异常/疑似封禁 */
    private val FORBID_CODES = setOf(201, 202)

    private var getProfileCardMethod: Method? = null
    private var getProfileCardFromCacheMethod: Method? = null
    private var processProfileCardMethod: Method? = null

    override fun onInit(): Boolean {
        val serviceCls =
            "com.tencent.mobileqq.profilecard.api.impl.ProfileDataServiceImpl".clazz

        getProfileCardMethod = serviceCls?.findMethodOrNull {
            name = "getProfileCard"
            paramTypes(string, boolean, null)
        }
        getProfileCardFromCacheMethod = serviceCls?.findMethodOrNull {
            name = "getProfileCardFromCache"
            paramTypes(string, null)
        }

        // 备路径：主路径类被改异步/改名时仍可从协议处理层清码，两条路都装
        val processorCls =
            "com.tencent.mobileqq.profilecard.processor.ProfileSecureProcessor".clazz
        processProfileCardMethod = processorCls?.findMethodOrNull {
            name = "processProfileCard"
            paramTypes(bundle, null, null)
        }

        return getProfileCardMethod != null ||
                getProfileCardFromCacheMethod != null ||
                processProfileCardMethod != null
    }

    override fun onHook() {
        getProfileCardMethod?.hookAfter(this) { param -> fixForbid(param.result) }
        getProfileCardFromCacheMethod?.hookAfter(this) { param -> fixForbid(param.result) }
        processProfileCardMethod?.hookBefore(this) { param ->
            fixIResult(param.args.getOrNull(2))
        }
    }

    /** 主路径：把 ProfileCardInfo 的 forbidCode 清零并解除 isForbidAccount */
    private fun fixForbid(result: Any?) {
        result ?: return
        val code = result.getQzFieldDeepOrNull("forbidCode") as? Int ?: return
        if (code in FORBID_CODES) {
            result.setQzFieldDeep("isForbidAccount", false)
            result.setQzFieldDeep("forbidCode", 0)
        }
    }

    /** 备路径：把 RespSummaryCard.iResult 清零 */
    private fun fixIResult(resp: Any?) {
        resp ?: return
        val code = resp.getQzFieldDeepOrNull("iResult") as? Int ?: return
        if (code in FORBID_CODES) {
            resp.setQzFieldDeep("iResult", 0)
        }
    }
}
