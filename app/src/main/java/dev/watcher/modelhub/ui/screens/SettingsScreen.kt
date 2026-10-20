package dev.watcher.modelhub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.data.AccentPalette
import dev.watcher.modelhub.data.AppSettings
import dev.watcher.modelhub.data.Density
import dev.watcher.modelhub.data.HardwareInfo
import dev.watcher.modelhub.data.ThemeMode
import dev.watcher.modelhub.data.formatGb
import dev.watcher.modelhub.ui.LocalUiConfig
import dev.watcher.modelhub.ui.components.FilterPill
import dev.watcher.modelhub.ui.components.InfoRow
import dev.watcher.modelhub.ui.components.SectionHeader
import dev.watcher.modelhub.ui.components.SurfaceCard

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hardware: HardwareInfo,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val ui = LocalUiConfig.current
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ui.pagePadding,
            end = ui.pagePadding,
            top = 8.dp,
            bottom = 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(ui.gap),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader("Внешний вид") }

        item {
            SurfaceCard {
                FieldLabel("Тема")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterPill(
                            text = mode.label,
                            selected = settings.themeMode == mode,
                            onClick = { onChange { it.copy(themeMode = mode) } },
                        )
                    }
                }

                CardDivider()

                SwitchRow(
                    title = "Динамический цвет",
                    subtitle = "Material You, Android 12+",
                    checked = settings.dynamicColor,
                    onCheckedChange = { value -> onChange { it.copy(dynamicColor = value) } },
                )

                FieldLabel("Акцент", topPadding = 8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccentPalette.forEach { (name, argb) ->
                        val color = Color(argb)
                        val selected = settings.accentArgb == color.toArgb()
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) colors.onSurface else colors.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    onChange {
                                        it.copy(accentArgb = if (selected) null else color.toArgb())
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (color.luminance() > 0.55f) Color.Black else Color.White,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = if (settings.dynamicColor) {
                        "Акцент применяется, когда динамический цвет выключен."
                    } else {
                        "Нажми на выбранный цвет ещё раз, чтобы сбросить."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )

                CardDivider()

                FieldLabel("Плотность")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Density.entries.forEach { density ->
                        FilterPill(
                            text = density.label,
                            selected = settings.density == density,
                            onClick = { onChange { it.copy(density = density) } },
                        )
                    }
                }

                CardDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Скругления")
                        Text(
                            text = "${(settings.cornerScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape((14 * settings.cornerScale).dp))
                            .background(colors.primaryContainer),
                    )
                }
                Slider(
                    value = settings.cornerScale,
                    onValueChange = { value -> onChange { it.copy(cornerScale = value) } },
                    valueRange = 0f..1.6f,
                )
            }
        }

        item { SectionHeader("Содержимое") }

        item {
            SurfaceCard {
                SwitchRow(
                    title = "Теги моделей",
                    subtitle = "Короткие метки под названием",
                    checked = settings.showTags,
                    onCheckedChange = { value -> onChange { it.copy(showTags = value) } },
                )
                SwitchRow(
                    title = "Размер и квант",
                    subtitle = "Строка параметров в карточке",
                    checked = settings.showStats,
                    onCheckedChange = { value -> onChange { it.copy(showStats = value) } },
                )
                SwitchRow(
                    title = "Подсказка по железу",
                    subtitle = "Рекомендация по RAM на главной",
                    checked = settings.showAdvice,
                    onCheckedChange = { value -> onChange { it.copy(showAdvice = value) } },
                )
            }
        }

        item { SectionHeader("Устройство") }

        item {
            SurfaceCard {
                InfoRow("RAM", "${formatGb(hardware.totalRamGb)} ГБ")
                InfoRow("Доступно RAM", "${formatGb(hardware.availRamGb)} ГБ")
                InfoRow("Ядра", "${hardware.cores}")
                InfoRow("Свободно на диске", "${formatGb(hardware.freeStorageGb)} ГБ")
                InfoRow("ABI", hardware.abi)
            }
        }

        item { SectionHeader("О системе") }

        item {
            SurfaceCard {
                InfoRow("Версия", "0.1.0 · UI + HF API")
                InfoRow("Источник моделей", "huggingface.co/api/models")
                InfoRow("Фильтр", "type=model · gguf")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "«Максимум кастома. Максимум минимализма.»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { onChange { AppSettings() } }) {
                    Text("Сбросить настройки")
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = topPadding, bottom = 6.dp),
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
