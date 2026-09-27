package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation(
    "隐藏通讯录卡片",
    "隐藏新朋友列表中的通讯录推荐卡片",
    HookCategory.PURIFY
)
object HideContactCard : BaseSwitchHookItem(), DexKitTask {

    private const val CONTACT_GUIDE_BUILDER =
        "com.tencent.mobileqq.newfriend.ui.builder.NewFriendBindContactGuideBuilderV3"

    private var contactGuideBuilderClass: Class<*>? = null

    override fun onInit(): Boolean {
        if (!HostInfo.isQQ) return false
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码，行为与迁移前一致
        contactGuideBuilderClass =
            runCatching { requireClass("contactGuideBuilder") }.getOrNull() ?: CONTACT_GUIDE_BUILDER.clazz
        return contactGuideBuilderClass != null
    }

    override fun onHook() {
        // QQ 9.3.15 实测签名：h(I, Landroid/view/View;)Landroid/view/View;（老版为 h(int)，已按实证放宽）
        contactGuideBuilderClass
            ?.findMethod {
                name = "h"
                paramCount = 2
            }
            ?.doNothing(this)
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "contactGuideBuilder" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.newfriend")
            matcher {
                // 全角冒号日志串（9.3.15 双包全 dex 验证唯一）
                usingStrings("leakOptSwitch：")
            }
        }
    )
}
