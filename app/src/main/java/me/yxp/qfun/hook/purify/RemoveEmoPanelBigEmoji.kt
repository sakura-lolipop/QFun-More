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
    "删除QQ表情底栏中的QQ大表情选项",
    "删除在QQ9.0.50左右出现在经典小表情 选项右侧的大表情动画选项（好傻的表情：汪汪、喜花泥等）",
    HookCategory.PURIFY
)
object RemoveEmoPanelBigEmoji : BaseSwitchHookItem() {

    private const val PANEL_CONTROLLER = "com.tencent.mobileqq.emoticonview.EmoticonPanelController"

    /** QStory: 表情面板项 type == 0x13 (19) 即为大表情动画选项 */
    private const val BIG_EMOJI_TYPE = 0x13

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
                    val type = item?.getObjectOrNull("type") as? Number
                    type?.toInt() == BIG_EMOJI_TYPE
                }
            }
    }
}
