package me.yxp.qfun.hook.purify

import android.view.View
import android.widget.LinearLayout
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.getObjectByTypeOrNull
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "陌生人资料卡你们的关系标识",
    "隐藏陌生人资料卡上方的关系标识区域",
    HookCategory.PURIFY
)
object HideRelationshipIdentification : BaseSwitchHookItem(), DexKitTask {

    private const val IN_STEP_COMPONENT =
        "com.tencent.mobileqq.profilecard.component.ProfileInStepComponent"
    private const val CARD_INFO = "com.tencent.mobileqq.profilecard.data.ProfileCardInfo"

    // DEXKIT-KEEP（运行时视图树检查，非 hook 目标查找）: RFWCardListView 仅用于
    // onDataUpdate 后从运行时实例上按类型找 listView，与 dex 定位无关
    private const val CARD_LIST_VIEW =
        "com.tencent.biz.richframework.widget.listview.card.RFWCardListView"

    private var inStepComponentClass: Class<*>? = null
    private var cardInfoClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        inStepComponentClass =
            runCatching { requireClass("inStepComponent") }.getOrNull() ?: IN_STEP_COMPONENT.clazz
        cardInfoClass = runCatching { requireClass("profileCardInfo") }.getOrNull() ?: CARD_INFO.clazz
        return inStepComponentClass != null && cardInfoClass != null
    }

    override fun onHook() {
        val componentClass = inStepComponentClass ?: return
        val infoClass = cardInfoClass ?: return
        componentClass
            .findMethod {
                name = "onDataUpdate"
                returnType = boolean
                paramTypes(infoClass)
            }
            .hookAfter(this) { param ->
                val cardListViewClass = CARD_LIST_VIEW.clazz ?: return@hookAfter
                val listView = param.thisObject.getObjectByTypeOrNull(cardListViewClass)
                    ?: return@hookAfter
                val parent = runCatching { (listView as View).parent }.getOrNull() as? LinearLayout
                    ?: return@hookAfter
                for (i in 0 until parent.childCount) {
                    val child = parent.getChildAt(i)
                    if (child.visibility != View.GONE) child.visibility = View.GONE
                }
                if (parent.visibility != View.GONE) parent.visibility = View.GONE
            }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "inStepComponent" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.profilecard")
            matcher {
                usingStrings(listOf("bindCardData fail, card or listview is null"), matchType = StringMatchType.Equals)
            }
        },
        // ProfileCardInfo 自身无字符串常量，按方法组合定位（9.3.15 双包验证唯一）
        "profileCardInfo" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.profilecard.data")
            matcher {
                methods {
                    add { name("putBusinessInfo") }
                    add { name("getBusinessInfo") }
                }
            }
        }
    )
}
