package me.yxp.qfun.hook.purify

import android.view.View
import android.widget.LinearLayout
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.getObjectByTypeOrNull

@HookItemAnnotation(
    "陌生人资料卡你们的关系标识",
    "隐藏陌生人资料卡上方的关系标识区域",
    HookCategory.PURIFY
)
object HideRelationshipIdentification : BaseSwitchHookItem() {

    private const val IN_STEP_COMPONENT =
        "com.tencent.mobileqq.profilecard.component.ProfileInStepComponent"
    private const val CARD_INFO = "com.tencent.mobileqq.profilecard.data.ProfileCardInfo"
    private const val CARD_LIST_VIEW =
        "com.tencent.biz.richframework.widget.listview.card.RFWCardListView"

    override fun onInit() =
        HostInfo.isQQ && IN_STEP_COMPONENT.clazz != null && CARD_INFO.clazz != null

    override fun onHook() {
        val componentClass = IN_STEP_COMPONENT.clazz ?: return
        val cardInfoClass = CARD_INFO.clazz ?: return
        componentClass
            .findMethod {
                name = "onDataUpdate"
                returnType = boolean
                paramTypes(cardInfoClass)
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
}
