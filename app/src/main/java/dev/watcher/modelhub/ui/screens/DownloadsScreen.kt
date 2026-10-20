package dev.watcher.modelhub.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.R
import dev.watcher.modelhub.data.DownloadStatus
import dev.watcher.modelhub.data.DownloadTask
import dev.watcher.modelhub.data.HardwareInfo
import dev.watcher.modelhub.data.Services
import dev.watcher.modelhub.data.formatBytes
import dev.watcher.modelhub.data.formatGb
import dev.watcher.modelhub.ui.LocalUiConfig
import dev.watcher.modelhub.ui.components.EmptyState
import dev.watcher.modelhub.ui.components.HubHeader
import dev.watcher.modelhub.ui.components.SectionHeader
import dev.watcher.modelhub.ui.components.StorageBar
import dev.watcher.modelhub.ui.components.SurfaceCard
import java.util.Locale

@Composable
fun DownloadsScreen(
    hardware: HardwareInfo,
    onOpenModels: () -> Unit,
) {
    val ui = LocalUiConfig.current
    val colors = MaterialTheme.colorScheme
    val tasks by Services.downloads.tasks.collectAsState()

    val active = tasks.filter { it.status != DownloadStatus.DONE }
    val done = tasks.filter { it.status == DownloadStatus.DONE }
    val usedGb = done.sumOf { it.bytes } / (1024.0 * 1024.0 * 1024.0)

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
                title = "Загрузки",
                subtitle = "Прямые файлы с huggingface.co",
            )
        }

        item {
            SurfaceCard {
                StorageBar(
                    usedGb = usedGb,
                    freeGb = hardware.freeStorageGb,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Моделей скачано",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "${done.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Активных",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = "${active.size}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface,
                        )
                    }
                }
            }
        }

        if (active.isNotEmpty()) {
            item { SectionHeader("В процессе") }
            items(active, key = { it.key }) { task -> ActiveTask(task) }
        }

        if (done.isNotEmpty()) {
            item { SectionHeader("Скачано") }
            items(done, key = { it.key }) { task -> DoneTask(task) }
        }

        if (tasks.isEmpty()) {
            item {
                EmptyState(
                    title = "Пока пусто",
                    body = "Выбери модель во вкладке «Модели» — файл поедет прямо с Hugging Face.",
                    actionLabel = "К моделям",
                    onAction = onOpenModels,
                )
            }
        }

        item {
            Text(
                text = "Файлы лежат в files/models, без облака и посредников.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ActiveTask(task: DownloadTask) {
    val colors = MaterialTheme.colorScheme
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.repoId,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (task.status) {
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> IconButton(
                    onClick = { Services.downloads.resume(task.key) },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Продолжить")
                }
                DownloadStatus.QUEUED -> Box32 { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
                else -> IconButton(onClick = { Services.downloads.pause(task.key) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pause),
                        contentDescription = "Пауза",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = { Services.downloads.cancel(task.key) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить")
            }
        }

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { task.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (task.status == DownloadStatus.FAILED) colors.error else colors.primary,
            trackColor = colors.surfaceContainerHighest,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                text = "${formatBytes(task.bytes)} / ${formatBytes(task.totalBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = statusLine(task),
                style = MaterialTheme.typography.labelSmall,
                color = if (task.status == DownloadStatus.FAILED) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneTask(task: DownloadTask) {
    val colors = MaterialTheme.colorScheme
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${task.repoId} · ${formatBytes(task.bytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { Services.downloads.cancel(task.key) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить файл")
            }
        }
    }
}

@Composable
private fun Box32(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun statusLine(task: DownloadTask): String {
    val percent = (task.progress * 100).toInt()
    return when (task.status) {
        DownloadStatus.PAUSED -> "Пауза · $percent%"
        DownloadStatus.FAILED -> task.error ?: "Ошибка"
        DownloadStatus.QUEUED -> "В очереди"
        DownloadStatus.DONE -> "Готово"
        DownloadStatus.RUNNING -> {
            val speed = task.speedBps / (1024.0 * 1024.0)
            if (speed > 0.1) {
                String.format(Locale.US, "%d%% · %.1f МБ/с", percent, speed)
            } else {
                "$percent%"
            }
        }
    }
}
