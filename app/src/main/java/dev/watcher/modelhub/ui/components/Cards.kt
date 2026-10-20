package dev.watcher.modelhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.R
import dev.watcher.modelhub.data.Advice
import dev.watcher.modelhub.data.GgufFile
import dev.watcher.modelhub.data.HardwareInfo
import dev.watcher.modelhub.data.HfModel
import dev.watcher.modelhub.data.RepoCheck
import dev.watcher.modelhub.data.formatBytes
import dev.watcher.modelhub.data.formatCount
import dev.watcher.modelhub.data.formatGb

@Composable
fun AdviceCard(
    hardware: HardwareInfo,
    advice: Advice,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    SurfaceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_memory),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Под твоё железо",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Пересчитать") }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = advice.headline,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = advice.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MiniStat("RAM", "${formatGb(hardware.totalRamGb)} ГБ")
            MiniStat("Ядра", "${hardware.cores}")
            MiniStat("Свободно", "${formatGb(hardware.freeStorageGb)} ГБ")
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun RepoCheckCard(
    check: RepoCheck,
    modifier: Modifier = Modifier,
    onDownload: (HfModel, GgufFile) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (tint, title, body) = when (check) {
        is RepoCheck.Model -> Triple(
            colors.tertiary,
            "Модель подтверждена",
            "${check.model.id} · type=model · ${check.model.files.size} GGUF",
        )
        is RepoCheck.Dataset -> Triple(
            colors.error,
            "Это датасет, а не модель",
            "${check.id} · type=dataset · ${formatCount(check.downloads)} загрузок. Весов модели здесь нет.",
        )
        is RepoCheck.ModelWithoutGguf -> Triple(
            colors.error,
            "Модель без GGUF",
            "${check.id} · в репозитории нет .gguf файлов, скачивать нечего.",
        )
        is RepoCheck.NotFound -> Triple(
            colors.error,
            "Репозиторий не найден",
            "${check.id} нет среди публичных моделей и датасетов — либо опечатка, либо репозиторий приватный/gated.",
        )
        is RepoCheck.InvalidId -> Triple(
            colors.error,
            "Неверный repo ID",
            "Нужен формат owner/name, например bartowski/Qwen2.5-3B-Instruct-GGUF.",
        )
        is RepoCheck.Failed -> Triple(
            colors.error,
            "Проверка не удалась",
            check.message,
        )
    }

    SurfaceCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (check is RepoCheck.Model) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        val model = (check as? RepoCheck.Model)?.model
        val file = model?.bestFile
        if (model != null && file != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(file.quant, formatBytes(file.sizeBytes)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                FilledTonalButton(onClick = { onDownload(model, file) }) {
                    Text("Скачать")
                }
            }
        }
    }
}

@Composable
fun NoticeRow(text: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}
