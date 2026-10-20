package dev.watcher.modelhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.data.Advice
import dev.watcher.modelhub.data.AppSettings
import dev.watcher.modelhub.data.DownloadStatus
import dev.watcher.modelhub.data.HardwareInfo
import dev.watcher.modelhub.data.HfModel
import dev.watcher.modelhub.data.RepoCheck
import dev.watcher.modelhub.data.SampleModels
import dev.watcher.modelhub.data.Services
import dev.watcher.modelhub.data.fits
import dev.watcher.modelhub.ui.LocalUiConfig
import dev.watcher.modelhub.ui.ModelFilters
import dev.watcher.modelhub.ui.ModelsUiState
import dev.watcher.modelhub.ui.components.AdviceCard
import dev.watcher.modelhub.ui.components.FilterPill
import dev.watcher.modelhub.ui.components.HubHeader
import dev.watcher.modelhub.ui.components.ModelCard
import dev.watcher.modelhub.ui.components.NoticeRow
import dev.watcher.modelhub.ui.components.RepoCheckCard
import dev.watcher.modelhub.ui.components.SearchPill
import dev.watcher.modelhub.ui.components.SectionHeader
import dev.watcher.modelhub.ui.components.SurfaceCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ModelsScreen(
    state: ModelsUiState,
    settings: AppSettings,
    hardware: HardwareInfo,
    advice: Advice,
    onRefreshHardware: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val ui = LocalUiConfig.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val tasks by Services.downloads.tasks.collectAsState()

    LaunchedEffect(state.query) {
        if (state.loadedQuery == state.query) return@LaunchedEffect
        delay(400)
        state.loading = true
        runCatching { Services.hf.search(state.query) }
            .onSuccess {
                state.models = it
                state.offline = false
            }
            .onFailure {
                state.offline = true
                if (state.models.isEmpty()) state.models = SampleModels.all
            }
        state.loadedQuery = state.query
        state.loading = false
    }

    fun download(model: HfModel) {
        val file = model.bestFile
        if (file != null) {
            Services.downloads.start(model, file)
            return
        }
        if (model.id in state.busy) return
        scope.launch {
            state.busy = state.busy + model.id
            val check = Services.hf.inspect(model.id)
            state.checkResult = check
            if (check is RepoCheck.Model) {
                check.model.bestFile?.let { Services.downloads.start(check.model, it) }
            }
            state.busy = state.busy - model.id
        }
    }

    fun runCheck() {
        if (state.repoInput.isBlank() || state.checking) return
        scope.launch {
            state.checking = true
            state.checkResult = Services.hf.inspect(state.repoInput)
            state.checking = false
        }
    }

    val visible = state.models.filter { model ->
        when (state.filter) {
            "Чат" -> model.tags.any { it in setOf("chat", "conversational") } || model.pipelineTag == "text-generation"
            "Код" -> model.tags.any { it.contains("code", true) }
            "Vision" -> model.tags.any { it in setOf("vision", "image-text-to-text", "multimodal") }
            "Reasoning" -> model.tags.any { it.contains("reason") }
            "≤ 2B" -> (model.paramsB ?: 99.0) <= 2.0
            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ui.pagePadding,
            end = ui.pagePadding,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ui.gap),
    ) {
        item {
            HubHeader(
                title = "Model Hub",
                subtitle = "Локальные GGUF · Hugging Face",
                trailing = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        }

        item {
            SearchPill(value = state.query, onValueChange = { state.query = it })
        }

        if (settings.showAdvice) {
            item {
                AdviceCard(
                    hardware = hardware,
                    advice = advice,
                    onRefresh = onRefreshHardware,
                )
            }
        }

        item {
            SurfaceCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.checkOpen = !state.checkOpen },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Проверить repo ID",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Убедимся, что это модель, а не датасет",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (state.checkOpen) 180f else 0f),
                    )
                }

                if (state.checkOpen) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (state.repoInput.isEmpty()) {
                                Text(
                                    text = "owner/name",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            BasicTextField(
                                value = state.repoInput,
                                onValueChange = { state.repoInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                                cursorBrush = SolidColor(colors.primary),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { runCheck() },
                        enabled = state.repoInput.isNotBlank() && !state.checking,
                    ) {
                        if (state.checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.checking) "Проверяю" else "Проверить")
                    }
                }
            }
        }

        state.checkResult?.let { result ->
            item {
                RepoCheckCard(
                    check = result,
                    onDownload = { model, file -> Services.downloads.start(model, file) },
                )
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ModelFilters) { item ->
                    FilterPill(
                        text = item,
                        selected = item == state.filter,
                        onClick = { state.filter = item },
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = if (state.query.isBlank()) "Популярные GGUF" else "Результаты",
                trailing = {
                    Text(
                        text = "${visible.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                },
            )
        }

        if (state.offline) {
            item {
                NoticeRow(
                    text = "Hugging Face недоступен — показываю офлайн-подборку",
                    tint = colors.error,
                )
            }
        }

        if (state.loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                }
            }
        }

        items(visible, key = { it.id }) { model ->
            val task = tasks.firstOrNull { it.repoId == model.id && it.status != DownloadStatus.DONE }
            val downloaded = tasks.any { it.repoId == model.id && it.status == DownloadStatus.DONE }
            val sizeGb = model.bestFile?.sizeBytes?.takeIf { it > 0L }?.div(1024.0 * 1024.0 * 1024.0)
            ModelCard(
                model = model,
                downloaded = downloaded,
                progress = task?.progress,
                fits = sizeGb?.let { hardware.fits(it) },
                busy = model.id in state.busy,
                onAction = {
                    if (downloaded) onOpenChat() else download(model)
                },
            )
        }

        item {
            Text(
                text = "Минимум интерфейса. Максимум контроля.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}
