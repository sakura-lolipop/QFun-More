package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.reflect.callMethod
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import me.yxp.qfun.utils.reflect.findQzMethodDeepOrNull
import me.yxp.qfun.utils.reflect.getObjectByTypeOrNull
import me.yxp.qfun.utils.reflect.setQzFieldDeep
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "小程序跳过广告",
    "自动跳过小程序开屏/激励视频广告（GDT 广告 SDK），QQ 更新后易失效",
    HookCategory.PURIFY
)
object MiniProgramSkipAds : BaseSwitchHookItem(), DexKitTask {

    private const val DELAY_MOTIVE_BROWSING = 200L
    private const val DELAY_MOTIVE_VIDEO = 1200L

    /** 跳过标记字段名：QQ 9.3.15 为 "I"，旧版为 "n" */
    private val SKIP_FLAG_CANDIDATES = listOf("I", "n")

    /** 伪装已看完广告的字段名（mHasWatchAds） */
    private const val HAS_WATCH_ADS_FIELD = "k"

    /** initTitle 方法名：QQ 9.3.15 为 "W"，旧版为 "Z"/"V" */
    private val INIT_TITLE_CANDIDATES = listOf("W", "Z", "V")

    private var mvControllerCallback: Method? = null

    private var browsingDialogCls: Class<*>? = null
    private var doOnBackEvent: Method? = null

    private var videoViewControllerCls: Class<*>? = null
    private var onBackEvent: Method? = null

    private var motiveVideoDialogCls: Class<*>? = null

    override fun onInit(): Boolean {
        mvControllerCallback = runCatching { requireMethod("mvControllerCallback") }.getOrNull()

        browsingDialogCls = "com.tencent.gdtad.basics.motivebrowsing.GdtMotiveBrowsingDialog".clazz
        doOnBackEvent = browsingDialogCls?.findMethodOrNull { name = "doOnBackEvent" }

        videoViewControllerCls = "com.tencent.gdtad.basics.motivevideo.GdtMvVideoViewController".clazz
        onBackEvent = videoViewControllerCls?.findMethodOrNull {
            returnType = boolean
            paramTypes(boolean)
        }

        motiveVideoDialogCls =
            "com.tencent.gdtad.basics.motivevideo.GdtMotiveVideoDialog".clazz
                ?: runCatching { requireClass("motiveVideoDialog") }.getOrNull()

        return mvControllerCallback != null ||
                (browsingDialogCls != null && doOnBackEvent != null) ||
                (videoViewControllerCls != null && onBackEvent != null) ||
                motiveVideoDialogCls != null
    }

    override fun onHook() {
        hookMvControllerCallback()
        hookMotiveBrowsingDialog()
        hookMvVideoViewController()
        hookMotiveVideoDialog()
    }

    /** Hook #1：动视频控制器回调 a.b(View, controller, model) after → controller.a()/b() */
    private fun hookMvControllerCallback() {
        mvControllerCallback?.hookAfter(this) { param ->
            val controller = param.args.getOrNull(1) ?: return@hookAfter
            runCatching { controller.callMethod("a") }
            runCatching { controller.callMethod("b") }
        }
    }

    /** Hook #2：激励浏览弹窗 onCreate after +200ms → 置跳过标记 + doOnBackEvent() */
    private fun hookMotiveBrowsingDialog() {
        val dialogCls = browsingDialogCls ?: return
        val backEvent = doOnBackEvent ?: return

        dialogCls.findQzMethodDeepOrNull {
            name = "onCreate"
            paramTypes(bundle)
        }?.hookAfter(this) { param ->
            val dialog = param.thisObject
            ModuleScope.launchMainDelayed(DELAY_MOTIVE_BROWSING) {
                SKIP_FLAG_CANDIDATES.forEach { name ->
                    dialog.setQzFieldDeep(name, true)
                }
                runCatching { backEvent.invoke(dialog) }
            }
        }
    }

    /** Hook #3：动视频控制器 onBackEvent(boolean) before → 伪装已看完广告 */
    private fun hookMvVideoViewController() {
        onBackEvent?.hookBefore(this) { param ->
            param.thisObject.setQzFieldDeep(HAS_WATCH_ADS_FIELD, true)
        }
    }

    /** Hook #4：动视频弹窗 onCreate after +1200ms → controller.a()/b()/initTitle */
    private fun hookMotiveVideoDialog() {
        val dialogCls = motiveVideoDialogCls ?: return
        val controllerCls = videoViewControllerCls ?: return

        dialogCls.findQzMethodDeepOrNull {
            name = "onCreate"
            paramTypes(bundle)
        }?.hookAfter(this) { param ->
            val controller = param.thisObject.getObjectByTypeOrNull(controllerCls) ?: return@hookAfter
            ModuleScope.launchMainDelayed(DELAY_MOTIVE_VIDEO) {
                runCatching { controller.callMethod("a") }
                runCatching { controller.callMethod("b") }
                for (name in INIT_TITLE_CANDIDATES) {
                    if (runCatching { controller.callMethod(name) }.isSuccess) break
                }
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "mvControllerCallback" to FindMethod().apply {
            searchPackages("com.tencent.gdtad.basics.motivevideo")
            matcher {
                usingStrings("GdtMvTitleHelper", "bar == null")
            }
        },
        "motiveVideoDialog" to FindClass().apply {
            searchPackages("com.tencent.gdtad.basics.motivevideo")
            matcher {
                usingStrings("onWindowFocusChanged() called with: hasFocus = [")
            }
        }
    )
}
