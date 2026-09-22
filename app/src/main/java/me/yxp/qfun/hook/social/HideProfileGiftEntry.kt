package me.yxp.qfun.hook.social

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethodOrNull
import java.lang.reflect.Method

@HookItemAnnotation(
    "屏蔽好友资料卡送礼物",
    "屏蔽好友资料卡底部工具栏中的送礼物按钮",
    HookCategory.SOCIAL
)
object HideProfileGiftEntry : BaseSwitchHookItem() {

    private const val GIFT_TEXT = "送礼物"

    private var bottomContainerCls: Class<*>? = null
    private var initViewsMethod: Method? = null

    override fun onInit(): Boolean {
        bottomContainerCls =
            "com.tencent.mobileqq.profilecard.base.container.ProfileBottomContainer".clazz
        initViewsMethod = bottomContainerCls?.findMethodOrNull {
            name = "initViews"
            paramCount = 0
        }
        return bottomContainerCls != null
    }

    override fun onHook() {
        val cls = bottomContainerCls ?: return

        val initViews = initViewsMethod
        if (initViews != null) {
            initViews.hookAfter(this) { param ->
                hideGiftEntry(param.thisObject, cls)
            }
        } else {
            // 备选：initViews 被内联/改名时改为构造后延帧遍历
            cls.declaredConstructors.forEach { ctor ->
                ctor.hookAfter(this) { param ->
                    (param.thisObject as? View)?.post {
                        (param.thisObject as? View)?.post {
                            hideGiftEntry(param.thisObject, cls)
                        }
                    }
                }
            }
        }
    }

    /** 取底部工具栏根布局（第一个 LinearLayout 类型字段），延帧到布局完成后遍历 */
    private fun hideGiftEntry(container: Any, cls: Class<*>) {
        val bar = findLinearLayoutField(container, cls) ?: return
        bar.post { walkAndHide(bar) }
    }

    private fun findLinearLayoutField(container: Any, cls: Class<*>): LinearLayout? {
        var cur: Class<*>? = cls
        while (cur != null) {
            val field = cur.declaredFields.firstOrNull {
                LinearLayout::class.java.isAssignableFrom(it.type)
            }
            if (field != null) {
                field.isAccessible = true
                return runCatching { field.get(container) as? LinearLayout }.getOrNull()
            }
            cur = cur.superclass
        }
        return null
    }

    private fun walkAndHide(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            when (child) {
                is TextView -> if (child.text.toString() == GIFT_TEXT) {
                    hideGiftText(child)
                }

                is ViewGroup -> walkAndHide(child)
            }
        }
    }

    private fun hideGiftText(textView: TextView) {
        (textView.parent as? View)?.visibility = View.GONE
        // 兜底：文案被异步重新设置时再次隐藏
        textView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isEnable && s?.toString() == GIFT_TEXT) {
                    (textView.parent as? View)?.visibility = View.GONE
                }
            }
        })
    }
}
