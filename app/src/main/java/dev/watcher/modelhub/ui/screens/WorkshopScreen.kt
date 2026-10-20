package dev.watcher.modelhub.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.watcher.modelhub.data.Services
import dev.watcher.modelhub.ui.components.HubHeader
import dev.watcher.modelhub.ui.components.SurfaceCard

@Composable
fun WorkshopScreen() {
    val workshop = Services.workshop
    val output by workshop.transcript.collectAsState()
    val busy by workshop.busy.collectAsState()
    val plugins by workshop.plugins.collectAsState()
    var command by rememberSaveable { mutableStateOf("") }
    val pluginImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workshop.importFile(uri, true)
    }
    val modelImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workshop.importFile(uri, false)
    }
    val scroll = rememberScrollState()
    LaunchedEffect(output) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HubHeader("Мастерская", "ADPP · Python · llama.cpp")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { pluginImport.launch(arrayOf("*/*")) }, enabled = !busy) { Text("+ ADPP") }
            OutlinedButton(onClick = { modelImport.launch(arrayOf("*/*")) }, enabled = !busy) { Text("+ GGUF") }
            TextButton(onClick = { workshop.clear() }, enabled = !busy) { Text("Очистить") }
        }
        if (plugins.isNotEmpty()) {
            Column(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
                plugins.forEach { plugin ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${plugin.name} · ${plugin.language}", Modifier.weight(1f))
                        Switch(plugin.enabled, { workshop.setEnabled(plugin.id, it) }, enabled = !busy)
                        TextButton({ workshop.remove(plugin.id) }, enabled = !busy) { Text("Удалить") }
                    }
                }
            }
        }
        SurfaceCard(Modifier.weight(1f)) {
            SelectionContainer {
                Text(output, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll))
            }
        }
        Text(if (busy) "Выполняется…" else "help · models · packages · py import numpy as np", style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(command, { command = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Команда / py <код>") }, maxLines = 5, enabled = !busy)
        FilledTonalButton(onClick = { workshop.submit(command); command = "" }, enabled = !busy && command.isNotBlank()) { Text("Выполнить") }
    }
}
