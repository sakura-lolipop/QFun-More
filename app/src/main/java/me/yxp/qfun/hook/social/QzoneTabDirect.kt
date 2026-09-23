package me.yxp.qfun.hook.social

import android.content.Context
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 底栏"动态"Tab 直达 QQ 空间好友动态页（完整还原原生 QZONE tab 配置）。
 *
 * 逆向结论（QQ 9.3.15，base.apk / tinker_classN.apk classes4.dex 等逐指令比对）：
 * - 底栏框架启动时由 FrameControllerImpl 注入器构建 FrameInitBean
 *   (com.tencent.mobileqq.activity.home.impl.h，内存 HashMap 状态)，随后
 *   TabFrameControllerImpl.checkBusinessSwitch(h) → dispatchCheckBusinessSwitch
 *   遍历 sFrameBusinessCallbacks（<clinit> 时从 home/w.b 注册，key=getKey()）
 *   逐个调 y.r(h)。QZONE 业务 com.tencent.mobileqq.activity.framebusiness.s.r(h)
 *   调门面 cooperation.qzone.api.QZoneApiProxy.needShowQzoneFrame(Context,
 *   AppRuntime)（public static → QRoute.api(IQZoneApiProxy) → 反射实例化
 *   QZoneApiProxyImpl），按结果写 h.e("QZONE"/"LEBA", ...)。
 *   其他业务（GUILD/ME/AI_ASSISTANT/META_DREAM…）的 r(h) 也在同一轮 dispatch
 *   内读取各自 key —— 状态必须在 dispatch 期间为 QZONE=true 才能还原原生环境。
 * - tab 槽位固定为 "LEBA"（initTabIndexByConfig 拼 tab 列表只用 LEBA），槽内
 *   挂 QzoneFrame 还是聚合页 Leba 由 framebusiness.s.b（setQzoneLebaTab）按
 *   h.c("QZONE") 决定：true → ILebaFrameApi.showQzoneFrame() + addFrame
 *   (QzoneFrame)，QzoneFrame 内嵌 com.qzone.reborn.feedx.fragment.QZoneFeedX*
 *   FrameFragment 即好友动态页。
 * - 除启动期外，reborn 空间 UI 在运行时反复调同一门面判断"是否 QZONE frame
 *   模式"：QZoneFriendFeedxTitle.C()/V(Z)（标题栏/设置入口显隐，false 会
 *   setVisibility(GONE) → 设置按钮半残）、feedx/util/j.h、feedpro
 *   QzoneFriendFeedProTitlePart.v9、presenter/ad/e.x、route/a.h 等。
 *   只翻 FrameInitBean 标志位对这些运行时调用无效 —— 这是上一版
 *   （仅覆写状态）背景割裂/设置不可用的根因。
 *
 * 因此本版三管齐下，等价于"原生配置了 QZONE 直开的用户"：
 * 1) 门面静态方法 hookBefore 返回 true（主路，覆盖 s.r 与全部 reborn 运行时调用）；
 * 2) impl 实例方法 hookBefore 返回 true（兜底 QRoute 直连 impl 的调用方）；
 * 3) checkBusinessSwitch hookAfter 强制 h("QZONE")=true / h("LEBA")=false
 *    （时序兜底：QQ 启动期 perf.startup 追踪显示 FrameInitBean 首次构建可能
 *    早于模块 hook 安装，其后 buildTabIcon/重建路径仍会再跑一次，此兜底保证
 *    槽内 frame 正确切换）。
 */
@HookItemAnnotation(
    "底栏直接打开空间动态",
    "点击底栏动态Tab时直接显示空间好友动态（还原原生 QZONE tab 配置，含标题栏/设置等运行时行为；空间页缺失时自动回退聚合页）",
    HookCategory.SOCIAL
)
object QzoneTabDirect : BaseSwitchHookItem() {

    private const val QZONE_API_PROXY = "com.tencent.qzonehub.api.impl.QZoneApiProxyImpl"
    private const val QZONE_API_PROXY_FACADE = "cooperation.qzone.api.QZoneApiProxy"
    private const val APP_RUNTIME = "mqq.app.AppRuntime"
    private const val TAB_FRAME_CONTROLLER =
        "com.tencent.mobileqq.activity.home.impl.TabFrameControllerImpl"
    private const val QZONE_FRAME = "com.tencent.mobileqq.activity.leba.QzoneFrame"
    private const val MIN_VERSION_CODE = 12290

    private const val KEY_QZONE = "QZONE"
    private const val KEY_LEBA = "LEBA"

    /** 主路：门面静态方法（reborn UI 运行时判断全部走这里）。 */
    private var facadeMethod: Method? = null

    /** 兜底：QRoute 实际实例化的 impl 实例方法。 */
    private var implGateMethod: Method? = null

    /** 时序兜底：TabFrameControllerImpl.checkBusinessSwitch(FrameInitBean)。 */
    private var checkBusinessSwitch: Method? = null

    /** FrameInitBean 状态写入（混淆名，按 (String, boolean)->void 签名定位）。 */
    private var stateSetter: Method? = null

    override fun onInit(): Boolean {
        if (HostInfo.isQQ && HostInfo.versionCode < MIN_VERSION_CODE) return false

        // 空间框类缺失（组件被裁剪等）时整体禁用，保留聚合页兜底
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
        // 1) 主路：全局闸门返回 true —— 启动期 frame 决策 + reborn UI 运行时判断
        //    （标题栏/设置/路由/广告）全部进入原生 QZONE frame 模式
        facadeMethod?.hookBefore(this) { param -> param.result = true }
        // 2) 兜底：QRoute 直连 impl 的调用方
        implGateMethod?.hookBefore(this) { param -> param.result = true }
        // 3) 时序兜底：闸门首次消费早于 hook 安装时，后续重建仍强制槽内挂 QzoneFrame
        checkBusinessSwitch?.hookAfter(this) { param ->
            val state = param.args.firstOrNull() ?: return@hookAfter
            stateSetter?.invoke(state, KEY_QZONE, true)
            stateSetter?.invoke(state, KEY_LEBA, false)
        }
        // 灰带1：空的订阅刷新头容器（36px 纯灰、无内容）—— 构造时挂 attach 监听，
        // 挂树瞬间 GONE 父容器；仅作用于空间 feed 头内部，内容非空时自动恢复
        "com.tencent.biz.subscribe.part.block.base.RefreshHeaderView".clazz
            ?.declaredConstructors
            ?.forEach { ctor ->
                ctor.hookAfter(this) { param ->
                    val v = param.thisObject as? android.view.View ?: return@hookAfter
                    v.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(av: android.view.View) {
                            var p: android.view.ViewParent = av.parent
                            var inFeedHeader = false
                            while (p is android.view.ViewGroup) {
                                if (p.javaClass.simpleName == "QzoneConciseHeaderView") {
                                    inFeedHeader = true
                                    break
                                }
                                p = p.parent
                            }
                            if (!inFeedHeader) return
                            (av.parent as? android.view.ViewGroup)?.visibility = android.view.View.GONE
                            av.post {
                                val pg = av.parent as? android.view.ViewGroup
                                if (av.height > 0 && pg != null) pg.visibility = android.view.View.VISIBLE
                            }
                        }

                        override fun onViewDetachedFromWindow(av: android.view.View) {}
                    })
                }
            }
    }
}
