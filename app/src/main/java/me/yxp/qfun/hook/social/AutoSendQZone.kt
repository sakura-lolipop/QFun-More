package me.yxp.qfun.hook.social

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.base.BaseClickableHookItem
import me.yxp.qfun.ui.components.listitems.InputItem
import me.yxp.qfun.ui.components.listitems.SwitchItem
import me.yxp.qfun.ui.components.scaffold.ConfigPageScaffold
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.Toasts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Serializable
data class AutoSendConfig(
    val isPublic: Boolean = false,
    val useCustomContent: Boolean = false,
    val customContent: String = "",
    val autoDelete: Boolean = false
)

@HookItemAnnotation(
    "每天自动发QQ空间",
    "每天自动发表一条带日期的打卡说说，默认仅自己可见，可自定义内容、公开可见与自动删除",
    HookCategory.SOCIAL
)
object AutoSendQZone : BaseClickableHookItem<AutoSendConfig>(AutoSendConfig.serializer()) {

    override val defaultConfig: AutoSendConfig = AutoSendConfig()

    private const val DONE_DATE_KEY = "qzone_autosend_date"
    private const val DEFAULT_PREFIX = "QFun每日打卡:"

    private val TICK_INTERVAL = 5.minutes
    private val AWAIT_TICKET_TIMEOUT = 30.minutes
    private const val SETTLE_DELAY = 3000L

    override fun onHook() {
        ModuleScope.launchIO(name) {
            while (isActive) {
                runCatching { tick() }.onFailure { LogUtils.e(name, it) }
                delay(TICK_INTERVAL)
            }
        }
    }

    private suspend fun tick() {
        if (!isEnable) return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (prefs.getString(DONE_DATE_KEY, "") == today) return

        // 等登录态：带总超时轮询，不许死等
        if (!QZoneApi.awaitTicket(AWAIT_TICKET_TIMEOUT)) {
            LogUtils.d("[$name] 等待 qzone 登录态超时，下个 tick 重试")
            return
        }
        delay(SETTLE_DELAY) // 登录态刚就绪稳一下

        val content = buildContent(today)
        val result = QZoneApi.publish(content, config.isPublic)
        if (!result.isSuccess) {
            // 业务失败不写完成标记，下个 tick 重试（QStory 写在 try 尾部的缺陷，这里修正）
            LogUtils.d("[$name] 发表失败 raw=${result.raw.take(120)}，下个 tick 重试")
            return
        }

        // 发表业务成功才记完成，删除失败不影响当天完成状态
        prefs.edit { putString(DONE_DATE_KEY, today) }
        Toasts.toast("发送成功")
        LogUtils.d("[$name] 发表成功：$content")

        if (config.autoDelete) {
            val tid = result.tid
            if (tid.isNullOrEmpty()) {
                // 响应可解析但拿不到 t1_tid（可能被审核拦截），跳过删除，别拿空 tid 硬戳
                LogUtils.d("[$name] 未解析到 t1_tid，跳过自动删除")
            } else {
                val deleted = QZoneApi.delete(tid)
                LogUtils.d("[$name] 自动删除${if (deleted) "成功" else "失败"} tid=$tid")
            }
        }
    }

    /** 内容必须带日期变化量（风控红线）：默认文案拼日期，自定义内容也追加日期后缀 */
    private fun buildContent(today: String): String {
        val custom = config.customContent.trim()
        return if (config.useCustomContent && custom.isNotEmpty()) {
            "$custom $today"
        } else {
            "$DEFAULT_PREFIX$today"
        }
    }

    @Composable
    override fun ConfigContent(onDismiss: () -> Unit) {
        var isPublic by remember(config) { mutableStateOf(config.isPublic) }
        var useCustomContent by remember(config) { mutableStateOf(config.useCustomContent) }
        var customContent by remember(config) { mutableStateOf(config.customContent) }
        var autoDelete by remember(config) { mutableStateOf(config.autoDelete) }

        ConfigPageScaffold(
            title = "自动发空间配置",
            configData = AutoSendConfig(isPublic, useCustomContent, customContent, autoDelete),
            onSave = { updateConfig(it) },
            onDismiss = onDismiss,
            confirmText = "保存"
        ) { _ ->
            SwitchItem(
                title = "发送QQ空间时是否公开可见",
                checked = isPublic,
                onCheckedChange = { isPublic = it },
                description = "关闭时仅自己可见（私密发→删也能拿成长值）"
            )
            SwitchItem(
                title = "发送空间使用自定义的内容",
                checked = useCustomContent,
                onCheckedChange = { useCustomContent = it },
                description = "开启后使用下方自定义内容，发表时会自动附加当天日期"
            )
            if (useCustomContent) {
                InputItem(
                    title = "自定义内容",
                    value = customContent,
                    onValueChange = { customContent = it },
                    placeholder = "QFun每日打卡"
                )
            }
            SwitchItem(
                title = "自动删除发布的空间",
                checked = autoDelete,
                onCheckedChange = { autoDelete = it },
                description = "发表成功后自动删除刚发的内容"
            )
        }
    }
}
