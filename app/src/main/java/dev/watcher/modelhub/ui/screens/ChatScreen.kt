package dev.watcher.modelhub.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.data.DownloadStatus
import dev.watcher.modelhub.data.Services
import dev.watcher.modelhub.ui.ChatMessage
import dev.watcher.modelhub.ui.ChatUiState
import dev.watcher.modelhub.ui.LocalUiConfig
import dev.watcher.modelhub.ui.components.EmptyState
import dev.watcher.modelhub.ui.components.FilterPill
import dev.watcher.modelhub.ui.components.HubHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    state: ChatUiState,
    onOpenModels: () -> Unit,
) {
    val ui = LocalUiConfig.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val tasks by Services.downloads.tasks.collectAsState()

    val available = tasks.filter { it.status == DownloadStatus.DONE }
    val current = available.firstOrNull { it.key == state.selectedKey } ?: available.firstOrNull()

    fun send() {
        val text = state.input.trim()
        if (text.isEmpty()) return
        state.messages += ChatMessage(state.nextId++, text, true)
        state.input = ""
        state.typing = true
        scope.launch {
            delay(900)
            state.messages += ChatMessage(state.nextId++, reply(text), false)
            state.typing = false
        }
    }

    LaunchedEffect(state.messages.size, state.typing) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex + if (state.typing) 1 else 0)
        }
    }

    if (available.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ui.pagePadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                title = "Нужна модель",
                body = "Сначала скачай GGUF во вкладке «Модели». Чат поднимется поверх локального файла.",
                actionLabel = "К моделям",
                onAction = onOpenModels,
            )
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = ui.pagePadding,
                end = ui.pagePadding,
                top = 8.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HubHeader(
                    title = "Чат",
                    subtitle = current?.fileName ?: "Локальная модель",
                )
            }

            if (available.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(available, key = { it.key }) { task ->
                            FilterPill(
                                text = task.fileName.removeSuffix(".gguf"),
                                selected = task.key == current?.key,
                                onClick = { state.selectedKey = task.key },
                            )
                        }
                    }
                }
            }

            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (state.typing) {
                item { TypingBubble() }
            }

            item {
                Text(
                    text = "Демо-ответы. Движок llama.cpp подключим следующим шагом.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = ui.pagePadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (state.input.isEmpty()) {
                        Text(
                            text = "Сообщение",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = state.input,
                        onValueChange = { state.input = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = { send() },
                enabled = state.input.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(MaterialTheme.shapes.large)
                .background(if (message.fromUser) colors.primaryContainer else colors.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.fromUser) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(3) { index ->
                val alpha = 0.25f + 0.75f * (((phase + index * 0.25f) % 1f))
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.onSurfaceVariant.copy(alpha = alpha)),
                )
            }
        }
    }
}

private fun reply(text: String): String {
    val lower = text.lowercase()
    return when {
        lower.contains("привет") || lower.contains("hello") ->
            "Привет. Пока я отвечаю заглушкой, но интерфейс и загрузка моделей уже настоящие."
        lower.contains("сколько") ->
            "N параметров × байты на параметр ≈ размер файла. Q4_K_M — примерно 0.6 ГБ на 1B параметров."
        lower.contains("квант") || lower.contains("quant") ->
            "Q4_K_M — лучший баланс качества и RAM для телефона. Q5_K_M тяжелее, Q4_0 легче, но грубее."
        lower.contains("ram") || lower.contains("памят") ->
            "Оставь системе ~1.2 ГБ и под KV-кэш ещё немного. Всё, что больше, уйдёт в свап и будет тормозить."
        else -> "Принял. Сейчас это демо-ветка диалога: чек-лист — llama.cpp через NDK и стриминг токенов."
    }
}
