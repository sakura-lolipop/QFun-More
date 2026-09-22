package me.yxp.qfun.hook.chat

import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.api.AIOViewUpdateListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem

@HookItemAnnotation(
    "灰字提示显示QQ号",
    "消息下方以灰字显示发送者的QQ号",
    HookCategory.CHAT
)
object ShowUinGrayTip : BaseSwitchHookItem(), AIOViewUpdateListener {

    private const val VIEW_TAG = "QFunUinTipView"

    override fun onUpdate(frameLayout: FrameLayout, msgRecord: MsgRecord) {

        var tipView = frameLayout.findViewWithTag<TextView>(VIEW_TAG)
        if (tipView == null) {
            tipView = TextView(frameLayout.context).apply {
                tag = VIEW_TAG
                textSize = 10f
                setTextColor(Color.GRAY)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = 10
                }
            }
            frameLayout.addView(tipView)
        }

        val text = if (msgRecord.senderUin > 0) "QQ:${msgRecord.senderUin}" else ""
        tipView.text = text
        tipView.isVisible = text.isNotEmpty()
    }
}
