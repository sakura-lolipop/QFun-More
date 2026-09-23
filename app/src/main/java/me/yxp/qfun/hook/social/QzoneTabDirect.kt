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
 * 底栏"动态"Tab 直达 QQ 空间好友动态页。
 *
 * 逆向结论（QQ 9.3.15，base.apk classes4.dex / tinker classes4.dex 均验证）：
 * - 底栏框架每次启动由 FrameControllerInjectImpl.s(...) 构建 FrameInitBean
 *   (com.tencent.mobileqq.activity.home.impl.h)，随后调用
 *   TabFrameControllerImpl.checkBusinessSwitch(h) → 遍历 sFrameBusinessCallbacks，
 *   其中 QZONE 业务 com.tencent.mobileqq.activity.framebusiness.s.r(h) 询问
 *   cooperation.qzone.api.QZoneApiProxy.needShowQzoneFrame(Context, AppRuntime)
 *   （静态门面 → QRoute → IQZoneApiProxy）并把结果写入 h.e("QZONE"/"LEBA", ...)。
 *   hook 成功但闸门不再被执行/时序不匹配时状态永远 LEBA=true，即聚合页
 *   com.tencent.mobileqq.leba.Leba。
 * - checkBusinessSwitch 返回后，framebusiness.s.b(...)（"setQzoneLebaTab"）读取
 *   h.c("QZONE")：true → ILebaFrameApi.showQzoneFrame() + addFrame(QzoneFrame)，
 *   QzoneFrame 内嵌 com.qzone.reborn.feedx.fragment.QZoneFeedX*FrameFragment
 *   即好友动态页；false → showLebaFrame() 聚合页。
 *
 * 因此新方案在 checkBusinessSwitch 之后直接覆写状态 QZONE=true / LEBA=false，
 * 走 QQ 原生"showQzoneFrame"分支，无闪现、无 Activity 跳转。
 * 状态类 (home/impl/h) 已混淆，方法按签名 (String, boolean)->void 动态定位。
 */
@HookItemAnnotation(
    "底栏直接打开空间动态",
    "点击底栏动态Tab时直接显示空间好友动态（QQ 9.x 覆写底栏框架 QZONE/LEBA 状态，空间页缺失时自动回退聚合页）",
    HookCategory.SOCIAL
)
object QzoneTabDirect : BaseSwitchHookItem() {

    private const val QZONE_API_PROXY = "com.tencent.qzonehub.api.impl.QZoneApiProxyImpl"
    private const val APP_RUNTIME = "mqq.app.AppRuntime"
    private const val TAB_FRAME_CONTROLLER =
        "com.tencent.mobileqq.activity.home.impl.TabFrameControllerImpl"
    private const val QZONE_FRAME = "com.tencent.mobileqq.activity.leba.QzoneFrame"
    private const val MIN_VERSION_CODE = 12290

    private const val KEY_QZONE = "QZONE"
    private const val KEY_LEBA = "LEBA"

    /** 老路：needShowQzoneFrame 闸门（部分旧版本仍有效）。 */
    private var gateMethod: Method? = null

    /** 新路：TabFrameControllerImpl.checkBusinessSwitch(FrameInitBean)。 */
    private var checkBusinessSwitch: Method? = null

    /** FrameInitBean.e(String, boolean)：状态写入（混淆名，按签名定位）。 */
    private var stateSetter: Method? = null

    override fun onInit(): Boolean {
        if (HostInfo.isQQ && HostInfo.versionCode < MIN_VERSION_CODE) return false

        val runtime = APP_RUNTIME.clazz
        gateMethod = QZONE_API_PROXY.clazz?.declaredMethods?.firstOrNull {
            it.name == "needShowQzoneFrame" && it.parameterCount == 2 &&
                Context::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                runtime != null && it.parameterTypes[1].isAssignableFrom(runtime)
        }

        // 空间框类缺失（如组件被裁剪）时只能走老闸门，否则保留聚合页兜底
        if (QZONE_FRAME.clazz == null) {
            LogUtils.d("$name: QzoneFrame 缺失，仅尝试 needShowQzoneFrame 闸门")
            return gateMethod != null
        }

        val controller = TAB_FRAME_CONTROLLER.clazz ?: return gateMethod != null
        checkBusinessSwitch = controller.declaredMethods.firstOrNull {
            it.name == "checkBusinessSwitch" && it.parameterCount == 1
        } ?: return gateMethod != null

        val stateClass = checkBusinessSwitch!!.parameterTypes[0]
        stateSetter = stateClass.declaredMethods.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE && it.parameterCount == 2 &&
                it.parameterTypes[0] == String::class.java &&
                it.parameterTypes[1] == Boolean::class.javaPrimitiveType
        } ?: return gateMethod != null

        return true
    }

    override fun onHook() {
        checkBusinessSwitch?.hookAfter(this) { param ->
            val state = param.args.firstOrNull() ?: return@hookAfter
            stateSetter?.invoke(state, KEY_QZONE, true)
            stateSetter?.invoke(state, KEY_LEBA, false)
        }
        gateMethod?.hookBefore(this) { param ->
            param.result = true
        }
    }
}
