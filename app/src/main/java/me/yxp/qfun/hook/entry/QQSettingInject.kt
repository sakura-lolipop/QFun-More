package me.yxp.qfun.hook.entry

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import me.yxp.qfun.R
import me.yxp.qfun.activity.PluginActivity
import me.yxp.qfun.activity.SettingActivity
import me.yxp.qfun.activity.StorageCleanActivity
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseApiHookItem
import me.yxp.qfun.hook.base.Listener
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.ClassUtils
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.getStaticObject
import me.yxp.qfun.utils.reflect.newInstanceWithArgs
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Proxy

@HookItemAnnotation("QQ设置入口")
object QQSettingInject : BaseApiHookItem<Listener>(), DexKitTask {

    private const val TOP_TITLE = "模块"
    private const val BOTTOM_TITLE = ""
    private const val MODULE_ORDER = 10

    @delegate:SuppressLint("DiscouragedApi")
    private val deleteIconRes by lazy {
        try {
            HostInfo.hostContext.resources.getIdentifier(
                "qui_delete_light_selector",
                "drawable",
                HostInfo.packageName
            )
        } catch (_: Exception) {
            R.drawable.ic_launcher
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun loadHook() {

        val providerList = mutableListOf<Class<*>?>()
        // DEXKIT-EXEMPT: 仅 versionCode<12288 旧版 QQ 回退分支；两个类在 9.3.15 包内已不存在，
        // 锚点无法静态验证，保留稳定名硬编码
        val newProvider = "com.tencent.mobileqq.setting.main.NewSettingConfigProvider".clazz
        val mainProvider = "com.tencent.mobileqq.setting.main.MainSettingConfigProvider".clazz

        if (HostInfo.isQQ && HostInfo.versionCode >= 12288) {
            val obfProvider = requireClass("ConfigProvider")
            providerList.add(obfProvider)
        } else {
            providerList.add(newProvider)
            providerList.add(mainProvider)
        }

        if (providerList.isEmpty()) throw ClassNotFoundException("SettingConfigProvider")

        val itemClass = requireClass("ItemProcessor")

        val setOnClickListener = itemClass.findMethod {
            returnType = void
            paramTypes("kotlin.jvm.functions.Function0".toClass)
        }

        providerList.forEach {
            it?.findMethod {
                returnType = list
                paramTypes(context)
            }?.hookAfter(this) { param ->
                val context = param.args[0] as Context
                val result = param.result as MutableList<Any>

                val settingEntry = makeItem(
                    itemClass,
                    context,
                    MODULE_ORDER,
                    "QFun",
                    R.drawable.ic_launcher
                )
                setOnClickListener.invoke(
                    settingEntry, makeProxy(
                        context,
                        SettingActivity::class.java
                    )
                )

                val pluginEntry = makeItem(
                    itemClass,
                    context,
                    MODULE_ORDER,
                    "JavaPlugin",
                    R.drawable.ic_float_ball
                )
                setOnClickListener.invoke(
                    pluginEntry, makeProxy(
                        context,
                        PluginActivity::class.java
                    )
                )

                val cleanEntry = makeItem(
                    itemClass,
                    context,
                    MODULE_ORDER,
                    "缓存清理",
                    deleteIconRes
                )
                setOnClickListener.invoke(
                    cleanEntry, makeProxy(
                        context,
                        StorageCleanActivity::class.java
                    )
                )

                result.add(
                    1,
                    result[0].javaClass.newInstanceWithArgs(
                        listOf(settingEntry, pluginEntry, cleanEntry),
                        TOP_TITLE,
                        BOTTOM_TITLE,
                        0,
                        null
                    )
                )

            }

        }

    }

    private fun makeItem(itemClass: Class<*>, vararg args: Any?): Any {
        return runCatching {
            itemClass.newInstanceWithArgs(*args, null)
        }.getOrElse {
            itemClass.newInstanceWithArgs(*args)
        }
    }

    private fun makeProxy(
        context: Context,
        activityClass: Class<*>
    ): Any {

        val unit = "kotlin.Unit".toClass.getStaticObject("INSTANCE")

        return Proxy.newProxyInstance(
            ClassUtils.hostClassLoader,
            arrayOf("kotlin.jvm.functions.Function0".toClass)
        ) { _, method, _ ->
            if (method.name == "invoke") {
                runCatching {
                    context.startActivity(Intent(context, activityClass))
                }.onFailure {
                    LogUtils.e(this, it)
                }
            }
            unit
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> {
        val item = if (HostInfo.isQQ && HostInfo.versionCode >= 14498) {
            FindClass().apply {
                matcher {
                    usingStrings(listOf("context", "leftText", "SimpleItemProcessor"), matchType = StringMatchType.Equals)
                }
            }
        } else {
            FindClass().apply {
                searchPackages("com.tencent.mobileqq.setting.processor")
                matcher {
                    usingStrings(listOf("context", "leftText"), matchType = StringMatchType.Equals)
                }
            }
        }

        return mapOf(
            "ConfigProvider" to FindClass().apply {
                searchPackages("com.tencent.mobileqq.setting.main")
                matcher {
                    superClass("com.tencent.mobileqq.setting.processor.SettingConfigProvider")
                }
            },
            "ItemProcessor" to item
        )
    }

}
