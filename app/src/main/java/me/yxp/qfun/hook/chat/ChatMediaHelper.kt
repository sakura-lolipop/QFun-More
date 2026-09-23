package me.yxp.qfun.hook.chat

import me.yxp.qfun.hook.api.OnGetRKey
import me.yxp.qfun.plugin.bean.MsgData

/**
 * 聊天媒体类菜单功能的共享工具：rkey 选择与图片直链拼装。
 * DownloadEmojiNew / CopyPicLink 共用。
 */
object ChatMediaHelper {

    /** 私聊/群聊各自的多媒体下载 rkey。 */
    fun rkey(msgData: MsgData): String =
        if (msgData.type == 1) OnGetRKey.friendRkey else OnGetRKey.groupRkey

    /** 提取消息中可下载的图片（md5 与原图直链成对），并拼好带 rkey 的完整链接。 */
    fun picPairs(msgData: MsgData): List<Pair<String, String>> {
        val rkey = rkey(msgData)
        return msgData.data.elements
            .mapNotNull { it.picElement }
            .filter { !it.md5HexStr.isNullOrEmpty() && !it.originImageUrl.isNullOrEmpty() }
            .map { pic ->
                val md5 = pic.md5HexStr.uppercase(java.util.Locale.getDefault())
                md5 to "https://multimedia.nt.qq.com.cn${pic.originImageUrl}$rkey"
            }
    }
}
