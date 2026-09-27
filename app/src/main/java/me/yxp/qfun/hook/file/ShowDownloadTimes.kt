package me.yxp.qfun.hook.file

import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.view.descendants
import androidx.core.widget.doAfterTextChanged
import com.tencent.mobileqq.troop.file.data.TroopFileShowAdapter
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookAfter
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.json.getProtoValueAs
import me.yxp.qfun.utils.json.isMessage
import me.yxp.qfun.utils.json.isMessageInstance
import me.yxp.qfun.utils.json.replaceProtoValue
import me.yxp.qfun.utils.reflect.ClassUtils
import me.yxp.qfun.utils.reflect.ReflectCache
import me.yxp.qfun.utils.reflect.callMethod
import me.yxp.qfun.utils.dexkit.DexKitTask
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.findMethods
import me.yxp.qfun.utils.reflect.getObject
import me.yxp.qfun.utils.reflect.setObject
import me.yxp.qfun.utils.reflect.toClass
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher
import java.lang.reflect.Method

@HookItemAnnotation(
    "显示文件下载次数",
    "群文件显示具体下载次数",
    HookCategory.FILE
)
object ShowDownloadTimes : BaseSwitchHookItem(), DexKitTask {

    private var getView: Method? = null

    private val syncExtraInfoMethods = mutableListOf<Method>()

    private var countMap: Map<String, Int>? = null

    // DEXKIT-EXEMPT: 插件 troop_plugin.apk 内的类名（非宿主 dex），供宿主类缺失时兜底加载
    private const val ADAPTER_CLASS_NAME = "com.tencent.mobileqq.troop.data.TroopFileShowAdapter"

    private val downloadTimesRegex = Regex("uint32_download_times=(\\d+)")
    private val fileIdRegex = Regex("fileId=([^,]+)")

    private var groupFileListRepo: Class<*>? = null

    override fun onInit(): Boolean {

        if (!isInTargetProcess()) return false

        // 保留编译期桩引用 + 插件兜底：适配器类可能由 troop_plugin.apk 提供，
        // DexKit 只扫宿主 APK，扫不到插件 dex，故不走 DexKit
        runCatching {
            TroopFileShowAdapter::class.java
        }.recoverCatching {
            ClassUtils.loadFromPlugin("troop_plugin.apk", ADAPTER_CLASS_NAME)
        }.onSuccess {
            getView = it.findMethod {
                name = "getView"
            }
        }

        groupFileListRepo = runCatching { requireClass("groupFileListRepo") }.getOrNull()
        groupFileListRepo
            ?.findMethods { paramTypes(string, null, "kotlin.coroutines.Continuation".toClass) }
            ?.filter { it.parameterTypes[1].isMessage }
            ?.let { syncExtraInfoMethods.addAll(it) }

        return getView != null || syncExtraInfoMethods.isNotEmpty()
    }

    override fun onHook() {

        getView?.hookAfter(this) { param ->
            if (param.args[1] != null) return@hookAfter
            val adapter = param.thisObject as BaseAdapter
            val fileInfo = adapter.getItem(param.args[0] as Int)

            val view = param.result as ViewGroup
            val descText = view.descendants
                .filterIsInstance<TextView>()
                .first { it.tag == fileInfo }
            descText.doAfterTextChanged { s ->
                if (s?.toString()?.endsWith("次") != false) return@doAfterTextChanged

                val tag = descText.tag
                extract(tag.toString(), downloadTimesRegex)?.let {
                    if (it != "0") s.append(" · $it 次")
                }

            }

        }

        syncExtraInfoMethods.forEach { method ->
            method.hookBefore(this) { param ->
                val message = param.args[1] ?: return@hookBefore
                val fileList = message.getProtoValueAs<List<*>>(5)
                countMap = fileList?.filterNotNull()?.associate { file ->
                    val fileId = file.getProtoValueAs<String>(3, 1) ?: ""
                    val count = file.getProtoValueAs<Int>(3, 9) ?: 0
                    fileId to count
                }
            }
            method.hookAfter(this) { param ->
                val localFileList = param.result as? List<*>
                localFileList?.takeIf { it.isNotEmpty() } ?: return@hookAfter
                val isNew = localFileList[0].toString().contains("QQGroupFileInfo")

                localFileList.filterNotNull().forEach {
                    if (isNew) {
                        val fileId = extract(it.toString(), fileIdRegex)
                        countMap?.get(fileId)?.let { count ->
                            val name = it.getObject("x")
                            it.setObject("x", "$name · $count 次")
                        }
                    } else {
                        it.originalModel?.let { model ->
                            val fileId = model.getProtoValueAs<String>(1, 1)
                            countMap?.get(fileId)?.let { count ->
                                model.replaceProtoValue(6, 2) { name ->
                                    "$name · $count 次"
                                }
                            }
                        }
                    }
                }

            }
        }

    }

    private fun extract(source: String, regex: Regex): String? {
        return regex.find(source)?.groupValues?.get(1)
    }

    private val Any.originalModel: Any?
        get() {
            val modelField = ReflectCache.getField("$name->originalModel") {
                this.javaClass.declaredFields
                    .filter {
                        it.type.name == "androidx.compose.runtime.MutableState"
                    }.single {
                        it.isAccessible = true
                        it.get(this)?.callMethod("getValue").isMessageInstance
                    }
            }

            return modelField?.get(this)?.callMethod("getValue")

        }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "groupFileListRepo" to FindClass().apply {
            searchPackages("group_file.group_file_common.repo")
            matcher {
                usingStrings("[syncExtraInfoV2]: start", "[syncExtraInfoV2]: end, count=")
            }
        }
    )

}
