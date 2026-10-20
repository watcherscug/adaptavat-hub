package dev.watcher.modelhub.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.watcher.modelhub.data.HfModel
import dev.watcher.modelhub.data.RepoCheck

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
class ChatUiState {
    var input by mutableStateOf("")
    var selectedKey by mutableStateOf<String?>(null)
    var typing by mutableStateOf(false)
    var nextId by mutableStateOf(1L)
    val messages = mutableStateListOf(
        ChatMessage(
            id = 0,
            text = "Привет. Я работаю полностью на устройстве — после того как подключим движок llama.cpp. Выбери скачанную модель и пиши.",
            fromUser = false,
        )
    )
}
