package me.yxp.qfun.hook.social

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.yxp.qfun.utils.net.HttpUtils
import me.yxp.qfun.utils.qq.CookieTool
import me.yxp.qfun.utils.qq.QQCurrentEnv
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * QQ空间 Web CGI 薄封装（B3 自动点赞 / B4 自动发+删 共用）。
 *
 * 仿 QStory：拿登录态 Cookie（pskey/skey）后直接 POST QZone Web CGI，
 * 全部在宿主进程内完成，不 hook QQ 私有 API。
 * 表单字段与 QStory 2.6.2 逐字段一致，见蓝图 §3.3 / §4.3 / §4.4。
 *
 * 非 HookItem：不带 @HookItemAnnotation，KSP 不会注册。
 */
internal object QZoneApi {

    const val QZONE_DOMAIN = "qzone.qq.com"

    private const val FEEDS_URL =
        "https://h5.qzone.qq.com/webapp/json/mqzone_feeds/getActiveFeeds"
    private const val DOLIKE_URL =
        "https://h5.qzone.qq.com/proxy/domain/w.qzone.qq.com/cgi-bin/likes/internal_dolike_app"
    private const val PUBLISH_URL =
        "https://user.qzone.qq.com/proxy/domain/taotao.qzone.qq.com/cgi-bin/emotion_cgi_publish_v6"
    private const val DELETE_URL =
        "https://user.qzone.qq.com/proxy/domain/taotao.qq.com/cgi-bin/emotion_cgi_delete_v6"

    class QZoneApiException(message: String) : Exception(message)

    /** 最新一条好友动态的点赞 key：unikey=orglikekey，curkey=curlikekey */
    data class LikeKeys(val unikey: String, val curkey: String)

    data class DoLikeResult(val ret: Int, val msg: String) {
        val isOk: Boolean get() = ret == 0

        /** "已赞过"类业务码也视为当天完成，避免反复戳同一条 */
        val isAlreadyLiked: Boolean
            get() = !isOk && (msg.contains("已赞") || msg.contains("已经赞"))
    }

    data class PublishResult(val isSuccess: Boolean, val tid: String?, val raw: String)

    /**
     * 等待 qzone.qq.com 域登录态就绪（pskey 非空）。
     * 带总超时轮询，超时返回 false；QStory 是死等，这里必须改进。
     */
    suspend fun awaitTicket(timeout: Duration = 30.minutes): Boolean =
        withTimeoutOrNull(timeout) {
            while (readPskey().isNullOrEmpty()) delay(1000)
            true
        } ?: false

    fun hasTicket(): Boolean = !readPskey().isNullOrEmpty()

    /**
     * 拉取好友最新一条动态的点赞 key（getActiveFeeds）。
     * @return null 表示登录态正常但动态列表为空（放弃本次，不算失败）
     * @throws QZoneApiException ret != 0 或响应不可解析
     */
    suspend fun getActiveFeeds(): LikeKeys? = withContext(Dispatchers.IO) {
        val cookie = requireCookie(withPuin = true)
        val resp = HttpUtils.postFormSuspend(
            "$FEEDS_URL?g_tk=${gtk()}",
            mapOf(
                "res_type" to "0",
                "refresh_type" to "1",
                "format" to "json",
            ),
            mapOf("Cookie" to cookie)
        )
        if (resp.isEmpty()) throw QZoneApiException("获取QQ空间列表失败：响应为空")
        val json = resp.toJSONObject()
            ?: throw QZoneApiException("获取QQ空间列表失败：响应非 JSON：${resp.take(120)}")
        val ret = json.optInt("ret", -1)
        if (ret != 0) throw QZoneApiException("获取QQ空间列表失败：ret=$ret")
        val comm = json.optJSONObject("data")
            ?.optJSONArray("vFeeds")
            ?.optJSONObject(0)
            ?.optJSONObject("comm") ?: return@withContext null
        val unikey = comm.optString("orglikekey")
        val curkey = comm.optString("curlikekey")
        if (unikey.isEmpty()) return@withContext null
        LikeKeys(unikey, curkey)
    }

    /**
     * 给单条动态点赞（internal_dolike_app，与网页端同接口）。
     * 只赞 vFeeds[0]、每天一次，不做遍历全赞（风控红线）。
     */
    suspend fun dolike(keys: LikeKeys): DoLikeResult = withContext(Dispatchers.IO) {
        val cookie = requireCookie(withPuin = true)
        val resp = HttpUtils.postFormSuspend(
            "$DOLIKE_URL?g_tk=${gtk()}",
            mapOf(
                "opuin" to QQCurrentEnv.currentUin,
                "unikey" to keys.unikey,
                "curkey" to keys.curkey,
                "appid" to "311",
                "opr_type" to "like",
                "format" to "purejson",
            ),
            mapOf("Cookie" to cookie)
        )
        val json = resp.toJSONObject()
            ?: throw QZoneApiException("点赞响应解析失败：${resp.take(120)}")
        val msg = json.optString("msg").ifEmpty { json.optString("message") }
        DoLikeResult(json.optInt("ret", -1), msg)
    }

    /**
     * 发表纯文本说说（emotion_cgi_publish_v6）。
     * 响应为 JSONP 壳 frameElement.callback({...});，解析出业务 code 与 t1_tid。
     */
    suspend fun publish(content: String, isPublic: Boolean): PublishResult =
        withContext(Dispatchers.IO) {
            val uin = QQCurrentEnv.currentUin
            val cookie = requireCookie(withPuin = false)
            val resp = HttpUtils.postFormSuspend(
                "$PUBLISH_URL?g_tk=${gtk()}",
                buildMap {
                    put("syn_tweet_verson", "1")
                    put("paramstr", "")
                    put("pic_template", "")
                    put("richtype", "0")
                    put("richval", "")
                    put("special_url", "0")
                    put("subrichtype", "0")
                    put("con", content)
                    put("feedversion", "1")
                    put("ver", "1")
                    put("ugc_right", if (isPublic) "1" else "64")
                    put("to_sign", "0")
                    put("hostuin", uin)
                    put("code_version", "1")
                    put("format", "fs")
                    put("qzreferrer", "https://user.qzone.qq.com/$uin/main")
                },
                mapOf("Cookie" to cookie)
            )
            val json = extractCallbackJson(resp)
                ?: throw QZoneApiException("未找到 callback 函数")
            val code = json.optInt("code", Int.MIN_VALUE)
            val tid = json.optString("t1_tid").takeIf { it.isNotEmpty() }
            val success = code == 0 || (code == Int.MIN_VALUE && tid != null)
            PublishResult(success, tid, resp)
        }

    /**
     * 删除自己刚发表的说说（emotion_cgi_delete_v6）。
     * @param tid 发表响应里的 t1_tid
     */
    suspend fun delete(tid: String): Boolean = withContext(Dispatchers.IO) {
        val uin = QQCurrentEnv.currentUin
        val cookie = requireCookie(withPuin = false)
        val resp = HttpUtils.postFormSuspend(
            "$DELETE_URL?g_tk=${gtk()}",
            mapOf(
                "tid" to tid,
                "t1_source" to "1",
                "hostuin" to uin,
                "code_version" to "1",
                "format" to "fs",
                "qzreferrer" to "https://user.qzone.qq.com/$uin/main",
            ),
            mapOf("Cookie" to cookie)
        )
        extractCallbackJson(resp)?.optInt("code", -1) == 0
    }

    /**
     * 解析 JSONP 壳：resp.indexOf("frameElement.callback(") 起，
     * 取第一个 '{' 到最后一个 '}' 之间的 JSON。
     */
    private fun extractCallbackJson(resp: String): JSONObject? {
        val idx = resp.indexOf("frameElement.callback(")
        if (idx == -1) return null
        val start = resp.indexOf('{', idx)
        val end = resp.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return resp.substring(start, end + 1).toJSONObject()
    }

    /**
     * Cookie 四件套拼装（QStory 原样）。
     * @param withPuin true=uin+p_uin+skey+p_skey（B3 点赞）；false=uin+skey+p_skey（B4 发/删，实测够用）
     */
    private fun requireCookie(withPuin: Boolean): String {
        val uin = QQCurrentEnv.currentUin
        val skey = runCatching { CookieTool.getSkey() }.getOrNull()
        val pskey = readPskey()
        if (skey.isNullOrEmpty() || pskey.isNullOrEmpty()) {
            throw QZoneApiException("登录态缺失（skey/pskey 为空）")
        }
        return if (withPuin) {
            "uin=o$uin; p_uin=o$uin; skey=$skey; p_skey=$pskey"
        } else {
            "uin=o$uin; skey=$skey; p_skey=$pskey"
        }
    }

    private fun readPskey(): String? =
        runCatching { CookieTool.getPskey(QZONE_DOMAIN) }.getOrNull()

    /** g_tk 必须用 qzone.qq.com 域的 pskey 计算 */
    private fun gtk(): String =
        runCatching { CookieTool.getGTK(QZONE_DOMAIN) }.getOrDefault("0")

    private fun String.toJSONObject(): JSONObject? =
        runCatching { JSONObject(this) }.getOrNull()
}
