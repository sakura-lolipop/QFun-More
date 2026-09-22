package me.yxp.qfun.hook.purify

import android.app.Dialog
import android.os.Bundle
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull

@HookItemAnnotation(
    "阻止QQ每日广告弹窗",
    "阻止QQ每天弹出的广告弹窗（激励浏览/激励视频广告页）",
    HookCategory.PURIFY
)
object BlockDailyAdDialog : BaseSwitchHookItem() {

    private val adDialogs = listOf(
        "com.tencent.gdtad.basics.motivebrowsing.GdtMotiveBrowsingDialog",
        "com.tencent.gdtad.basics.motivevideo.GdtMvVideoDialog"
    )

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // 目标类随版本变动（QQ 9.3.15 已移除），任一存在即可用；全缺则静默禁用
        return adDialogs.any { it.clazz != null }
    }

    override fun onHook() {
        adDialogs.forEach { dialogName ->
            dialogName.clazz
                ?.findMethodOrNull {
                    name = "onCreate"
                    paramTypes(Bundle::class.java)
                }
                ?.hookAfter(this) { param ->
                    // QStory: onCreate 后立即触发返回事件关闭广告弹窗
                    runCatching {
                        val dialog = param.thisObject as? Dialog ?: return@hookAfter
                        dialog.dismiss()
                    }
                }
        }
    }
}
