// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import androidx.core.content.FileProvider
import com.osfans.trime.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

class AppUpdates(private val activity: Activity, private val status: TextView) {
    companion object { private val downloads = Mutex() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = activity.getSharedPreferences("app-updates", Activity.MODE_PRIVATE)
    private val directory = File(activity.cacheDir, "app-update")
    private val apk = File(directory, "update.apk")
    private var busy = false
    private var awaitingPermission = false

    private data class Release(val tag: String, val url: String, val bytes: Long, val hash: String, val notes: String)

    fun destroy() = scope.cancel()

    fun checkAutomatically() {
        scope.launch {
            withContext(Dispatchers.IO) {
                directory.mkdirs()
                // Remove interrupted downloads and packages already installed; retain at most one APK.
                if (downloads.tryLock()) {
                    try {
                        File(directory, "update.part").delete()
                        if (apk.exists()) {
                            val archived = activity.packageManager.getPackageArchiveInfo(apk.path, 0)
                            if (archived == null || archived.versionCode <= BuildConfig.VERSION_CODE) apk.delete()
                        }
                    } finally { downloads.unlock() }
                }
            }
            if (System.currentTimeMillis() - prefs.getLong("last_check", 0) >= 24 * 60 * 60 * 1000L) check(false)
        }
    }

    fun check(manual: Boolean) {
        if (busy) return
        busy = true
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        status.text = "正在检查 GitHub 更新…"
        scope.launch {
            try {
                val release = withContext(Dispatchers.IO) { latest() }
                if (!AppUpdatePolicy.newer(release.tag, BuildConfig.VERSION_NAME)) {
                    status.text = "当前已是最新版本：${BuildConfig.VERSION_NAME}"
                    return@launch
                }
                status.text = "发现新版 ${release.tag}"
                AlertDialog.Builder(activity).setTitle("发现新版 ${release.tag}")
                    .setMessage("大小：${release.bytes / (1024 * 1024)} MB\n${release.notes.take(1400)}\n\n下载后由你确认安装，保留词库和同步设置。")
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("下载更新") { _, _ -> download(release) }.show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status.text = (if (manual) "检查失败：" else "自动检查暂未完成，可手动重试：") + (e.message ?: "网络不可用")
            } finally { busy = false }
        }
    }

    private fun latest(): Release {
        val connection = URL("https://api.github.com/repos/${AppUpdatePolicy.REPOSITORY}/releases/latest").openConnection() as HttpURLConnection
        val bytes = try {
            configure(connection)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            require(connection.responseCode == 200) { "GitHub 返回 ${connection.responseCode}，请检查网络或稍后重试" }
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                val output = ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= 2 * 1024 * 1024) { "更新信息过大" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } finally { connection.disconnect() }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "尚无正式更新" }
        val tag = json.getString("tag_name")
        val expected = AppUpdatePolicy.assetName(tag)
        val assets = json.getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name") == expected }
        require(matches.size == 1) { "新版尚未上传通用 APK，请稍后重试" }
        val asset = matches.single()
        val url = asset.getString("browser_download_url")
        val size = asset.getLong("size")
        val hash = asset.getString("digest").removePrefix("sha256:")
        require(AppUpdatePolicy.downloadUrl(url, true)) { "更新下载地址不受信任" }
        require(size in 1..AppUpdatePolicy.MAX_APK_BYTES && Regex("[0-9a-f]{64}").matches(hash)) { "更新大小或校验信息无效" }
        require(asset.optString("state") == "uploaded") { "新版尚未上传完成" }
        return Release(tag, url, size, hash, json.optString("body"))
    }

    private fun configure(connection: HttpURLConnection) {
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "TrimeCloudSync/${BuildConfig.VERSION_NAME}")
        connection.setRequestProperty("Accept-Encoding", "identity")
    }

    private fun download(release: Release) {
        if (busy) return
        if (!downloads.tryLock()) { status.text = "另一个页面正在下载更新，请稍后重试"; return }
        busy = true
        status.text = "正在下载 ${release.tag}，请保持此页面打开…"
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    val partial = File(directory, "update.part")
                    partial.delete()
                    apk.delete()
                    var address = release.url
                    try {
                        for (hop in 0..5) {
                            require(AppUpdatePolicy.downloadUrl(address, hop == 0)) { "更新重定向地址不受信任" }
                            val connection = URL(address).openConnection() as HttpURLConnection
                            try {
                                configure(connection)
                                val code = connection.responseCode
                                if (code in listOf(301, 302, 303, 307, 308)) {
                                    require(hop < 5) { "下载重定向次数过多" }
                                    address = URL(URL(address), connection.getHeaderField("Location") ?: error("缺少下载地址")).toString()
                                    continue
                                }
                                require(code == 200) { "下载失败：HTTP $code" }
                                require(connection.contentLengthLong in listOf(-1L, release.bytes)) { "下载大小不符" }
                                val hash = MessageDigest.getInstance("SHA-256")
                                var total = 0L
                                connection.inputStream.use { input -> partial.outputStream().use { output ->
                                    val buffer = ByteArray(65536)
                                    while (true) {
                                        ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        total += count
                                        require(total <= release.bytes) { "下载超出声明大小" }
                                        hash.update(buffer, 0, count)
                                        output.write(buffer, 0, count)
                                    }
                                } }
                                require(total == release.bytes && hex(hash.digest()) == release.hash) { "下载校验失败，请重试" }
                                validateApk(partial, release.tag)
                                check(partial.renameTo(apk)) { "无法保存更新" }
                                break
                            } finally { connection.disconnect() }
                        }
                    } finally { partial.delete() }
                }
                status.text = "下载完成，已校验包名、版本及签名"
                AlertDialog.Builder(activity).setTitle("更新已就绪").setMessage("安装 ${release.tag}？词库和同步设置会保留。")
                    .setNegativeButton("稍后", null).setPositiveButton("安装更新") { _, _ -> installDownloaded() }.show()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { status.text = "更新失败：${e.message ?: "请重试"}"
            } finally { busy = false; downloads.unlock() }
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, tag: String? = null) {
        val manager = activity.packageManager
        val archived = manager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNATURES) ?: error("APK 无法读取")
        val installed = manager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNATURES)
        require(archived.packageName == activity.packageName && archived.versionCode > installed.versionCode) { "APK 包名不匹配或版本未递增" }
        if (tag != null) require(AppUpdatePolicy.version(archived.versionName ?: "") == AppUpdatePolicy.version(tag)) { "APK 与发布版本不符" }
        fun fingerprints(signatures: Array<android.content.pm.Signature>?) = signatures.orEmpty().map {
            hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }.toSet()
        val signatures = fingerprints(archived.signatures)
        require(signatures == fingerprints(installed.signatures) && signatures == setOf(AppUpdatePolicy.CERTIFICATE)) { "APK 签名不匹配" }
    }

    fun resumeInstall() {
        if (awaitingPermission) {
            awaitingPermission = false
            if (activity.packageManager.canRequestPackageInstalls()) installDownloaded()
            else status.text = "尚未允许安装；可再次点击“安装已下载更新”"
        }
    }

    fun installDownloaded() {
        if (busy || downloads.isLocked) return
        try {
            require(apk.isFile) { "还没有下载更新，请先检查版本" }
            validateApk(apk)
            if (!activity.packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(activity).setTitle("允许安装更新")
                    .setMessage("需要在系统设置中允许“同文·云同步”安装应用。返回后会继续打开系统安装界面。")
                    .setNegativeButton("取消", null).setPositiveButton("去设置") { _, _ ->
                        awaitingPermission = true
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    }.show()
                return
            }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) { status.text = "无法安装：${e.message ?: "请重试"}" }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
