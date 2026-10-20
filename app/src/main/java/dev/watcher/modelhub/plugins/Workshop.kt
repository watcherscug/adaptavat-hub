package dev.watcher.modelhub.plugins

import android.content.Context
import android.net.Uri
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import dalvik.system.InMemoryDexClassLoader
import dev.watcher.modelhub.data.NativeEngine
import dev.watcher.modelhub.data.Services
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

data class InstalledPlugin(val id: String, val name: String, val language: String, val commands: List<String>, val enabled: Boolean)

class Workshop(private val context: Context) : PluginApi {
    private val root = File(context.filesDir, "workshop").apply { mkdirs() }
    private val pluginsDir = File(root, "plugins").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("adpp", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val output = MutableStateFlow("Adaptavat Workshop · API v1\nhelp — команды · py <код> — Python\n")
    val transcript = output.asStateFlow()
    private val running = MutableStateFlow(false)
    val busy = running.asStateFlow()
    private val installed = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val plugins = installed.asStateFlow()
    private val instances = mutableMapOf<String, AdppPlugin>()
    private var depth = 0

    init { refresh() }
    override fun version() = 1
    override fun workspace() = files.absolutePath
    @Synchronized override fun log(message: String) { output.value = (output.value + message + "\n").takeLast(120_000) }
    override fun models(): String {
        val entries = JSONArray()
        File(context.filesDir, "models").walkTopDown().filter { it.isFile && it.extension == "gguf" }.forEach {
            entries.put(JSONObject().put("name", it.name).put("path", it.absolutePath).put("bytes", it.length()))
        }
        files.walkTopDown().filter { it.isFile && it.extension == "gguf" }.forEach {
            entries.put(JSONObject().put("name", it.name).put("path", it.absolutePath).put("bytes", it.length()))
        }
        return entries.toString(2)
    }

    private fun python(): Python {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context))
        return Python.getInstance()
    }

    @Synchronized fun submit(source: String) {
        if (running.value || source.isBlank()) return
        running.value = true
        scope.launch {
            try {
                log("> $source")
                python().getModule("adpp_console").callAttr("execute", source, this@Workshop)
            } catch (e: Exception) { log("Ошибка: ${e.message}") }
            finally { running.value = false }
        }
    }

    fun clear() { if (!running.value) output.value = "" }

    private fun refresh() {
        installed.value = pluginsDir.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.mapNotNull { dir ->
            runCatching {
                val m = JSONObject(File(dir, "manifest.json").readText())
                InstalledPlugin(m.getString("id"), m.getString("name"), m.getString("language"),
                    (0 until m.getJSONArray("commands").length()).map { m.getJSONArray("commands").getString(it) },
                    prefs.getBoolean(dir.name, true))
            }.getOrNull()
        }.sortedBy { it.id }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        if (running.value) return
        prefs.edit().putBoolean(id, enabled).apply()
        instances.remove(id)
        refresh()
    }

    fun remove(id: String) {
        if (running.value) return
        require(id.matches(Regex("[a-z][a-z0-9_.-]{2,79}")))
        File(pluginsDir, id).deleteRecursively()
        instances.remove(id)
        refresh()
    }

    @Synchronized fun importFile(uri: Uri, plugin: Boolean) {
        if (running.value) return
        running.value = true
        scope.launch {
            try {
                if (plugin) install(uri) else {
                    val file = File(files, "import-${System.currentTimeMillis()}.gguf")
                    try {
                        context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                        file.inputStream().use { val magic = ByteArray(4); require(it.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF") { "Файл не GGUF" } }
                        log("Импортирован: ${file.absolutePath}")
                    } catch (e: Exception) { file.delete(); throw e }
                }
            } catch (e: Exception) { log("Ошибка импорта: ${e.message}") }
            finally { running.value = false }
        }
    }

    private fun install(uri: Uri) {
        val staging = File(pluginsDir, ".install-${System.nanoTime()}").apply { mkdirs() }
        try {
            var total = 0L
            var count = 0
            context.contentResolver.openInputStream(uri)!!.use { stream ->
                ZipInputStream(stream).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        require(++count <= 2048) { "Слишком много файлов" }
                        val dest = File(staging, entry.name).canonicalFile
                        require(dest.path.startsWith(staging.canonicalPath + File.separator)) { "Некорректный путь в ADPP" }
                        if (entry.isDirectory) dest.mkdirs() else {
                            require(!dest.exists()) { "Повторяющийся файл" }
                            dest.parentFile!!.mkdirs()
                            dest.outputStream().use { out ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    val n = zip.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= 64L * 1024 * 1024) { "ADPP превышает 64 МБ" }
                                    out.write(buffer, 0, n)
                                }
                            }
                        }
                    }
                }
            }
            val m = JSONObject(File(staging, "manifest.json").readText())
            val id = m.getString("id")
            require(id.matches(Regex("[a-z][a-z0-9_.-]{2,79}"))) { "Некорректный id" }
            require(m.getInt("apiVersion") == 1) { "Неподдерживаемая версия API" }
            require(m.getString("name").isNotBlank() && m.getString("version").isNotBlank())
            val language = m.getString("language")
            require(language == "python" || language == "kotlin")
            val commands = m.getJSONArray("commands")
            require(commands.length() in 1..64)
            val seen = mutableSetOf<String>()
            for (i in 0 until commands.length()) {
                val name = commands.getString(i)
                require(name.matches(Regex("[a-z][a-z0-9_-]{0,39}")) && seen.add(name))
            }
            if (language == "kotlin") {
                require(File(staging, "classes.dex").isFile) { "Нет classes.dex" }
                require(m.getString("entryPoint").matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*")))
            } else {
                require(m.getString("entryPoint").matches(Regex("[A-Za-z_][A-Za-z0-9_]*\\.py")))
                require(File(staging, m.getString("entryPoint")).isFile)
            }
            val target = File(pluginsDir, id)
            require(!target.exists()) { "Плагин уже установлен: сначала удалите старую версию" }
            require(staging.renameTo(target))
            refresh()
            log("Установлен $id. Команды: ${seen.joinToString { "$id:$it" }}")
        } finally { staging.deleteRecursively() }
    }

    private fun path(raw: String, output: Boolean = false): File {
        val file = (if (File(raw).isAbsolute) File(raw) else File(files, raw)).canonicalFile
        val allowed = listOf(files.canonicalFile, File(context.filesDir, "models").canonicalFile)
        require(allowed.any { file.path.startsWith(it.path + File.separator) }) { "Путь вне файлов моделей/мастерской" }
        if (output) {
            require(file.path.startsWith(files.canonicalPath + File.separator)) { "Результаты сохраняются в workspace" }
            require(!file.exists()) { "Результат уже существует" }
            file.parentFile!!.mkdirs()
        } else require(file.isFile) { "Файл не найден: $raw" }
        return file
    }

    @Synchronized override fun invoke(command: String, argumentsJson: String): String {
        require(depth < 8) { "Превышена глубина вызовов API" }
        depth++
        try {
            val a = JSONObject(argumentsJson)
            return when (command) {
                "models" -> models()
                "files" -> JSONArray(files.walkTopDown().filter { it.isFile }.map { it.relativeTo(files).path }.toList()).toString(2)
                "workspace" -> workspace()
                "packages" -> python().getModule("adpp_console").callAttr("packages").toString()
                "help" -> "models | files | workspace | packages | plugins | inspect PATH | quantize IN OUT Q4_K_M [LAYERS] | prune IN OUT 4,5 | py CODE | run ID:COMMAND {JSON}\nРезультаты: workspace; пути с пробелами заключайте в кавычки."
                "plugins" -> installed.value.joinToString("\n") { "${it.id} (${it.language}, enabled=${it.enabled}): ${it.commands.joinToString()}" }
                "inspect" -> {
                    val f = path(a.getString("input"))
                    f.inputStream().use { val magic = ByteArray(4); require(it.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF") { "Файл не GGUF" } }
                    JSONObject().put("path", f.path).put("bytes", f.length()).put("format", "GGUF").toString(2)
                }
                "quantize", "prune", "transform" -> {
                    val input = path(a.getString("input"))
                    val out = path(a.getString("output"), true)
                    require(out.extension == "gguf")
                    val layers = a.optJSONArray("layers") ?: JSONArray()
                    val indices = (0 until layers.length()).map { layers.getInt(it) }.distinct().sorted()
                    require(indices.all { it >= 0 })
                    if (command == "prune") require(indices.isNotEmpty())
                    val quant = if (command == "prune") "COPY" else a.optString("quant", "Q4_K_M").uppercase()
                    val partial = File(out.path + ".partial")
                    require(!partial.exists()) { "Остался .partial: выберите другое имя результата" }
                    log("llama.cpp: $quant; удалить слои $indices; обрабатываю…")
                    try {
                        NativeEngine.transform(input.path, partial.path, quant, indices.toIntArray(), a.optInt("threads", 4).coerceIn(1, 8))
                        require(partial.isFile && partial.length() > 4 && partial.renameTo(out)) { "Ошибка сохранения" }
                    } catch (e: Throwable) { partial.delete(); throw e }
                    Services.downloads.registerLocal(out)
                    JSONObject().put("output", out.path).put("bytes", out.length()).put("quant", quant).put("removedLayers", JSONArray(indices)).toString(2)
                }
                else -> {
                    val parts = command.split(':', limit = 2)
                    require(parts.size == 2) { "Неизвестная команда $command" }
                    val info = installed.value.firstOrNull { it.id == parts[0] && it.enabled } ?: error("Плагин выключен или не установлен")
                    require(parts[1] in info.commands) { "Команда не экспортирована" }
                    val dir = File(pluginsDir, info.id)
                    val m = JSONObject(File(dir, "manifest.json").readText())
                    if (info.language == "python") python().getModule("adpp_console").callAttr("plugin", dir.path, m.getString("entryPoint"), this, parts[1], argumentsJson).toString()
                    else {
                        val plugin = instances.getOrPut(info.id) {
                            val loader = InMemoryDexClassLoader(ByteBuffer.wrap(File(dir, "classes.dex").readBytes()), AdppPlugin::class.java.classLoader)
                            loader.loadClass(m.getString("entryPoint")).getDeclaredConstructor().newInstance() as AdppPlugin
                        }
                        plugin.execute(this, parts[1], argumentsJson)
                    }
                }
            }
        } finally { depth-- }
    }
}
