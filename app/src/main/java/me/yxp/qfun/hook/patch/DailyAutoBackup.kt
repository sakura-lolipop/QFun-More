package me.yxp.qfun.hook.patch

import android.os.Environment
import androidx.core.content.edit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import me.yxp.qfun.annotation.HookCategory
import me.yxp.qfun.annotation.HookItemAnnotation
import me.yxp.qfun.common.ModuleScope
import me.yxp.qfun.hook.base.BaseSwitchHookItem
import me.yxp.qfun.utils.io.FileUtils
import me.yxp.qfun.utils.log.LogUtils
import me.yxp.qfun.utils.qq.HostInfo
import me.yxp.qfun.utils.qq.QQCurrentEnv
import org.json.JSONObject

@HookItemAnnotation(
    "每天自动备份模块数据",
    "每天首次启动QQ自动将模块配置打包到 Download/QFun，仅保留最近7份",
    HookCategory.OTHER
)
object DailyAutoBackup : BaseSwitchHookItem() {

    private const val MARKER = "AutoBackup"
    private const val BACKUP_PREFIX = "QFunBackup_"
    private const val KEEP_COUNT = 7

    override fun onHook() {

        ModuleScope.launchIO(name) {

            val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            if (prefs.getString(MARKER, null) == today) return@launchIO

            runCatching {
                val destDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "QFun"
                )
                if (!FileUtils.ensureDir(destDir)) return@launchIO

                runCatching { File(destDir, ".nomedia").createNewFile() }

                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zip = File(destDir, "$BACKUP_PREFIX$stamp.zip")

                if (packToZip(zip)) {
                    prefs.edit { putString(MARKER, today) }
                    pruneOldBackups(destDir)
                }
            }.onFailure {
                LogUtils.e(this@DailyAutoBackup, it)
            }
        }
    }

    private fun packToZip(zip: File): Boolean {

        val context = HostInfo.hostContext
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val tempRoot = File(cacheDir, "qfun_autobackup_tmp")
        FileUtils.delete(tempRoot)

        val backupDir = File(tempRoot, "backup")
        FileUtils.ensureDir(backupDir)

        val prefsData = JSONObject()
        prefs.all.forEach { (key, value) ->
            prefsData.put(key, value)
        }
        prefsData.put("_uin", QQCurrentEnv.currentUin)
        FileUtils.writeText(File(backupDir, "config.json"), prefsData.toString())

        val dataDir = File(QQCurrentEnv.currentDir)
        if (dataDir.isDirectory) {
            val destData = File(backupDir, "data")
            FileUtils.ensureDir(destData)
            dataDir.listFiles()?.forEach { file ->
                if (file.name != "log" && file.name != "cache") {
                    FileUtils.copy(file, File(destData, file.name))
                }
            }
        }

        val result = FileUtils.zip(backupDir, zip)
        FileUtils.delete(tempRoot)
        return result
    }

    private fun pruneOldBackups(dir: File) {
        dir.listFiles { file ->
            file.isFile && file.name.startsWith(BACKUP_PREFIX) && file.name.endsWith(".zip")
        }?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP_COUNT)
            ?.forEach { runCatching { it.delete() } }
    }
}
