package me.yxp.qfun.hook.chat

import android.media.MediaScannerConnection
import android.os.Environment
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.api.MenuClickListener
import me.yxp.qfun.hook.api.OnGetRKey
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.plugin.bean.MsgData
import me.yxp.qfun.utils.net.HttpUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.Toasts
import java.io.File
import java.util.Locale

@HookItemAnnotation(
    "表情可下载(新版)",
    "长按表情消息出现，用于弥补在9.2.20上无法下载表情的问题",
    HookCategory.CHAT
)
object DownloadEmojiNew : BaseSwitchHookItem(), MenuClickListener {

    override val menuKey: String get() = "[QFun],$name,保存表情,,2,9"

    override fun onClick(msgData: MsgData) {

        val pics = msgData.data.elements
            .mapNotNull { it.picElement }
            .filter { !it.md5HexStr.isNullOrEmpty() && !it.originImageUrl.isNullOrEmpty() }
        if (pics.isEmpty()) return

        val rkey = if (msgData.type == 1) OnGetRKey.friendRkey else OnGetRKey.groupRkey

        ModuleScope.launchIO(name) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "QQ"
            )
            dir.mkdirs()

            var success = true
            pics.forEach { pic ->
                val md5 = pic.md5HexStr.uppercase(Locale.getDefault())
                val url = "https://multimedia.nt.qq.com.cn${pic.originImageUrl}$rkey"
                val out = File(dir, "$md5.png")
                if (out.exists()) out.delete()

                if (HttpUtils.downloadSuspend(url, out.absolutePath)) {
                    MediaScannerConnection.scanFile(
                        HostInfo.hostContext,
                        arrayOf(out.absolutePath),
                        null,
                        null
                    )
                } else {
                    success = false
                }
            }

            Toasts.toast(if (success) "下载成功!" else "下载失败!")
        }
    }
}
