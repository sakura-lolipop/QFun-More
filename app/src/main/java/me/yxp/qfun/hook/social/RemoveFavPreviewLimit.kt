package me.yxp.qfun.hook.social

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.setObject
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

@HookItemAnnotation(
    "移除收藏预览限制",
    "移除QQ收藏内容因安全检测被限制预览的限制",
    HookCategory.SOCIAL,
    process = "All"
)
object RemoveFavPreviewLimit : BaseSwitchHookItem(), DexKitTask {

    private lateinit var getFavoriteData: Method

    override fun onInit(): Boolean {
        val serviceClass = runCatching { requireClass("favoriteService") }.getOrNull()
            ?: "com.qqfav.FavoriteService".clazz
        val dataClass = runCatching { requireClass("favoriteData") }.getOrNull()
            ?: "com.qqfav.data.FavoriteData".clazz
        if (serviceClass == null || dataClass == null) return false

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

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "favoriteService" to FindClass().apply {
            searchPackages("com.qqfav")
            matcher {
                usingStrings(listOf("addNewFavoriteInternal|invalid arg"), matchType = StringMatchType.Equals)
            }
        },
        "favoriteData" to FindClass().apply {
            searchPackages("com.qqfav")
            matcher {
                usingStrings(listOf("checkRebuildFileExData, rebuild FileExData list"), matchType = StringMatchType.Equals)
            }
        }
    )
}
