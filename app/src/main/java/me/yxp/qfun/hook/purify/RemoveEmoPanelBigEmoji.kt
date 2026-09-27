package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.getObjectOrNull
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "删除QQ表情底栏中的QQ大表情选项",
    "删除在QQ9.0.50左右出现在经典小表情 选项右侧的大表情动画选项（好傻的表情：汪汪、喜花泥等）",
    HookCategory.PURIFY
)
object RemoveEmoPanelBigEmoji : BaseSwitchHookItem(), DexKitTask {

    private const val PANEL_CONTROLLER = "com.tencent.mobileqq.emoticonview.EmoticonPanelController"

    /** QStory: 表情面板项 type == 0x13 (19) 即为大表情动画选项 */
    private const val BIG_EMOJI_TYPE = 0x13

    private var panelControllerClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        panelControllerClass =
            runCatching { requireClass("emoticonPanelController") }.getOrNull() ?: PANEL_CONTROLLER.clazz
        return panelControllerClass != null
    }

    override fun onHook() {
        panelControllerClass
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

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "emoticonPanelController" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.emoticonview")
            matcher {
                // 类自引用 TAG 串全 dex 唯一，再叠加拼错的 "OnDestory" 日志串收紧
                usingStrings(listOf("EmoticonPanelController", "OnDestory"), matchType = StringMatchType.Equals)
            }
        }
    )
}
