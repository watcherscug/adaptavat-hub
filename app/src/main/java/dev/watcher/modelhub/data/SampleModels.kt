package dev.watcher.modelhub.data

data class SampleModel(
    val id: String,
    val sizeGb: Double,
    val quant: String,
    val downloads: Long,
    val likes: Long,
    val tags: List<String>,
)

object SampleModels {
    val all: List<HfModel> = listOf(
        SampleModel("bartowski/Qwen2.5-3B-Instruct-GGUF", 1.93, "Q4_K_M", 184_000, 2_100, listOf("chat", "reasoning")),
        SampleModel("bartowski/Llama-3.2-3B-Instruct-GGUF", 2.02, "Q4_K_M", 1_450_000, 8_700, listOf("chat", "meta")),
        SampleModel("unsloth/gemma-3-4b-it-GGUF", 2.49, "Q4_K_M", 312_000, 1_900, listOf("chat", "vision")),
        SampleModel("bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF", 0.99, "Q4_K_M", 96_000, 640, listOf("code")),
        SampleModel("bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF", 1.12, "Q4_K_M", 420_000, 3_400, listOf("reasoning")),
        SampleModel("HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF", 1.06, "Q4_K_M", 210_000, 1_100, listOf("chat", "tiny")),
        SampleModel("bartowski/Phi-4-mini-instruct-GGUF", 2.32, "Q4_K_M", 75_000, 520, listOf("chat", "microsoft")),
        SampleModel("bartowski/Qwen2.5-7B-Instruct-GGUF", 4.68, "Q4_K_M", 980_000, 5_200, listOf("chat", "reasoning")),
    ).map { sample ->
        val fileName = sample.id.substringAfterLast('/')
            .removeSuffix("-GGUF")
            .lowercase() + "-" + sample.quant.lowercase() + ".gguf"
        HfModel(
            id = sample.id,
            pipelineTag = "text-generation",
            downloads = sample.downloads,
            likes = sample.likes,
            tags = sample.tags + "gguf",
            files = listOf(
                GgufFile(
                    name = fileName,
                    sizeBytes = (sample.sizeGb * 1024 * 1024 * 1024).toLong(),
                    quant = sample.quant,
                )
            ),
        )
    }
}
