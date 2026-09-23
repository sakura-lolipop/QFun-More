package me.yxp.qfun.hook.social

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.reflect.clazz
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 底栏"动态"Tab 直达 QQ 空间好友动态页 + 空间页视觉修复。
 *
 * 直开原理（QQ 9.3.15 实证）：
 * - 启动期 frame 决策读 checkBusinessSwitch 产出的状态 h("QZONE"/"LEBA")，
 *   运行期 reborn UI 反复询问 needShowQzoneFrame（门面/impl 两个入口都有调用方）。
 * - QStory 2.6.4 原实现只 hook impl（在 9.3.15 上失效）。本版三管齐下：
 *   facade 静态 + impl 实例（均返回 true）+ checkBusinessSwitch 状态覆写。
 * - facade 类启动期不可加载 → facade 钩子在 armFeedHooks() 里懒注册
 *   （impl 渲染钩子触发时重试，直到类可加载）。
 *
 * 灰带根治（详见 qfunory/gray.md）：
 * - 灰带1：QzoneConciseHeaderView 内空的订阅刷新头容器（36px 灰）→ GONE。
 * - 灰带2：feedx/widget/j（ItemDecoration，混淆名随版本变）在卡片间隙
 *   onDraw 画 #dedddc 实心条并预留 offsets → getItemOffsets 清零 + onDraw 拦截。
 * - 评论条 QUI 填充底色 → 绑定后置空背景让皮肤透出（卡片灰底亦让位）。
 * - qzone 侧类启动期不可加载 → 全部在 impl 首次触发时懒挂载。
 */
@HookItemAnnotation(
    "底栏直接打开空间动态",
    "点击底栏动态Tab时直接显示空间好友动态（含灰带消除；空间页缺失时自动回退聚合页）",
    HookCategory.SOCIAL
)
object QzoneTabDirect : BaseSwitchHookItem() {

    private const val QZONE_API_PROXY = "com.tencent.qzonehub.api.impl.QZoneApiProxyImpl"
    private const val QZONE_API_PROXY_FACADE = "cooperation.qzone.api.QZoneApiProxy"
    private const val APP_RUNTIME = "mqq.app.AppRuntime"
    private const val TAB_FRAME_CONTROLLER =
        "com.tencent.mobileqq.activity.home.impl.TabFrameControllerImpl"
    private const val QZONE_FRAME = "com.tencent.mobileqq.activity.leba.QzoneFrame"
    private const val SUBSCRIBE_REFRESH_HEADER =
        "com.tencent.biz.subscribe.part.block.base.RefreshHeaderView"
    private const val FEED_ITEM_BASE = "com.qzone.reborn.feedx.itemview.QZoneBaseFeedItemView"
    private const val FEED_LIST_DECORATION = "com.qzone.reborn.feedx.widget.j"
    private const val BOTTOM_AREA_ID = 0x7f0a6619
    private const val MIN_VERSION_CODE = 12290

    private const val KEY_QZONE = "QZONE"
    private const val KEY_LEBA = "LEBA"

    private var facadeMethod: Method? = null
    private var implGateMethod: Method? = null
    private var checkBusinessSwitch: Method? = null
    private var stateSetter: Method? = null

    private var facadeHooksArmed = false
    private var feedHooksArmed = false
    private var listDecoArmed = false
    private var facadeCalls = 0

    override fun onInit(): Boolean {
        if (HostInfo.isQQ && HostInfo.versionCode < MIN_VERSION_CODE) return false

        if (QZONE_FRAME.clazz == null) {
            LogUtils.d("$name: QzoneFrame 缺失，功能禁用")
            return false
        }

        val runtime = APP_RUNTIME.clazz ?: return false

        fun Method.isNeedShowQzoneFrame() =
            name == "needShowQzoneFrame" && parameterCount == 2 &&
                Context::class.java.isAssignableFrom(parameterTypes[0]) &&
                parameterTypes[1].isAssignableFrom(runtime)

        facadeMethod = QZONE_API_PROXY_FACADE.clazz?.declaredMethods?.firstOrNull {
            Modifier.isStatic(it.modifiers) && it.isNeedShowQzoneFrame()
        }
        implGateMethod = QZONE_API_PROXY.clazz?.declaredMethods?.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.isNeedShowQzoneFrame()
        }
        if (facadeMethod == null && implGateMethod == null) {
            LogUtils.d("$name: needShowQzoneFrame 门面/impl 均未找到")
            return false
        }

        checkBusinessSwitch = TAB_FRAME_CONTROLLER.clazz?.declaredMethods?.firstOrNull {
            it.name == "checkBusinessSwitch" && it.parameterCount == 1
        }
        if (checkBusinessSwitch != null) {
            val stateClass = checkBusinessSwitch!!.parameterTypes[0]
            stateSetter = stateClass.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0] == String::class.java &&
                    it.parameterTypes[1] == Boolean::class.javaPrimitiveType
            }
            if (stateSetter == null) checkBusinessSwitch = null
        }

        return true
    }

    override fun onHook() {
        // 1) 门面（onInit 时类不可加载则为 null，armFeedHooks 懒补注册）
        facadeMethod?.hookBefore(this) { param -> param.result = true }
        // 2) impl 兜底：feed 渲染必经，也是懒挂载的触发源
        implGateMethod?.hookBefore(this) { param ->
            param.result = true
            armFeedHooks()
            armListDecoration()
            if (facadeCalls++ % 20 == 0) dumpViewTreeWithBgOnce()
        }
        // 3) 启动期 frame 决策状态覆写
        checkBusinessSwitch?.hookAfter(this) { param ->
            val state = param.args.firstOrNull() ?: return@hookAfter
            stateSetter?.invoke(state, KEY_QZONE, true)
            stateSetter?.invoke(state, KEY_LEBA, false)
        }
        // 4) 灰带1：空的订阅刷新头容器 —— attach 瞬间 GONE 父容器（内容非空自动恢复）
        SUBSCRIBE_REFRESH_HEADER.clazz?.declaredConstructors?.forEach { ctor ->
            ctor.hookAfter(this) { param ->
                val v = param.thisObject as? View ?: return@hookAfter
                v.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(av: View) {
                        if (!hasAncestorNamed(av, "QzoneConciseHeaderView")) return
                        (av.parent as? ViewGroup)?.visibility = View.GONE
                        av.post {
                            val p = av.parent as? ViewGroup
                            if (av.height > 0 && p != null) p.visibility = View.VISIBLE
                        }
                    }

                    override fun onViewDetachedFromWindow(av: View) {}
                })
            }
        }
    }

    /** 懒挂载：facade 门面钩子补注册 + feed 卡片绑定钩子。 */
    private fun armFeedHooks() {
        if (!facadeHooksArmed) {
            val runtime = APP_RUNTIME.clazz
            val f = if (runtime == null) {
                null
            } else {
                QZONE_API_PROXY_FACADE.clazz?.declaredMethods?.firstOrNull {
                    it.name == "needShowQzoneFrame" && it.parameterCount == 2 &&
                        Context::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                        it.parameterTypes[1].isAssignableFrom(runtime)
                }
            }
            if (f != null) {
                facadeMethod = f
                f.hookBefore(this) { param -> param.result = true }
                facadeHooksArmed = true
            }
        }
        if (!feedHooksArmed) {
            val ms = FEED_ITEM_BASE.clazz?.declaredMethods
            if (ms != null) {
                ms.filter { it.name == "bindData" || it.name == "c0" }.forEach { m ->
                    m.hookAfter(this) { param ->
                        (param.thisObject as? View)?.let(::surgeryOnBind)
                    }
                }
                feedHooksArmed = true
            }
        }
    }

    /** 懒挂载：列表装饰器（灰条绘制与预留）双拦截。 */
    private fun armListDecoration() {
        if (listDecoArmed) return
        val ms = FEED_LIST_DECORATION.clazz?.declaredMethods
        if (ms != null) {
            ms.forEach { m ->
                when (m.name) {
                    "getItemOffsets" -> m.hookAfter(this) { param ->
                        (param.args.firstOrNull() as? Rect)?.set(0, 0, 0, 0)
                    }

                    "onDraw" -> m.doNothing(this)
                }
            }
            listDecoArmed = true
        }
    }

    /**
     * 每次绑定后的卡片手术（含复用视图；绑定后、布局前执行，无闪现）：
     * 1) 卡片灰底 ColorDrawable 置空 → 页面级皮肤透出（灰带2 主体）；
     * 2) 卡片 margin 原地归零（卡片间 24px 灰隙）；
     * 3) 底部区子树（快捷评论条）背景清空 + margin 归零。
     * 注意：本方法在布局计算期间被调用，禁止 `layoutParams =` 赋值（会抛异常），
     * margin 一律原地改字段，下一轮布局自然生效。
     */
    private fun surgeryOnBind(card: View) {
        runCatching {
            if (card.background is android.graphics.drawable.ColorDrawable) card.background = null
            val lp = card.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null && (lp.topMargin != 0 || lp.bottomMargin != 0)) {
                lp.topMargin = 0
                lp.bottomMargin = 0
            }
            val strip = card.findViewById<View>(BOTTOM_AREA_ID) ?: return
            fun clear(v: View) {
                if (v !== strip && v.background != null) v.background = null
                val vlp = v.layoutParams as? ViewGroup.MarginLayoutParams
                if (vlp != null && (vlp.topMargin != 0 || vlp.bottomMargin != 0)) {
                    vlp.topMargin = 0
                    vlp.bottomMargin = 0
                }
                if (v is ViewGroup) for (i in 0 until v.childCount) clear(v.getChildAt(i))
            }
            clear(strip)
        }
    }

    private fun hasAncestorNamed(v: View, simpleName: String): Boolean {
        var p: ViewParent = v.parent
        while (p is ViewGroup) {
            if (p.javaClass.simpleName == simpleName) return true
            p = p.parent
        }
        return false
    }

    /** TEMP-DEBUG: 空间页渲染时 dump 视图树（含背景类型），定位稳定后移除。 */
    private fun dumpViewTreeWithBgOnce() {
        runCatching {
            val activity = QQCurrentEnv.activity ?: return
            val out = StringBuilder()
            fun walk(v: View, depth: Int) {
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                val bg = v.background?.javaClass?.simpleName ?: "-"
                val lp = v.layoutParams as? ViewGroup.MarginLayoutParams
                val mg = if (lp != null) "mT=${lp.topMargin},mB=${lp.bottomMargin}" else "mX"
                out.append("  ".repeat(depth)).append(v.javaClass.name)
                    .append(" @").append(loc[0]).append(',').append(loc[1])
                    .append(' ').append(v.width).append('x').append(v.height)
                    .append(" bg=").append(bg).append(' ').append(mg)
                    .append('\n')
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
            }
            walk(activity.window.decorView, 0)
            if (!out.contains("QzoneConciseHeaderView")) return
            java.io.File(HostInfo.hostContext.filesDir, "qfun_bg_dump.txt").writeText(out.toString())
        }
    }
}
