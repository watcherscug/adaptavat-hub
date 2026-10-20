package dev.watcher.modelhub.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.data.Services
import dev.watcher.modelhub.plugins.InstalledPlugin
import dev.watcher.modelhub.plugins.PluginCatalogItem
import dev.watcher.modelhub.ui.components.HubHeader
import dev.watcher.modelhub.ui.components.SurfaceCard
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class WorkshopPage(val label: String) {
    PLUGINS("Плагины"), CATALOG("Каталог"), CONSOLE("Консоль")
}

@Composable
fun WorkshopScreen() {
    val workshop = Services.workshop
    val output by workshop.transcript.collectAsState()
    val busy by workshop.busy.collectAsState()
    val plugins by workshop.plugins.collectAsState()
    val scope = rememberCoroutineScope()

    var page by rememberSaveable { mutableStateOf(WorkshopPage.PLUGINS.name) }
    var command by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<PluginCatalogItem>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var installingId by remember { mutableStateOf<String?>(null) }
    var selectedCommand by remember { mutableStateOf<Pair<InstalledPlugin, String>?>(null) }
    var arguments by rememberSaveable { mutableStateOf("{}") }
    var argumentError by remember { mutableStateOf<String?>(null) }

    val pluginImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workshop.importFile(uri, true)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(output, page) {
        if (page == WorkshopPage.CONSOLE.name) scroll.scrollTo(scroll.maxValue)
    }

    fun searchCatalog() {
        val query = search.trim()
        if (query.isEmpty() || catalogLoading) return
        scope.launch {
            catalogLoading = true
            catalogError = null
            runCatching { workshop.searchCatalog(query) }
                .onSuccess { catalog = it }
                .onFailure { catalogError = it.message ?: "Не удалось загрузить каталог" }
            catalogLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HubHeader("Мастерская", "Плагины · Python · Kotlin")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkshopPage.entries.forEach { item ->
                FilterChip(
                    selected = page == item.name,
                    onClick = { page = item.name },
                    label = { Text(item.label) },
                )
            }
        }

        when (page) {
            WorkshopPage.PLUGINS.name -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { pluginImport.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) {
                        Text("Установить .adpp")
                    }
                }
                if (plugins.isEmpty()) {
                    SurfaceCard {
                        Text("Плагины пока не установлены", style = MaterialTheme.typography.titleMedium)
                        Text("Открой каталог или импортируй файл .adpp", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(plugins, key = { it.id }) { plugin ->
                            InstalledPluginCard(
                                plugin = plugin,
                                busy = busy,
                                onEnabled = { workshop.setEnabled(plugin.id, it) },
                                onRemove = { workshop.remove(plugin.id) },
                                onCommand = { selectedCommand = plugin to it; arguments = "{}"; argumentError = null },
                            )
                        }
                    }
                }
            }

            WorkshopPage.CATALOG.name -> {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Поиск плагинов Hugging Face") },
                    trailingIcon = {
                        TextButton(onClick = { searchCatalog() }, enabled = !catalogLoading && search.isNotBlank()) {
                            Text("Найти")
                        }
                    },
                )
                if (catalogLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(20.dp))
                }
                catalogError?.let { SurfaceCard { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (catalog.isEmpty() && !catalogLoading && catalogError == null) {
                    Text("Введи запрос. Каталог показывает только публичные репозитории с manifest.json типа adpp-plugin.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(catalog, key = { it.id }) { item ->
                            CatalogPluginCard(
                                item = item,
                                installed = plugins.any { it.id == item.id },
                                busy = busy || installingId != null,
                                onInstall = {
                                    scope.launch {
                                        installingId = item.id
                                        runCatching { workshop.installFromCatalog(item) }
                                            .onFailure { catalogError = it.message ?: "Не удалось установить плагин" }
                                        installingId = null
                                    }
                                },
                            )
                        }
                    }
                }
            }

            else -> {
                SurfaceCard(Modifier.weight(1f)) {
                    SelectionContainer {
                        Text(
                            text = output,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxSize().verticalScroll(scroll),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { workshop.clear() }, enabled = !busy) { Text("Очистить вывод") }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Команда / py <код>") },
                    maxLines = 4,
                    enabled = !busy,
                )
                FilledTonalButton(
                    onClick = { workshop.submit(command); command = "" },
                    enabled = !busy && command.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Выполняется…" else "Выполнить") }
            }
        }
    }

    selectedCommand?.let { (plugin, name) ->
        AlertDialog(
            onDismissRequest = { if (!busy) selectedCommand = null },
            title = { Text("${plugin.name} · $name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Аргументы команды в формате JSON")
                    OutlinedTextField(
                        value = arguments,
                        onValueChange = { arguments = it; argumentError = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        minLines = 3,
                        enabled = !busy,
                    )
                    argumentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    runCatching { JSONObject(arguments) }
                        .onSuccess {
                            workshop.executePluginCommand(plugin.id, name, arguments)
                            selectedCommand = null
                            page = WorkshopPage.CONSOLE.name
                        }
                        .onFailure { argumentError = "Некорректный JSON: ${it.message}" }
                }) { Text("Запустить") }
            },
            dismissButton = { TextButton(onClick = { selectedCommand = null }, enabled = !busy) { Text("Отмена") } },
        )
    }
}

@Composable
private fun InstalledPluginCard(
    plugin: InstalledPlugin,
    busy: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onCommand: (String) -> Unit,
) {
    SurfaceCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleMedium)
                Text("${plugin.id} · ${plugin.version} · ${plugin.language}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = plugin.enabled, onCheckedChange = onEnabled, enabled = !busy)
            IconButton(onClick = onRemove, enabled = !busy) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить ${plugin.name}")
            }
        }
        if (plugin.commands.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plugin.commands.forEach { name ->
                    OutlinedButton(onClick = { onCommand(name) }, enabled = plugin.enabled && !busy) { Text(name) }
                }
            }
        }
    }
}

@Composable
private fun CatalogPluginCard(
    item: PluginCatalogItem,
    installed: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    SurfaceCard {
        Text(item.name, style = MaterialTheme.typography.titleMedium)
        Text("${item.id} · ${item.version} · ${item.language}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.description, style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onInstall, enabled = !installed && !busy) {
            Text(when { installed -> "Установлен"; busy -> "Установка…"; else -> "Установить" })
        }
    }
}
