package dev.watcher.modelhub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GgufFile(
    val name: String,
    val sizeBytes: Long,
    val quant: String?,
)

data class HfModel(
    val id: String,
    val pipelineTag: String? = null,
    val downloads: Long = 0,
    val likes: Long = 0,
    val tags: List<String> = emptyList(),
    val files: List<GgufFile> = emptyList(),
) {
    val name: String get() = id.substringAfterLast('/')
    val author: String get() = id.substringBefore('/', "-")
    val paramsB: Double? get() = parseParamsB(name)
    val isGguf: Boolean get() = tags.any { it.equals("gguf", true) } || files.isNotEmpty()

    val bestFile: GgufFile?
        get() = files
            .filter { it.name.endsWith(".gguf", true) && !it.name.contains("mmproj", true) }
            .sortedWith(compareBy({ quantRank(it.quant) }, { it.sizeBytes }))
            .firstOrNull()
}

sealed interface RepoCheck {
    data class Model(val model: HfModel) : RepoCheck
    data class ModelWithoutGguf(val id: String, val pipelineTag: String?) : RepoCheck
    data class Dataset(val id: String, val downloads: Long, val likes: Long) : RepoCheck
    data class NotFound(val id: String) : RepoCheck
    data class InvalidId(val id: String) : RepoCheck
    data class Failed(val message: String) : RepoCheck
}

class HuggingFace {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 30): List<HfModel> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://huggingface.co/api/models")
            append("?filter=gguf&sort=downloads&direction=-1&limit=").append(limit)
            if (query.isNotBlank()) append("&search=").append(URLEncoder.encode(query, "UTF-8"))
        }
        val (code, body) = get(url)
        if (code != 200) throw IOException("HTTP $code")
        val arr = JSONArray(body)
        (0 until arr.length()).mapNotNull { index ->
            val o = arr.optJSONObject(index) ?: return@mapNotNull null
            val id = o.optString("id").ifBlank { o.optString("modelId") }
            if (id.isBlank()) return@mapNotNull null
            val tags = o.optJSONArray("tags").toStringList()
            if (tags.isNotEmpty() && tags.none { it.equals("gguf", true) }) return@mapNotNull null
            HfModel(
                id = id,
                pipelineTag = o.optString("pipeline_tag").takeIf { it.isNotBlank() },
                downloads = o.optLong("downloads"),
                likes = o.optLong("likes"),
                tags = tags,
            )
        }
    }

    suspend fun inspect(rawId: String): RepoCheck = withContext(Dispatchers.IO) {
        val id = rawId.trim()
            .removePrefix("https://huggingface.co/")
            .removePrefix("http://huggingface.co/")
            .trim('/')

        if (!REPO_ID.matches(id)) return@withContext RepoCheck.InvalidId(rawId)

        try {
            val (code, body) = get("https://huggingface.co/api/models/$id?blobs=true")
            when {
                code == 200 -> {
                    val o = JSONObject(body)
                    val files = parseFiles(o)
                    val pipeline = o.optString("pipeline_tag").takeIf { it.isNotBlank() }
                    if (files.isEmpty()) {
                        RepoCheck.ModelWithoutGguf(id, pipeline)
                    } else {
                        RepoCheck.Model(
                            HfModel(
                                id = o.optString("id", id),
                                pipelineTag = pipeline,
                                downloads = o.optLong("downloads"),
                                likes = o.optLong("likes"),
                                tags = o.optJSONArray("tags").toStringList(),
                                files = files,
                            )
                        )
                    }
                }

                code in 401..404 -> {
                    val (datasetCode, datasetBody) = get("https://huggingface.co/api/datasets/$id")
                    if (datasetCode == 200) {
                        val d = JSONObject(datasetBody)
                        RepoCheck.Dataset(
                            id = d.optString("id", id),
                            downloads = d.optLong("downloads"),
                            likes = d.optLong("likes"),
                        )
                    } else {
                        RepoCheck.NotFound(id)
                    }
                }

                else -> RepoCheck.Failed("Hugging Face ответил HTTP $code")
            }
        } catch (t: Throwable) {
            RepoCheck.Failed(t.message ?: "Сеть недоступна")
        }
    }

    private fun parseFiles(o: JSONObject): List<GgufFile> {
        val arr = o.optJSONArray("siblings") ?: return emptyList()
        val out = ArrayList<GgufFile>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val name = s.optString("rfilename")
            if (!name.endsWith(".gguf", ignoreCase = true)) continue
            val size = s.optJSONObject("lfs")?.optLong("size") ?: s.optLong("size")
            out += GgufFile(name = name, sizeBytes = size, quant = parseQuant(name))
        }
        return out.sortedBy { it.sizeBytes }
    }

    private suspend fun get(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ModelHub/0.1 (Android)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            response.code to response.body?.string().orEmpty()
        }
    }

    companion object {
        private val REPO_ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*(/[A-Za-z0-9][A-Za-z0-9._-]*)?$")

        fun fileUrl(repoId: String, fileName: String): String =
            "https://huggingface.co/$repoId/resolve/main/" +
                fileName.split('/').joinToString("/") {
                    URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                }
    }
}

private val PARAM_RE = Regex("(?i)(?<![0-9.])([0-9]+(?:\\.[0-9]+)?)b(?![a-z0-9])")
private val QUANT_RE = Regex("(?i)\\b(IQ[0-9]+[A-Z0-9_]*|Q[0-9]+(?:_K)?(?:_[SMLN0-9]+)?|BF16|F16|FP16|F32)\\b")

fun parseParamsB(raw: String): Double? =
    PARAM_RE.find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

fun parseQuant(raw: String): String? =
    QUANT_RE.find(raw)?.value?.uppercase()

private fun quantRank(quant: String?): Int = when {
    quant == null -> 9
    quant.contains("Q4_K_M") -> 0
    quant.startsWith("Q4") -> 1
    quant.startsWith("Q5_K_M") -> 2
    quant.startsWith("Q5") -> 3
    quant.startsWith("Q6") -> 4
    quant.startsWith("Q8") -> 5
    quant.startsWith("F16") || quant.startsWith("BF16") || quant.startsWith("FP16") -> 6
    else -> 7
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val value = optString(i)
        if (value.isNotBlank()) out += value
    }
    return out
}
