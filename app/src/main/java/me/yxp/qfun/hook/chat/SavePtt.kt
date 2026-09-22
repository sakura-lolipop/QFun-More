package me.yxp.qfun.hook.chat

import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.api.MenuClickListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.plugin.bean.MsgData
import me.yxp.qfun.utils.io.FileUtils
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.qq.Toasts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HookItemAnnotation(
    "保存语音",
    "语音消息长按菜单出现保存语音项，导出 silk/amr 原文件防止被清理",
    HookCategory.CHAT
)
object SavePtt : BaseSwitchHookItem(), MenuClickListener {

    override val menuKey: String get() = "[QFun],$name,保存语音,,6"

    private val invalidChars = Regex("[\\\\/:*?\"<>|]")

    override fun onClick(msgData: MsgData) {

        val ptt = msgData.data.elements.firstNotNullOfOrNull { it.pttElement } ?: return

        ModuleScope.launchIO(name) {
            val src = ptt.filePath
            if (src.isNullOrEmpty() || !File(src).exists()) {
                Toasts.toast("语音可能尚未加载完毕")
                return@launchIO
            }

            val rawName = (ptt.fileName ?: "").ifBlank {
                SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(Date())
            }
            val name = invalidChars.replace(rawName, "_")

            val dir = File("${QQCurrentEnv.currentDir}Voice").apply { mkdirs() }

            var dest = File(dir, name)
            var index = 1
            while (dest.exists()) {
                val base = name.substringBeforeLast('.')
                val ext = name.substringAfterLast('.', "")
                dest = File(dir, if (ext.isEmpty()) "$base ($index)" else "$base ($index).$ext")
                index++
            }

            if (FileUtils.copy(File(src), dest)) {
                Toasts.toast("语音已保存 ${dest.name}")
            } else {
                Toasts.toast("语音保存失败")
            }
        }
    }
}
