package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.getObjectOrNull

@HookItemAnnotation(
    "删除QQ表情底栏中的AI选项",
    "删除表情底栏中的AI选项（智绘、AI表情、AI绘绘等）",
    HookCategory.PURIFY
)
object RemoveEmoPanelAITab : BaseSwitchHookItem() {

    private const val PANEL_CONTROLLER = "com.tencent.mobileqq.emoticonview.EmoticonPanelController"

    private val aiKeywords = listOf("AI", "ai", "A.I.", "智绘", "AI表情", "AI绘绘", "创作")

    override fun onInit() = HostInfo.isQQ && PANEL_CONTROLLER.clazz != null

    override fun onHook() {
        PANEL_CONTROLLER.clazz
            ?.findMethod {
                name = "getPanelDataList"
                returnType = list
            }
            ?.hookAfter(this) { param ->
                val list = param.result as? MutableList<Any?> ?: return@hookAfter
                list.removeAll { item ->
                    val tabName = item?.getObjectOrNull("tabName") as? String
                        ?: item?.getObjectOrNull("name") as? String
                        ?: return@removeAll false
                    aiKeywords.any { tabName.contains(it, ignoreCase = true) }
                }
            }
    }
}
