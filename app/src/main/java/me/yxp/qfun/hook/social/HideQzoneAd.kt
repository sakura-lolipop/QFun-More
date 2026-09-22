package me.yxp.qfun.hook.social

import android.content.Context
import android.view.View
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.reflect.clazz

@HookItemAnnotation(
    "隐藏QQ空间广告",
    "过滤空间信息流广告卡片、相册推荐广告与推荐广告位",
    HookCategory.SOCIAL
)
object HideQzoneAd : BaseSwitchHookItem() {

    private val adItemViewNames = listOf(
        "com.qzone.reborn.feedx.itemview.ad.QZoneAdBaseFeedItemView",
        "com.qzone.reborn.feedpro.itemview.ad.QZoneAdBaseMediaFeedProItemView",
        "com.qzone.reborn.feedpro.itemview.QzoneFeedProGeneralBigCardItemView",
        "com.qzone.reborn.feedpro.itemview.QZoneAdFeedProForwardMixPicVideoItemView",
        "com.qzone.reborn.feedpro.widget.comment.QZoneFeedProDetailBottomAdBlockView",
        "com.qzone.reborn.feedx.itemview.ad.QZoneAdRewardFeedItemView",
    )

    private const val ALBUM_ADV_CONTROLLER = "com.tencent.mobileqq.vas.adv.qzone.logic.AlbumRecommendAdvController"
    private const val AD_FEED_DATA_EXT = "com.qzone.proxy.feedcomponent.model.gdt.QZoneAdFeedDataExtKt"

    private val albumAdFields = listOf(
        "advImageUrl", "advLogoUrl", "videoUrl", "videoReportUrl",
        "negativeFeedbackUrl", "clickUrl",
    )

    private var hookedCount = 0

    override fun onHook() {
        hookedCount = 0
        hookAdItemViewConstructors()
        hookAlbumRecommendAdv()
        hookRecommendAdGate()
        if (hookedCount == 0) {
            LogUtils.d("$name: 未命中任何空间广告 hook 点（QQ 版本变动或非 QQ 进程）")
        }
    }

    private fun hookAdItemViewConstructors() {
        for (name in adItemViewNames) {
            runCatching {
                val clazz = name.clazz ?: return@runCatching
                val ctor = clazz.declaredConstructors.firstOrNull {
                    it.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(it.parameterTypes[0])
                } ?: return@runCatching
                // QStory 在构造前 GONE，但构造链可能重置标志位；改在构造完成后设置更稳
                ctor.hookAfter(this) { param ->
                    val view = param.thisObject as? View ?: return@hookAfter
                    view.visibility = View.GONE
                    view.layoutParams?.let { lp ->
                        lp.width = 0
                        lp.height = 0
                        view.layoutParams = lp
                    }
                }
                hookedCount++
            }.onFailure { LogUtils.e("$name hook 失败", it) }
        }
    }

    private fun hookAlbumRecommendAdv() {
        runCatching {
            val controller = ALBUM_ADV_CONTROLLER.clazz ?: return@runCatching
            val method = controller.declaredMethods.firstOrNull {
                it.name == "initAndRenderData" && it.parameterCount == 1
            } ?: return@runCatching
            method.hookBefore(this) { param ->
                val data = param.args.getOrNull(0) ?: return@hookBefore
                for (fieldName in albumAdFields) {
                    setStringField(data, fieldName, "")
                }
            }
            hookedCount++
        }.onFailure { LogUtils.e("AlbumRecommendAdvController hook 失败", it) }
    }

    private fun hookRecommendAdGate() {
        runCatching {
            val ext = AD_FEED_DATA_EXT.clazz ?: return@runCatching
            val method = ext.declaredMethods.firstOrNull {
                it.name == "isShowingRecommendAd" && it.parameterCount == 1
            } ?: return@runCatching
            // 让 QQ 误以为当前 feed 已展示过推荐广告，从而不再插入第二条
            method.hookBefore(this) { param -> param.result = true }
            hookedCount++
        }.onFailure { LogUtils.e("QZoneAdFeedDataExtKt hook 失败", it) }
    }

    private fun setStringField(target: Any, fieldName: String, value: String) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            runCatching {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                if (field.type == String::class.java) field.set(target, value)
            }
            clazz = clazz.superclass
        }
    }
}
