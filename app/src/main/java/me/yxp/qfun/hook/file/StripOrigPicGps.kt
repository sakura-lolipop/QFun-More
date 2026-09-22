package me.yxp.qfun.hook.file

import android.media.ExifInterface
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.api.SendMsgListener
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo

@HookItemAnnotation(
    "发送原图时擦除位置信息",
    "发送原图前移除EXIF中的GPS信息，防止泄露拍摄位置（不改本地原文件）",
    HookCategory.FILE
)
object StripOrigPicGps : BaseSwitchHookItem(), SendMsgListener {

    override fun onSend(elements: ArrayList<MsgElement>) {
        elements.forEach { element ->
            val pic = element.picElement ?: return@forEach
            if (!pic.original) return@forEach
            val src = pic.sourcePath
            if (src.isNullOrEmpty()) return@forEach

            runCatching {
                val srcFile = File(src)
                if (!srcFile.exists()) return@forEach

                // 无 GPS 标签就不复制文件，零开销直通
                val srcExif = ExifInterface(src)
                if (GPS_TAGS.none { srcExif.getAttribute(it) != null }) return@forEach

                // 不改用户本地原文件：写临时副本移除 GPS 后替换发送路径
                val tmp = File.createTempFile("qfun_exif_", ".jpg", HostInfo.hostContext.cacheDir)
                FileInputStream(srcFile).use { input ->
                    FileOutputStream(tmp).use { output -> input.copyTo(output) }
                }
                val tmpExif = ExifInterface(tmp.absolutePath)
                GPS_TAGS.forEach { tmpExif.setAttribute(it, null) }
                tmpExif.saveAttributes()
                pic.sourcePath = tmp.absolutePath
            }.onFailure {
                LogUtils.e(this@StripOrigPicGps, it)
            }
        }
    }

    private val GPS_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
    )
}
