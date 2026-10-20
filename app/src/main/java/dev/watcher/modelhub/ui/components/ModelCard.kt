package dev.watcher.modelhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.R
import dev.watcher.modelhub.data.HfModel
import dev.watcher.modelhub.data.formatBytes
import dev.watcher.modelhub.ui.LocalUiConfig

@Composable
fun ModelCard(
    model: HfModel,
    downloaded: Boolean,
    progress: Float?,
    fits: Boolean?,
    busy: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = LocalUiConfig.current
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), MaterialTheme.shapes.large)
            .clickable(onClick = onAction)
            .padding(ui.cardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.primaryContainer.copy(alpha = if (downloaded) 0.9f else 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_memory),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (ui.showStats) model.metaLine() else model.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fits == false) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Может не влезть в RAM",
                        tint = colors.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (ui.showTags && model.tags.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.tags
                        .filterNot { it.equals("gguf", true) || it.equals("text-generation", true) }
                        .take(2)
                        .forEach { TagPill(it) }
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        when {
            downloaded -> DoneBadge()
            progress != null -> ProgressBadge(progress)
            else -> DownloadButton(busy = busy, onClick = onAction)
        }
    }
}

@Composable
private fun DownloadButton(busy: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_download),
                contentDescription = "Скачать",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DoneBadge() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Скачано",
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProgressBadge(progress: Float) {
    Box(
        modifier = Modifier.size(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(38.dp),
            strokeWidth = 3.dp,
        )
        Text(
            text = "${(progress * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = CircleShape,
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun HfModel.metaLine(): String {
    val params = paramsB?.let { if (it % 1.0 == 0.0) "${it.toInt()}B" else "${it}B" }
    val file = bestFile
    val parts = mutableListOf<String>()
    parts += author
    params?.let { parts += it }
    file?.quant?.let { parts += it }
    file?.sizeBytes?.takeIf { it > 0L }?.let { parts += formatBytes(it) }
    if (file == null) {
        if (downloads > 0L) parts += "↓ ${dev.watcher.modelhub.data.formatCount(downloads)}"
        if (likes > 0L) parts += "♥ ${dev.watcher.modelhub.data.formatCount(likes)}"
    }
    return parts.joinToString(" · ")
}
