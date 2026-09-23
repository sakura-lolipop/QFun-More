package me.yxp.qfun.hook.social

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 底栏"动态"Tab 直达 QQ 空间好友动态（独立 Activity 方案）。
 *
 * 逆向结论（QQ 9.3.15 base.apk / tinker_classN.apk）：
 * - 底栏 tab 点击链路：TabFrameControllerImpl$a.a(II)（"onTabSelected"）→
 *   TabFrameControllerImpl.dispatchUpdateFrameOnTabClick(FrameFragment, int,
 *   String tabKey)，tabKey 即 TabHost 的 getCurrentTabTag()（动态 tab = "LEBA"，
 *   initTabIndexByConfig 中该槽位固定注册为 LEBA，槽内挂 QzoneFrame 还是聚合页
 *   由 needShowQzoneFrame 闸门决定）。
 * - 好友动态独立页是 QQ 自家完全受支持的页面：路由
 *   com.tencent.qzonehub.api.impl.QZonePageApiImpl.checkIntentRedirect 在
 *   page_launch_friend_feed 时 setClassName 到
 *   com.qzone.reborn.feedx.activity.QZoneFriendFeedXActivity（feedpro 开关关），
 *   且 QZoneFriendFeedXActivity.jumpOtherPageIfNeed 会强制 putExtra
 *   public_fragment_class = com.qzone.reborn.feedx.fragment.QZoneFriendFeedXFragment，
 *   因此启动无需任何 extras。该 Activity 继承 QZoneBaseActivity（完整初始化：
 *   checkQZoneInitState / ResourcePreloader / 换肤 / 自带转场动画），嵌入模式下
 *   渲染失败的空间设置面板在此拥有自己的窗口与 FragmentManager，可正常构建。
 * - 闸门 cooperation.qzone.api.QZoneApiProxy.needShowQzoneFrame(Context,
 *   AppRuntime)（public static → QRoute → QZoneApiProxyImpl）被 reborn UI 运行时
 *   反复调用（如 QZoneFriendFeedxTitle.C()/V(Z) 决定标题栏紧凑模式与设置入口
 *   显隐）。独立页内需要它返回 true（QZONE frame 模式的完整标题栏），底栏主
 *   框架内则保持原生（聚合页可回退）。
 *
 * 实现：
 * 1) hook dispatchUpdateFrameOnTabClick：点击动态 tab（"LEBA"）时启动独立好友
 *    动态页；不阻断原方法，聚合页照常构建在底层，返回键自然回到原界面。带
 *    防抖 + Fragment.isResumed 守卫（避免启动期程序化选 tab 误触发）。
 * 2) 门面/impl 闸门 hook 改为条件生效：仅当调用方 Context 是好友动态独立页时
 *    返回 true（其内部 UI 走 QZONE 模式），其余调用透传原值。
 */
@HookItemAnnotation(
    "底栏直接打开空间动态",
    "点击底栏动态Tab直接打开独立好友动态页（QQ原生页面，设置等功能完整，返回键回到原界面）",
    HookCategory.SOCIAL
)
object QzoneTabDirect : BaseSwitchHookItem() {

    private const val FEED_ACTIVITY = "com.qzone.reborn.feedx.activity.QZoneFriendFeedXActivity"
    private const val QZONE_API_PROXY = "com.tencent.qzonehub.api.impl.QZoneApiProxyImpl"
    private const val QZONE_API_PROXY_FACADE = "cooperation.qzone.api.QZoneApiProxy"
    private const val APP_RUNTIME = "mqq.app.AppRuntime"
    private const val TAB_FRAME_CONTROLLER =
        "com.tencent.mobileqq.activity.home.impl.TabFrameControllerImpl"
    private const val TAB_KEY_LEBA = "LEBA"
    private const val MIN_VERSION_CODE = 12290
    private const val LAUNCH_INTERVAL_MS = 1200L

    /** 好友动态独立 Activity。 */
    private var feedActivityClass: Class<*>? = null

    /** 底栏 tab 点击分发：dispatchUpdateFrameOnTabClick(FrameFragment, int, String)。 */
    private var dispatchTabClick: Method? = null

    /** 闸门门面（静态）与 impl（实例）：独立页内条件返回 true。 */
    private var facadeMethod: Method? = null
    private var implGateMethod: Method? = null

    private var lastLaunchTime = 0L

    /** 宿主 androidx Fragment 反射句柄（FrameFragment 是其子类）。 */
    private var fragmentClass: Class<*>? = null
    private var fragmentGetActivity: Method? = null
    private var fragmentIsResumed: Method? = null

    override fun onInit(): Boolean {
        if (HostInfo.isQQ && HostInfo.versionCode < MIN_VERSION_CODE) return false

        feedActivityClass = FEED_ACTIVITY.clazz ?: run {
            LogUtils.d("$name: QZoneFriendFeedXActivity 缺失，功能禁用")
            return false
        }

        fragmentClass = "androidx.fragment.app.Fragment".clazz ?: run {
            LogUtils.d("$name: androidx Fragment 缺失，功能禁用")
            return false
        }
        fragmentGetActivity = fragmentClass!!.methods.firstOrNull {
            it.name == "getActivity" && it.parameterCount == 0
        }
        fragmentIsResumed = fragmentClass!!.methods.firstOrNull {
            it.name == "isResumed" && it.parameterCount == 0
        }
        if (fragmentGetActivity == null || fragmentIsResumed == null) {
            LogUtils.d("$name: Fragment.getActivity/isResumed 未找到，功能禁用")
            return false
        }

        val controller = TAB_FRAME_CONTROLLER.clazz ?: run {
            LogUtils.d("$name: TabFrameControllerImpl 缺失，功能禁用")
            return false
        }
        dispatchTabClick = controller.declaredMethods.firstOrNull {
            it.name == "dispatchUpdateFrameOnTabClick" && it.parameterCount == 3
        } ?: run {
            LogUtils.d("$name: dispatchUpdateFrameOnTabClick 未找到，功能禁用")
            return false
        }

        val runtime = APP_RUNTIME.clazz
        fun Method.isNeedShowQzoneFrame() =
            name == "needShowQzoneFrame" && parameterCount == 2 &&
                Context::class.java.isAssignableFrom(parameterTypes[0]) &&
                runtime != null && parameterTypes[1].isAssignableFrom(runtime)

        facadeMethod = QZONE_API_PROXY_FACADE.clazz?.declaredMethods?.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.isNeedShowQzoneFrame()
        }
        implGateMethod = QZONE_API_PROXY.clazz?.declaredMethods?.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.isNeedShowQzoneFrame()
        }
        if (facadeMethod == null && implGateMethod == null) {
            LogUtils.d("$name: needShowQzoneFrame 门面/impl 均未找到，独立页内将使用原生紧凑标题栏")
        }

        return true
    }

    override fun onHook() {
        dispatchTabClick?.hookBefore(this) { param ->
            if (param.args.getOrNull(2) != TAB_KEY_LEBA) return@hookBefore
            val fragment = param.args.getOrNull(0) ?: return@hookBefore
            val fragmentCls = fragmentClass ?: return@hookBefore
            if (!fragmentCls.isInstance(fragment)) return@hookBefore
            val activity = runCatching {
                fragmentGetActivity?.invoke(fragment) as? Activity
            }.getOrNull() ?: return@hookBefore
            if (activity.isFinishing) return@hookBefore
            val resumed = runCatching {
                fragmentIsResumed?.invoke(fragment) as? Boolean
            }.getOrNull() == true
            if (!resumed) return@hookBefore
            launchFeedActivity(activity)
        }

        facadeMethod?.hookBefore(this) { param ->
            if (isInFeedActivity(param.args.getOrNull(0) as? Context)) param.result = true
        }
        implGateMethod?.hookBefore(this) { param ->
            if (isInFeedActivity(param.args.getOrNull(0) as? Context)) param.result = true
        }
    }

    private fun isInFeedActivity(context: Context?): Boolean {
        var current: Context? = context
        var depth = 0
        while (current != null && depth < 6) {
            if (feedActivityClass?.isInstance(current) == true) return true
            current = (current as? ContextWrapper)?.baseContext
            depth++
        }
        return false
    }

    private fun launchFeedActivity(activity: Activity) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchTime < LAUNCH_INTERVAL_MS) return
        lastLaunchTime = now
        runCatching {
            activity.startActivity(Intent().setClassName(activity, FEED_ACTIVITY))
        }.onFailure {
            LogUtils.e("$name: 启动好友动态页失败", it)
        }
    }
}
