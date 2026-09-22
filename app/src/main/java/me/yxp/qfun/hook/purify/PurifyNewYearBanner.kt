package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods

@HookItemAnnotation(
    "净化主页横幅新年广告",
    "净化主页消息顶部的新年活动横幅广告",
    HookCategory.PURIFY
)
object PurifyNewYearBanner : BaseSwitchHookItem() {

    private const val BANNER_MODULE =
        "com.tencent.mobileqq.springhb.entry.module.SpringTabBannerModule"
    private const val BANNER_DATA =
        "com.tencent.mobileqq.springhb.entry.model.MsgTabBannerData"

    override fun onInit() = HostInfo.isQQ && BANNER_MODULE.clazz != null

    override fun onHook() {
        // QStory: hook SpringTabBannerModule 中参数为 MsgTabBannerData 的处理方法，直接拦截
        val dataClass = BANNER_DATA.clazz ?: return
        BANNER_MODULE.clazz
            ?.findMethods {
                returnType = void
                paramTypes(dataClass)
            }
            ?.forEach { it.doNothing(this) }

        "cooperation.vip.qqbanner.QbossADImmersionBannerManager".clazz
            ?.findMethods {
                name = "doOnCreate"
                returnType = void
            }
            ?.forEach { it.doNothing(this) }

        "com.tencent.mobileqq.activity.recent.bannerprocessor.VasADBannerProcessor".clazz
            ?.findMethods {
                name = "updateBanner"
                returnType = void
            }
            ?.forEach { it.doNothing(this) }
    }
}
