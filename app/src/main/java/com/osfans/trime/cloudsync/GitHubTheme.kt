// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync

import android.content.Context
import com.osfans.trime.core.RimeMaintenanceMutex
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.resume

object GitHubTheme {
    interface Choice { fun choose(index: Int) }
    interface Callback {
        fun onMessage(message: String)
        fun onChoices(names: Array<String>, choice: Choice)
        fun onDone(success: Boolean, message: String)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operation = Mutex()

    @JvmStatic fun initialize(context: Context) {
        scope.launch { operation.withLock { runCatching { migrate(context.applicationContext) } } }
    }
    private suspend fun migrate(context: Context) {
        val prefs = context.getSharedPreferences("theme-import", Context.MODE_PRIVATE)
        if (prefs.getBoolean("classic-migrated-026", false)) return
        RimeMaintenanceMutex.withLock {
            DataManager.sync()
            AppPrefs.defaultInstance().advanced.ignoreSystemGestureInsets.setValue(true)
            DataManager.userDataDir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".trime.yaml") }.forEach { it.delete() }
            ThemeManager.prefs.normalModeColor.setValue("wechat_dark")
            val actual = ThemeManager.selectTheme("classic.trime")
            check(actual == "classic.trime") { "内置主题无法加载" }
            val root = DataManager.userDataDir
            val oldManaged = JSONArray(prefs.getString("managed", "[]"))
            for (index in 0 until oldManaged.length()) {
                val path = oldManaged.getString(index)
                check(ThemeArchive.destination(path) == path) { "无效主题文件记录" }
                val oldFile = File(root, path)
                check(oldFile.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "主题文件路径越界" }
                check(!oldFile.exists() || (oldFile.isFile && oldFile.delete())) { "旧主题资源无法清理" }
            }
            DataManager.userDataDir.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".trime.yaml") }.forEach { it.delete() }
            File(DataManager.stagingDir, "星天云同步.trime.yaml").delete()
            ThemeManager.syncToDeviceEncryptedStorage()
            check(prefs.edit().putString("managed", "[]").putBoolean("classic-migrated-026", true).commit()) { "主题升级记录保存失败" }
        }
    }
    @JvmStatic fun start(context: Context, input: String, callback: Callback) {
        val ctx = context.applicationContext
        scope.launch {
            try {
                operation.withLock {
                    migrate(ctx)
                    val repo = ThemeArchive.repository(input)
                    val job = File(ctx.cacheDir, "github-theme-import")
                    check(job.deleteRecursively() || !job.exists()) { "旧临时文件无法清理" }
                    check(job.mkdirs()) { "无法创建下载目录" }
                    try {
                        withContext(Dispatchers.Main) { callback.onMessage("读取 GitHub 版本并下载主题…") }
                        val commit = File(job, "commit.json")
                        download("https://api.github.com/repos/$repo/commits/HEAD", commit, 2L * 1024 * 1024)
                        val sha = JSONObject(commit.readText()).getString("sha")
                        check(sha.matches(Regex("[0-9a-f]{40}"))) { "GitHub 版本信息无效" }
                        val archive = File(job, "theme.zip")
                        download("https://codeload.github.com/$repo/zip/$sha", archive, 64L * 1024 * 1024)
                        val staging = File(job, "new").also { it.mkdirs() }
                        val paths = ThemeArchive.extract(archive, staging)
                        val themes = paths.filter { it.endsWith(".trime.yaml") }.sorted()
                        val sources = ThemeLoader.SourceLoader { id ->
                            ThemeLoader.findSourceFile(id, listOf(staging, DataManager.sharedDataDir))
                        }
                        val valid = themes.mapNotNull { path ->
                            val id = path.removeSuffix(".yaml")
                            val result = ThemeLoader.loadFromSource(id, File(staging, path), sources)
                            if (result is ThemeLoader.ThemeLoadResult.Success) Triple(path, id, result.theme.name) else null
                        }
                        check(valid.isNotEmpty()) { "主题配置与当前同文不兼容，原主题已保留；不会运行仓库中的脚本" }
                        val selected = withContext(Dispatchers.Main) {
                            suspendCancellableCoroutine<Int> { continuation ->
                                callback.onChoices(valid.map { "${it.third} (${it.first})" }.toTypedArray(), object : Choice {
                                    override fun choose(index: Int) { if (continuation.isActive) continuation.resume(index) }
                                })
                            }
                        }
                        if (selected !in valid.indices) {
                            withContext(Dispatchers.Main) { callback.onDone(false, "已取消导入，原主题保留。") }
                            return@withLock
                        }
                        val chosen = valid[selected]
                        val installPaths = paths.filter { !it.endsWith(".trime.yaml") || valid.any { theme -> theme.first == it } }
                        install(ctx, staging, installPaths, chosen.second, File(job, "rollback"))
                        ctx.getSharedPreferences("theme-import", Context.MODE_PRIVATE).edit()
                            .putString("repository", repo).putString("commit", sha).apply()
                        withContext(Dispatchers.Main) { callback.onDone(true, "已应用 ${chosen.third}\n旧导入主题已替换。版本：${sha.take(8)}\n后续更新：用同一网址再次导入。") }
                    } finally { job.deleteRecursively() }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) { callback.onDone(false, "主题导入停止：${e.message}") }
            }
        }
    }
    private suspend fun install(context: Context, staging: File, paths: List<String>, selected: String, backup: File) {
        val prefs = context.getSharedPreferences("theme-import", Context.MODE_PRIVATE)
        val old = JSONArray(prefs.getString("managed", "[]"))
        val previous = ThemeManager.prefs.selectedTheme.getValue()
        val root = DataManager.userDataDir
        val affected = paths.toMutableSet()
        for (i in 0 until old.length()) affected.add(old.getString(i))
        root.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".trime.yaml") }.forEach { affected.add(it.name) }
        val bundled = listOf("fonts/ChaiPUA-0.2.7-snow.ttf", "fonts/FiraCode-Regular.ttf", "backgrounds/hanamaru/yuki.png")
        affected.addAll(bundled)
        // Prevent future asset checks from resurrecting replaced bundled resources after migration.
        RimeMaintenanceMutex.withLock {
            val existed = mutableSetOf<String>()
            for (path in affected) {
                check(ThemeArchive.destination(path) == path) { "无效主题文件记录" }
                val file = File(root, path)
                check(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "主题文件路径越界" }
                if (file.exists()) {
                    check(file.isFile) { "主题路径被目录占用" }
                    val saved = File(backup, path).also { it.parentFile?.mkdirs() }
                    file.copyTo(saved, overwrite = true)
                    existed.add(path)
                }
            }
            try {
                for (path in paths) {
                    val target = File(root, path).also { it.parentFile?.mkdirs() }
                    val incoming = File(target.parentFile, ".theme-incoming.tmp")
                    try {
                        File(staging, path).copyTo(incoming, overwrite = true)
                        Files.move(incoming.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    } finally { incoming.delete() }
                }
                check(ThemeManager.selectTheme(selected) == selected) { "主题应用失败" }
                for (path in affected - paths.toSet()) check(!File(root, path).exists() || File(root, path).delete()) { "旧主题文件无法清理" }
                ThemeManager.syncToDeviceEncryptedStorage()
                check(prefs.edit().putString("managed", JSONArray(paths).toString()).commit()) { "主题文件记录保存失败" }
            } catch (e: Exception) {
                for (path in affected) {
                    val target = File(root, path)
                    if (path in existed) File(backup, path).copyTo(target, overwrite = true) else target.delete()
                }
                ThemeManager.selectTheme(previous)
                throw e
            }
        }
    }
    private fun download(address: String, target: File, limit: Long) {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 20000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "TrimeCloudSync")
        try {
            check(connection.responseCode == 200) { "GitHub 请求失败（${connection.responseCode}）；请检查网络或稍后重试" }
            check(connection.contentLengthLong <= limit) { "仓库下载超过容量上限" }
            connection.inputStream.use { input -> target.outputStream().use { out ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= limit) { "仓库下载超过容量上限" }
                    out.write(buffer, 0, count)
                }
            } }
        } finally { connection.disconnect() }
    }
}
