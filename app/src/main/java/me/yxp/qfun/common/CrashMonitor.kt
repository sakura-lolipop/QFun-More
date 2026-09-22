package me.yxp.qfun.common

import android.content.Intent
import android.os.Looper
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess
import me.yxp.qfun.activity.CrashActivity
import me.yxp.qfun.hook.patch.SwallowSubThreadCrash
import me.yxp.qfun.loader.hookapi.HookEngineManager
import me.yxp.qfun.utils.hook.hookBefore
import me.yxp.qfun.utils.log.CrashReporter
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.QQCurrentEnv
import me.yxp.qfun.utils.qq.Toasts

object CrashMonitor {

    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        isInitialized = true

        try {
            Thread::class.java.getDeclaredMethod(
                "setDefaultUncaughtExceptionHandler",
                Thread.UncaughtExceptionHandler::class.java
            ).hookBefore { param ->
                val originalHandler = param.args[0] as? Thread.UncaughtExceptionHandler
                if (originalHandler !is CrashHandler) {
                    param.args[0] = CrashHandler(originalHandler)
                }
            }

            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(currentHandler))
            }
        } catch (t: Throwable) {
            HookEngineManager.engine.log(Log.ERROR, "[QFun]", "CrashMonitor init failed: ", t)
        }
    }

    class CrashHandler(private val originalHandler: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {

        override fun uncaughtException(t: Thread, e: Throwable) {

            // B16 阻止子线程闪退：非主线程崩溃且开关开启 → 记日志后拦截，进程存活
            if (t !== Looper.getMainLooper().thread && isSwallowEnabled()) {
                swallowSubThreadCrash(t, e)
                return
            }

            try {
                val result = CrashReporter.generateReport(t, e)

                val context = HostInfo.hostContext
                val intent = Intent(context, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("blamedModule", result.blamedModule)
                    putExtra("exceptionType", result.exceptionType)
                    putExtra("summary", result.summary)
                    putExtra("reportPath", result.zipFile.absolutePath)
                    putExtra("stackTrace", result.stackTrace)
                    putExtra("hostName", HostInfo.hostName)
                }

                context.startActivity(intent)
                exitProcess(10)

            } catch (innerEx: Throwable) {
                innerEx.printStackTrace()
                originalHandler?.uncaughtException(t, e)
            }
        }

        private fun isSwallowEnabled(): Boolean =
            runCatching { SwallowSubThreadCrash.isEnable }.getOrDefault(false)

        private fun swallowSubThreadCrash(t: Thread, e: Throwable) {
            // 记录失败也照样拦截：本分支目的就是不让进程退出
            runCatching {
                val dir = File(QQCurrentEnv.currentDir, "crash")
                dir.mkdirs()

                val logFile = File(dir, "swallowed.log")
                // 日志仅保留最新的一份：超过阈值先删旧文件
                if (logFile.length() > MAX_SWALLOW_LOG_SIZE) logFile.delete()

                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                logFile.appendText("$time T=${t.name}\n${Log.getStackTraceString(e)}\n\n")

                Toasts.toast("子线程闪退已拦截")
            }
        }
    }

    private const val MAX_SWALLOW_LOG_SIZE: Long = 512 * 1024
}