package dev.watcher.modelhub.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.watcher.modelhub.data.HfModel
import dev.watcher.modelhub.data.RepoCheck
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel

val ModelFilters = listOf("Все", "Чат", "Код", "Vision", "Reasoning", "≤ 2B")

@Stable
class ModelsUiState {
    var query by mutableStateOf("")
    var filter by mutableStateOf(ModelFilters.first())
    var models by mutableStateOf<List<HfModel>>(emptyList())
    var loading by mutableStateOf(true)
    var offline by mutableStateOf(false)
    var busy by mutableStateOf<Set<String>>(emptySet())
    var checkOpen by mutableStateOf(false)
    var repoInput by mutableStateOf("")
    var checking by mutableStateOf(false)
    var checkResult by mutableStateOf<RepoCheck?>(null)
    var loadedQuery by mutableStateOf<String?>(null)
}

data class ChatMessage(val id: Long, val text: String, val fromUser: Boolean)

@Stable
class ChatUiState : androidx.lifecycle.ViewModel() {
    var input by mutableStateOf("")
    var selectedKey by mutableStateOf<String?>(null)
    var typing by mutableStateOf(false)
    var nextId by mutableStateOf(1L)
    val messages = mutableStateListOf<ChatMessage>()
    var status by mutableStateOf("Выбери скачанную модель")
    var error by mutableStateOf<String?>(null)
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var handle = 0L
    private var loadedKey: String? = null

    fun select(key: String) {
        if (typing || selectedKey == key) return
        selectedKey = key
        messages.clear()
        error = null
        status = "Готово к загрузке модели"
    }

    fun stop() {
        stopped.set(true)
        if (handle != 0L) dev.watcher.modelhub.data.NativeEngine.stop()
    }

    fun clear() {
        if (!typing) { messages.clear(); error = null }
    }

    fun send(task: dev.watcher.modelhub.data.DownloadTask) {
        val text = input.trim()
        if (typing || text.isEmpty()) return
        if (selectedKey != task.key) select(task.key)
        messages += ChatMessage(nextId++, text, true)
        input = ""
        error = null
        typing = true
        stopped.set(false)
        val history = messages.filter { it.text.isNotBlank() }.toList()
        val answerId = nextId++
        messages += ChatMessage(answerId, "", false)
        scope.launch {
            try {
                if (loadedKey != task.key) {
                    status = "Загрузка GGUF…"
                    withContext(worker) {
                        if (handle != 0L) dev.watcher.modelhub.data.NativeEngine.release(handle)
                        handle = 0L
                        loadedKey = null
                        val file = java.io.File(dev.watcher.modelhub.data.Services.downloads.modelDir(task.repoId), task.fileName)
                        check(file.isFile) { "Файл модели не найден" }
                        handle = dev.watcher.modelhub.data.NativeEngine.load(file.absolutePath)
                        loadedKey = task.key
                    }
                }
                if (stopped.get()) return@launch
                status = "Обработка контекста…"
                withContext(worker) {
                    dev.watcher.modelhub.data.NativeEngine.prepare(
                        handle,
                        history.map { if (it.fromUser) "user" else "assistant" }.toTypedArray(),
                        history.map { it.text.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                        0.7f, Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                    )
                }
                status = "Генерация · CPU"
                val output = java.io.ByteArrayOutputStream()
                var tokens = 0
                val started = System.nanoTime()
                while (!stopped.get() && tokens < 512) {
                    val piece = withContext(worker) { dev.watcher.modelhub.data.NativeEngine.next(handle) } ?: break
                    output.write(piece)
                    val index = messages.indexOfFirst { it.id == answerId }
                    if (index >= 0) messages[index] = ChatMessage(answerId, output.toString("UTF-8"), false)
                    tokens++
                }
                val seconds = (System.nanoTime() - started) / 1e9
                status = if (stopped.get()) "Остановлено" else "Готово · $tokens токенов · ${"%.1f".format(tokens / seconds.coerceAtLeast(0.01))} ток/с"
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                error = failure.message ?: "Ошибка локального инференса"
                status = "Не удалось получить ответ"
            } finally {
                messages.removeAll { it.id == answerId && it.text.isBlank() }
                typing = false
            }
        }
    }

    override fun onCleared() {
        stop()
        scope.launch {
            withContext(worker) {
                if (handle != 0L) dev.watcher.modelhub.data.NativeEngine.release(handle)
                handle = 0L
            }
            worker.close()
            scope.cancel()
        }
    }
}
