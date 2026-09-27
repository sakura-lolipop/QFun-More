package me.yxp.qfun.hook.social

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import me.yxp.qfun.utils.reflect.getQzFieldDeepOrNull
import me.yxp.qfun.utils.reflect.setQzFieldDeep
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

@HookItemAnnotation(
    "允许查看异常资料卡",
    "忽略账号的异常状态，使其能够正常查看资料卡",
    HookCategory.SOCIAL
)
object AllowAbnormalProfileCard : BaseSwitchHookItem(), DexKitTask {

    /** 服务端风控码：201/202 = 账号异常/疑似封禁 */
    private val FORBID_CODES = setOf(201, 202)

    private var getProfileCardMethod: Method? = null
    private var getProfileCardFromCacheMethod: Method? = null
    private var processProfileCardMethod: Method? = null

    override fun onInit(): Boolean {
        val serviceCls = runCatching { requireClass("profileDataService") }.getOrNull()
            ?: "com.tencent.mobileqq.profilecard.api.impl.ProfileDataServiceImpl".clazz

        getProfileCardMethod = serviceCls?.findMethodOrNull {
            name = "getProfileCard"
            paramTypes(string, boolean, null)
        }
        getProfileCardFromCacheMethod = serviceCls?.findMethodOrNull {
            name = "getProfileCardFromCache"
            paramTypes(string, null)
        }

        // 备路径：主路径类被改异步/改名时仍可从协议处理层清码，两条路都装
        val processorCls = runCatching { requireClass("profileSecureProcessor") }.getOrNull()
            ?: "com.tencent.mobileqq.profilecard.processor.ProfileSecureProcessor".clazz
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

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "profileDataService" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.profilecard.api.impl")
            matcher {
                // 该服务实现类独有的取卡日志串（9.3.15 双包全 dex 验证唯一）
                usingStrings(
                    listOf(
                        "getProfileCard from db. uin=%s card=%s",
                        "getProfileCard from new. uin=%s card=%s"
                    ),
                    matchType = StringMatchType.Equals
                )
            }
        },

        "profileSecureProcessor" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.profilecard.processor")
            matcher {
                // 协议处理层风控清码日志串（9.3.15 双包全 dex 验证唯一）
                usingStrings(
                    listOf(
                        "onProcessProfileCard, get card null",
                        "onProcessProfileCard, card isForbidAccount="
                    ),
                    matchType = StringMatchType.Equals
                )
            }
        }
    )
}
