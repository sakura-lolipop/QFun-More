package me.yxp.qfun.hook.social

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.setObject
import java.lang.reflect.Method

@HookItemAnnotation(
    "移除收藏预览限制",
    "移除QQ收藏内容因安全检测被限制预览的限制",
    HookCategory.SOCIAL,
    process = "All"
)
object RemoveFavPreviewLimit : BaseSwitchHookItem() {

    private lateinit var getFavoriteData: Method

    override fun onInit(): Boolean {
        val serviceClass = "com.qqfav.FavoriteService".clazz ?: return false
        val dataClass = "com.qqfav.data.FavoriteData".clazz ?: return false

        getFavoriteData = serviceClass.findMethod {
            returnType = dataClass
            paramTypes(long, boolean)
        }
        return super.onInit()
    }

    override fun onHook() {
        getFavoriteData.hookAfter(this) { param ->
            param.result?.setObject("mSecurityBeat", 0L)
        }
    }
}
