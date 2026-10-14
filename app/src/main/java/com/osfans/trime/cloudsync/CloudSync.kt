// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.osfans.trime.core.RimeMaintenanceMutex
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.data.sync.SyncPathPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object CloudSync {
    private val operation = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    interface Callback { fun onMessage(message: String); fun onDone(success: Boolean) }

    @JvmStatic fun start(context: Context, callback: Callback) {
        val ctx = context.applicationContext
        scope.launch {
            try {
                synchronize(ctx) { message -> withContext(Dispatchers.Main) { callback.onMessage(message) } }
                withContext(Dispatchers.Main) { callback.onDone(true) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.Main) { callback.onMessage("同步停止：${e.message}"); callback.onDone(false) }
            }
        }
    }

    suspend fun synchronize(context: Context, progress: suspend (String) -> Unit = {}) = withContext(Dispatchers.IO) {
        check(operation.tryLock()) { "已有云同步正在运行" }
        val sessionName = "cloud-sync"
        var sessionCreated = false
        val work = File(context.filesDir, "cloud-sync").also { it.mkdirs() }
        val cache = File(work, "objects").also { it.mkdirs() }
        try {
            check(!RimeDataSync.usesExternalSync()) { "云同步使用应用专属存储；请在存储设置中关闭外部目录同步，避免两套同步互相覆盖" }
            val credentials = CloudCredentials(context)
            val client = S3Client(credentials.access(), credentials.secret(), cache)
            SyncStorage.pruneObjects(cache, cache.listFiles().orEmpty().filter { it.name.matches(Regex("[0-9a-f]{64}")) }.map { it.name }.toSet())
            SyncStorage.pruneBackups(File(work, "backups"))
            val session = RimeDaemon.createSession(sessionName)
            sessionCreated = true
            session.runOnReady { isEmpty() }
            val root = DataManager.userDataDir.canonicalFile
            val ownId = SyncPathPolicy.readOwnInstallationId() ?: error("设备 ID 尚未生成，请先完成输入法初始化")
            check(ownId.matches(Regex("[a-zA-Z0-9_-]+"))) { "本机设备 ID 无效" }
            val syncRoot = SyncPathPolicy.localSyncRoot(SyncPathPolicy.readOwnSyncDir(), root)
            for (attempt in 1..3) {
                progress("读取云端版本（第 $attempt 次）…")
                val current = client.get("current.json", 4L * 1024 * 1024).use { reply ->
                    val manifest = JSONObject(reply.file.readText())
                    validate(manifest)
                    manifest to (reply.etag ?: error("S3 未提供版本校验；停止上传"))
                }
                val manifest = current.first
                val downloaded = linkedMapOf<String, File>()
                val cloudSnapshots = manifest.getJSONObject("snapshots")
                val files = manifest.getJSONObject("files")
                val retained = mutableSetOf<String>()
                for (group in listOf("files", "snapshots")) {
                    val entries = manifest.getJSONObject(group)
                    for (name in entries.keys()) if (group == "snapshots" || mobileConfig(name)) {
                        retained.add(entries.getJSONObject(name).getString("sha256"))
                    }
                }
                SyncStorage.pruneObjects(cache, retained)
                val total = files.length() + cloudSnapshots.length()
                var done = 0
                for (group in listOf("files", "snapshots")) {
                    val entries = manifest.getJSONObject(group)
                    for (name in entries.keys()) {
                        ensureActive()
                        if (group == "snapshots" || mobileConfig(name)) {
                            val file = blob(client, entries.getJSONObject(name), cache)
                            if (group == "snapshots") check(Snapshot(file).dbName + ".userdb.txt" == name) { "快照名称不匹配" }
                            downloaded["$group/$name"] = file
                        }
                        progress("下载校验 ${++done}/$total")
                    }
                }
                // Only verified files are applied, serialized against all native maintenance.
                var changed = false
                RimeMaintenanceMutex.withLock {
                    val backup = File(work, "backups/${System.currentTimeMillis()}")
                    for (name in files.keys()) {
                        if (!mobileConfig(name)) continue
                        val source = downloaded.getValue("files/$name")
                        val target = contained(root, name)
                        if (target.isFile && S3Client.hash(target) == files.getJSONObject(name).getString("sha256")) continue
                        if (target.exists()) {
                            check(target.isFile) { "目标路径不是文件：$name" }
                            val saved = File(backup, name)
                            saved.parentFile?.mkdirs()
                            target.copyTo(saved, overwrite = false)
                        }
                        target.parentFile?.mkdirs()
                        val incoming = File(target.parentFile, ".cloud-${UUID.randomUUID()}.tmp")
                        source.copyTo(incoming)
                        try { java.nio.file.Files.move(incoming.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
                        finally { incoming.delete() }
                        changed = true
                    }
                    val peer = File(syncRoot, "cloud-${manifest.getString("device_id")}").also { it.mkdirs() }
                    for (name in cloudSnapshots.keys()) {
                        downloaded.getValue("snapshots/$name").copyTo(File(peer, name), overwrite = true)
                        File(root, name.removeSuffix(".userdb.txt") + ".userdb").mkdirs()
                    }
                }
                if (changed) {
                    progress("正在部署方案…")
                    session.runOnReady { deploy(skipImport = true) }
                }
                progress("正在合并词库并导出本机快照…")
                val started = System.currentTimeMillis()
                check(session.runOnReady { syncUserData() }) { "Rime 原生词库合并失败；停止上传" }
                val captures = linkedMapOf<String, File>()
                try {
                    RimeMaintenanceMutex.withLock {
                        val ownRoot = File(syncRoot, ownId)
                        val exported = ownRoot.listFiles { f -> f.isFile && f.name.endsWith(".userdb.txt") }.orEmpty()
                        check(exported.isNotEmpty()) { "Rime 未导出词库" }
                        for (source in exported) {
                            check(source.lastModified() >= started - 2000) { "发现旧词库快照；停止上传" }
                            val copy = File.createTempFile("capture-", ".txt", cache)
                            source.copyTo(copy, overwrite = true)
                            captures[source.name] = copy
                            val snapshot = Snapshot(copy)
                            check(snapshot.userId == ownId && snapshot.dbName + ".userdb.txt" == source.name) { "词库身份校验失败" }
                            captures[source.name] = copy
                        }
                    }
                    for (name in cloudSnapshots.keys()) {
                        val captured = captures[name] ?: error("云端词库 $name 未被本机合并；停止上传")
                        Snapshot(captured).requireMerged(Snapshot(downloaded.getValue("snapshots/$name")))
                    }
                    val outgoing = JSONObject()
                    for ((name, copy) in captures) {
                        ensureActive()
                        val hash = S3Client.hash(copy)
                        progress("上传并验证 $name…")
                        client.put("objects/$hash", copy, null, null)
                        client.get("objects/$hash", copy.length()).use { check(S3Client.hash(it.file) == hash) { "上传校验失败" } }
                        outgoing.put(name, JSONObject().put("sha256", hash).put("size", copy.length()))
                    }
                    // Android never publishes phone themes or replaces the desktop's file manifest.
                    val latest = JSONObject(manifest.toString())
                    latest.put("snapshots", outgoing).put("device_id", ownId)
                    latest.put("device_name", "Android-${Build.MANUFACTURER}-${Build.MODEL}")
                    latest.put("version", UUID.randomUUID().toString()).put("synced_at", Instant.now().toString())
                    latest.put("synced_at_china", OffsetDateTime.now(ZoneOffset.ofHours(8)).toString())
                    var bytes = 0L
                    for (group in listOf("files", "snapshots")) for (name in latest.getJSONObject(group).keys()) bytes += latest.getJSONObject(group).getJSONObject(name).getLong("size")
                    latest.put("file_count", files.length() + outgoing.length()).put("total_bytes", bytes)
                    validate(latest)
                    val payload = File(work, "pending.json").also { it.writeText(latest.toString()) }
                    try {
                        client.put("current.json", payload, "If-Match", current.second)
                    } catch (e: S3Client.HttpError) {
                        if (e.status == 409 || e.status == 412) {
                            progress("另一台更新了云端，重新拉取并合并…")
                            continue
                        }
                        throw e
                    }
                    credentials.prefs.edit().putString("last_success", latest.getString("synced_at_china")).remove("last_error").apply()
                    File(work, "last-success.json").writeText(latest.toString())
                    RimeMaintenanceMutex.withLock {
                        SyncStorage.prunePeers(syncRoot, ownId, "cloud-${manifest.getString("device_id")}")
                    }
                    progress("同步成功：${latest.getString("synced_at_china")}\n词库已合并，电脑配置保留。")
                    return@withContext
                } finally { captures.values.forEach { it.delete() } }
            }
            error("云端连续更新；已停止，未覆盖另一台的新版本")
        } finally {
            try {
                SyncStorage.pruneBackups(File(work, "backups"))
            } finally {
                if (sessionCreated) RimeDaemon.destroySession(sessionName)
                operation.unlock()
            }
        }
    }
    private fun mobileConfig(name: String) = !name.startsWith("weasel.") && !name.startsWith("squirrel.") && !name.startsWith("trime.") && !name.endsWith(".trime.yaml") && !name.startsWith("fonts/") && !name.startsWith("backgrounds/")
    private fun contained(root: File, name: String): File {
        safePath(name)
        val child = File(root, name).canonicalFile
        check(child.path.startsWith(root.canonicalPath + File.separator)) { "文件越出数据目录" }
        return child
    }
    private fun safePath(name: String) { check(name.isNotEmpty() && !name.startsWith('/') && ':' !in name && '\\' !in name && '\u0000' !in name && name.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "无效云端路径" } }
    private fun validate(m: JSONObject) {
        check(m.getInt("format") == 1 && m.getString("device_id").matches(Regex("[a-zA-Z0-9_-]+"))) { "云端格式不兼容" }
        check(m.getString("version").isNotEmpty()) { "缺少云端版本" }
        var total = 0L; var count = 0
        for (group in listOf("files", "snapshots")) for (name in m.getJSONObject(group).keys()) {
            safePath(name)
            check(name !in setOf("installation.yaml", "user.yaml", "AGENTS.md", "README.md")) { "受保护的云端文件" }
            check(name.substringBefore('/') !in setOf("build", "sync", "trash", ".git", ".idea", ".github") && name.split('/').none { it.endsWith(".userdb") }) { "不能同步运行数据库或缓存" }
            if (group == "snapshots") check(name.matches(Regex("[a-zA-Z0-9_-]+\\.userdb\\.txt"))) { "快照文件名无效" }
            val entry = m.getJSONObject(group).getJSONObject(name)
            val size = entry.getLong("size")
            check(size in 0..512L * 1024 * 1024 && entry.getString("sha256").matches(Regex("[0-9a-f]{64}"))) { "文件校验信息无效" }
            total += size; count++
            check(count <= 4096 && total <= 1024L * 1024 * 1024) { "备份超过容量限制" }
        }
        check(m.getJSONObject("snapshots").length() > 0) { "云端无词库" }
    }
    private fun blob(client: S3Client, entry: JSONObject, cache: File): File {
        val hash = entry.getString("sha256"); val size = entry.getLong("size")
        val file = File(cache, hash)
        if (file.isFile && file.length() == size && S3Client.hash(file) == hash) return file
        client.get("objects/$hash", size).use { reply ->
            check(reply.file.length() == size && S3Client.hash(reply.file) == hash) { "下载校验失败" }
            reply.file.copyTo(file, overwrite = true)
        }
        return file
    }
}

class CloudSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (!CloudCredentials(applicationContext).prefs.getBoolean("automatic", false)) Result.success()
        else { CloudSync.synchronize(applicationContext); Result.success() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        CloudCredentials(applicationContext).prefs.edit().putString("last_error", e.message ?: "同步失败").apply()
        Result.retry()
    }
}
