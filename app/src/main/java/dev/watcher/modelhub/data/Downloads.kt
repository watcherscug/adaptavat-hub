package dev.watcher.modelhub.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, DONE, FAILED }

data class DownloadTask(
    val key: String,
    val repoId: String,
    val fileName: String,
    val url: String,
    val totalBytes: Long = 0L,
    val bytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val speedBps: Double = 0.0,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

class DownloadManager(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val jobs = HashMap<String, Job>()

    init {
        restore()
    }

    fun modelDir(repoId: String): File =
        File(File(appContext.filesDir, "models"), repoId.replace('/', '_'))

    fun isDownloaded(repoId: String): Boolean =
        _tasks.value.any { it.repoId == repoId && it.status == DownloadStatus.DONE }

    fun start(model: HfModel, file: GgufFile) {
        val key = "${model.id}/${file.name}"
        if (_tasks.value.any { it.key == key && it.status == DownloadStatus.DONE }) return
        if (jobs.containsKey(key)) return

        val dest = File(modelDir(model.id), file.name)
        if (dest.exists() && dest.length() > 0L) {
            upsert(
                DownloadTask(
                    key = key,
                    repoId = model.id,
                    fileName = file.name,
                    url = "",
                    totalBytes = dest.length(),
                    bytes = dest.length(),
                    status = DownloadStatus.DONE,
                )
            )
            return
        }

        val task = DownloadTask(
            key = key,
            repoId = model.id,
            fileName = file.name,
            url = HuggingFace.fileUrl(model.id, file.name),
            totalBytes = file.sizeBytes,
        )
        upsert(task)
        launch(task)
    }

    fun pause(key: String) {
        jobs.remove(key)?.cancel()
        setStatus(key, DownloadStatus.PAUSED)
    }

    fun resume(key: String) {
        val task = _tasks.value.firstOrNull { it.key == key } ?: return
        if (task.status != DownloadStatus.PAUSED && task.status != DownloadStatus.FAILED) return
        if (jobs.containsKey(key)) return
        val fresh = task.copy(status = DownloadStatus.QUEUED, error = null)
        upsert(fresh)
        launch(fresh)
    }

    fun cancel(key: String) {
        jobs.remove(key)?.cancel()
        val task = _tasks.value.firstOrNull { it.key == key } ?: return
        File(modelDir(task.repoId), task.fileName + ".part").delete()
        File(modelDir(task.repoId), task.fileName).delete()
        _tasks.update { list -> list.filterNot { it.key == key } }
    }

    fun downloadedTasks(): List<DownloadTask> =
        _tasks.value.filter { it.status == DownloadStatus.DONE }

    private fun launch(task: DownloadTask) {
        val job = scope.launch {
            val dir = modelDir(task.repoId).apply { mkdirs() }
            val part = File(dir, task.fileName + ".part")
            val dest = File(dir, task.fileName)
            try {
                var written = if (part.exists()) part.length() else 0L
                val builder = Request.Builder()
                    .url(task.url)
                    .header("User-Agent", "ModelHub/0.1 (Android)")
                if (written > 0L) builder.header("Range", "bytes=$written-")

                client.newCall(builder.build()).execute().use { response ->
                    if (response.code != 200 && response.code != 206) {
                        throw IOException("HTTP ${response.code}")
                    }
                    val length = response.body?.contentLength() ?: 0L
                    val total = if (response.code == 206) written + length else length
                    if (total > 0L) {
                        upsert(task.copy(totalBytes = total, status = DownloadStatus.RUNNING))
                    }
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(part, written > 0L).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var lastTick = System.currentTimeMillis()
                            var lastBytes = written
                            while (isActive) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                val now = System.currentTimeMillis()
                                if (now - lastTick >= 300) {
                                    val speed = (written - lastBytes) * 1000.0 / (now - lastTick)
                                    upsert(
                                        task.copy(
                                            bytes = written,
                                            totalBytes = if (total > 0L) total else written,
                                            status = DownloadStatus.RUNNING,
                                            speedBps = speed,
                                        )
                                    )
                                    lastTick = now
                                    lastBytes = written
                                }
                            }
                        }
                    }
                }

                if (part.length() > 0L) {
                    dest.delete()
                    part.renameTo(dest)
                    upsert(
                        task.copy(
                            bytes = dest.length(),
                            totalBytes = dest.length(),
                            status = DownloadStatus.DONE,
                            speedBps = 0.0,
                        )
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                upsert(
                    task.copy(
                        status = DownloadStatus.FAILED,
                        error = t.message ?: "Ошибка загрузки",
                        speedBps = 0.0,
                    )
                )
            } finally {
                jobs.remove(task.key)
            }
        }
        jobs[task.key] = job
    }

    private fun upsert(task: DownloadTask) {
        _tasks.update { list ->
            if (list.any { it.key == task.key }) {
                list.map { if (it.key == task.key) task else it }
            } else {
                list + task
            }
        }
    }

    private fun setStatus(key: String, status: DownloadStatus) {
        _tasks.update { list ->
            list.map { if (it.key == key) it.copy(status = status, speedBps = 0.0) else it }
        }
    }

    private fun restore() {
        val root = File(appContext.filesDir, "models")
        if (!root.isDirectory) return
        val restored = ArrayList<DownloadTask>()
        root.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val repoId = dir.name.replaceFirst('_', '/')
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gguf", ignoreCase = true)) {
                    restored += DownloadTask(
                        key = "$repoId/${file.name}",
                        repoId = repoId,
                        fileName = file.name,
                        url = "",
                        totalBytes = file.length(),
                        bytes = file.length(),
                        status = DownloadStatus.DONE,
                    )
                }
            }
        }
        if (restored.isNotEmpty()) _tasks.value = restored
    }
}
