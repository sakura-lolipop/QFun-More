package me.yxp.qfun.hook.purify

import android.util.SparseArray
import androidx.core.util.forEach
import com.tencent.mobileqq.aio.msglist.holder.component.timestamp.AIOTimestampComponent
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@HookItemAnnotation(
    "隐藏系统时间",
    "隐藏聊天界面中居中显示的系统时间",
    HookCategory.PURIFY
)
object HideSystemTime : BaseSwitchHookItem(), DexKitTask {

    private const val TARGET_CLASS = $$"com.tencent.mobileqq.aio.msglist.holder.AIOItemComponentProvider$mComponentList$2"

    override fun onHook() {
        // DexKit 缓存未命中（首次启动未扫描）时回退原硬编码（保留 toClass 缺类抛错语义）
        (runCatching { requireClass("componentListLambda") }.getOrNull() ?: TARGET_CLASS.toClass)
            .findMethod {
                returnType = SparseArray::class.java
                name = "invoke"
            }.hookAfter(this) { param ->
                val array = param.result as SparseArray<*>
                array.forEach { k, v ->
                    if (v is AIOTimestampComponent) {
                        array.remove(k)
                        return@hookAfter
                    }
                }
            }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "componentListLambda" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.msglist.holder")
            matcher {
                // 该包下存在结构完全相同的姊妹 lambda（mLazyComponentList$2，仅 put 8 号位），
                // 以 invoke 内使用的组件类型常量 13 区分（9.3.15 双包验证唯一）
                superClass("kotlin.jvm.internal.Lambda")
                methods {
                    add {
                        name("invoke")
                        returnType("android.util.SparseArray")
                        usingNumbers(13)
                    }
                }
            }
        }
    )
}
