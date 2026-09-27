package me.yxp.qfun.hook.file

import android.os.Environment
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.findField
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType

@HookItemAnnotation(
    "下载重定向",
    "重定向下载路径到Download/QQ(TIM)",
    HookCategory.FILE,
    "All"
)
object DownloadRedirect : BaseSwitchHookItem(), DexKitTask {

    override val isNeedRestart: Boolean = true

    private val redirectPath =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/${HostInfo.hostName}/"

    private var appConstants: Class<*>? = null

    override fun onInit(): Boolean {
        appConstants = runCatching { requireClass("appConstants") }.getOrNull()
            ?: "com.tencent.mobileqq.app.AppConstants".toClass
        return appConstants != null
    }

    override fun onHook() {
        val clazz = appConstants ?: return
        if (isEnable) {
            clazz.findField {
                name = "SDCARD_FILE_SAVE_PATH"
            }.set(null, redirectPath)
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "appConstants" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.app")
            matcher {
                usingStrings(
                    listOf(
                        "QQEditPic/OriginPic/",
                        "/Tencent/QQ_Secretfile/"
                    ),
                    matchType = StringMatchType.Equals
                )
            }
        }
    )

}
