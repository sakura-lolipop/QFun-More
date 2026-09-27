package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethods
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "净化主页横幅新年广告",
    "净化主页消息顶部的新年活动横幅广告",
    HookCategory.PURIFY
)
object PurifyNewYearBanner : BaseSwitchHookItem(), DexKitTask {

    private const val BANNER_MODULE =
        "com.tencent.mobileqq.springhb.entry.module.SpringTabBannerModule"
    private const val BANNER_DATA =
        "com.tencent.mobileqq.springhb.entry.model.MsgTabBannerData"

    // DEXKIT-EXEMPT: QbossADImmersionBannerManager 在 9.3.15 双包均不存在，
    // 原代码 .clazz?. 语法本就静默跳过，无迁移收益
    private const val QBOSS_AD_BANNER_MANAGER =
        "cooperation.vip.qqbanner.QbossADImmersionBannerManager"

    private const val VAS_AD_BANNER_PROCESSOR =
        "com.tencent.mobileqq.activity.recent.bannerprocessor.VasADBannerProcessor"

    private var bannerModuleClass: Class<*>? = null
    private var bannerDataClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        bannerModuleClass =
            runCatching { requireClass("springTabBannerModule") }.getOrNull() ?: BANNER_MODULE.clazz
        bannerDataClass =
            runCatching { requireClass("msgTabBannerData") }.getOrNull() ?: BANNER_DATA.clazz
        return bannerModuleClass != null
    }

    override fun onHook() {
        // QStory: hook SpringTabBannerModule 中参数为 MsgTabBannerData 的处理方法，直接拦截
        val dataClass = bannerDataClass ?: return
        bannerModuleClass
            ?.findMethods {
                returnType = void
                paramTypes(dataClass)
            }
            ?.forEach { it.doNothing(this) }

        QBOSS_AD_BANNER_MANAGER.clazz
            ?.findMethods {
                name = "doOnCreate"
                returnType = void
            }
            ?.forEach { it.doNothing(this) }

        (runCatching { requireClass("vasADBannerProcessor") }.getOrNull()
            ?: VAS_AD_BANNER_PROCESSOR.clazz)
            ?.findMethods {
                name = "updateBanner"
                returnType = void
            }
            ?.forEach { it.doNothing(this) }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "springTabBannerModule" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.springhb.entry")
            matcher {
                usingStrings(listOf("onMsgTabBannerShow, data = "), matchType = StringMatchType.Equals)
            }
        },
        // toString 前缀串；"MsgTabBannerData{id='" 会同命中 MsgTabTitleIconData，故用独有字段串
        "msgTabBannerData" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.springhb.entry")
            matcher {
                usingStrings(listOf("', liuhaiBgUrl='"), matchType = StringMatchType.Equals)
            }
        },
        "vasADBannerProcessor" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.activity.recent")
            matcher {
                usingStrings(listOf("[Diag-715] handleCustomBanner empty, hide banner"), matchType = StringMatchType.Equals)
            }
        }
    )
}
