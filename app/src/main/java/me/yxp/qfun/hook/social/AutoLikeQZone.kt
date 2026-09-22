package me.yxp.qfun.hook.social

import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.log.LogUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@HookItemAnnotation(
    "自动点赞QQ空间",
    "每天自动给好友最新一条动态点赞（+0.5成长值）",
    HookCategory.SOCIAL
)
object AutoLikeQZone : BaseSwitchHookItem() {

    private const val DONE_DATE_KEY = "qzone_autolike_date"

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

        // 等登录态：带总超时轮询，不许死等（QStory 是 while + sleep 死等，不学）
        if (!QZoneApi.awaitTicket(AWAIT_TICKET_TIMEOUT)) {
            LogUtils.d("[$name] 等待 qzone 登录态超时，下个 tick 重试")
            return
        }
        delay(SETTLE_DELAY) // 登录态刚就绪稳一下

        // 只取最新一条好友动态（vFeeds[0]）——QStory 的风控姿态，不做遍历全赞
        val keys = QZoneApi.getActiveFeeds()
        if (keys == null) {
            LogUtils.d("[$name] 空间动态列表为0，放弃本次")
            return
        }

        val result = QZoneApi.dolike(keys)
        when {
            result.isOk -> {
                prefs.edit { putString(DONE_DATE_KEY, today) }
                LogUtils.d("[$name] 自动点赞成功")
            }
            result.isAlreadyLiked -> {
                // "已赞过"类业务码也记完成，避免当天反复戳
                prefs.edit { putString(DONE_DATE_KEY, today) }
                LogUtils.d("[$name] 今日已赞过（ret=${result.ret} ${result.msg}）")
            }
            else -> LogUtils.d("[$name] 点赞失败 ret=${result.ret} msg=${result.msg}，下个 tick 重试")
        }
    }
}
