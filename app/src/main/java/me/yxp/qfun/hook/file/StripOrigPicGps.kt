package me.yxp.qfun.hook.file

import android.media.ExifInterface
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayList
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.reflect.clazz
import me.yxp.qfun.utils.reflect.findMethod
import me.yxp.qfun.utils.reflect.toClass

@HookItemAnnotation(
    "发送原图时擦除位置信息",
    "发送原图前将EXIF中的GPS经纬度归零，防止泄露拍摄位置（实验性）",
    HookCategory.FILE
)
object StripOrigPicGps : BaseSwitchHookItem() {

    private const val CPP_PROXY =
        "com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService\$CppProxy"

    override fun onInit(): Boolean = CPP_PROXY.clazz != null

    override fun onHook() {

        CPP_PROXY.toClass.findMethod {
            name = "sendMsg"
            paramTypes(long, Contact::class.java, list, map, null)
        }.hookBefore(this) { param ->

            val elements = param.args[2] as? ArrayList<MsgElement> ?: return@hookBefore

            elements.forEach { element ->
                val pic = element.picElement ?: return@forEach
                if (!pic.original) return@forEach
                val src = pic.sourcePath
                if (src.isNullOrEmpty() || !File(src).exists()) return@forEach

                runCatching {
                    // 不改用户本地原文件：写临时副本擦除 GPS 后替换 sourcePath
                    val tmp = File.createTempFile("qfun_exif_", ".jpg", HostInfo.hostContext.cacheDir)
                    FileInputStream(src).use { input ->
                        FileOutputStream(tmp).use { output -> input.copyTo(output) }
                    }
                    val exif = ExifInterface(tmp.absolutePath)
                    exif.setAttribute("GPSLongitude", "0")
                    exif.setAttribute("GPSLatitude", "0")
                    exif.saveAttributes()
                    pic.sourcePath = tmp.absolutePath
                }.onFailure {
                    LogUtils.e(this@StripOrigPicGps, it)
                }
            }
        }
    }
}
