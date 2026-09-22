package me.yxp.qfun.hook.purify

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.doNothing
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod

@HookItemAnnotation(
    "隐藏通讯录卡片",
    "隐藏新朋友列表中的通讯录推荐卡片",
    HookCategory.PURIFY
)
object HideContactCard : BaseSwitchHookItem() {

    private const val CONTACT_GUIDE_BUILDER =
        "com.tencent.mobileqq.newfriend.ui.builder.NewFriendBindContactGuideBuilderV3"

    override fun onInit() = HostInfo.isQQ && CONTACT_GUIDE_BUILDER.clazz != null

    override fun onHook() {
        // QQ 9.3.15 实测签名：h(I, Landroid/view/View;)Landroid/view/View;（老版为 h(int)，已按实证放宽）
        CONTACT_GUIDE_BUILDER.clazz
            ?.findMethod {
                name = "h"
                paramCount = 2
            }
            ?.doNothing(this)
    }
}
