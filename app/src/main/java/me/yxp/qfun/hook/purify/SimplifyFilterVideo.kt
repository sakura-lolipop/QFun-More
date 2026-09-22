package me.yxp.qfun.hook.purify

import android.view.View
import android.view.ViewGroup
import com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.findMethod
import java.lang.reflect.Method

@HookItemAnnotation(
    "精简聊天界面滤镜视频",
    "精简聊天界面快捷栏中的滤镜视频、泡泡等选项",
    HookCategory.PURIFY
)
object SimplifyFilterVideo : BaseSwitchHookItem() {

    private val removeTitles = setOf("滤镜视频", "泡泡")

    private lateinit var bindView: Method

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        bindView = PanelIconLinearLayout::class.java
            .findMethod {
                returnType = void
                paramTypes(int, string, null)
            }
        return super.onInit()
    }

    override fun onHook() {
        bindView.hookAfter(this) { param ->
            val layout = param.thisObject as? ViewGroup ?: return@hookAfter
            for (i in layout.childCount - 1 downTo 0) {
                val child: View = layout.getChildAt(i)
                val desc = child.contentDescription?.toString() ?: continue
                if (desc in removeTitles) layout.removeViewAt(i)
            }
        }
    }
}
