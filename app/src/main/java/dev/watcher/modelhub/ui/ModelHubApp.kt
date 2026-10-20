package dev.watcher.modelhub.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.watcher.modelhub.R
import dev.watcher.modelhub.data.HardwareInfo
import dev.watcher.modelhub.data.Services
import dev.watcher.modelhub.data.advice
import dev.watcher.modelhub.ui.screens.ChatScreen
import dev.watcher.modelhub.ui.screens.DownloadsScreen
import dev.watcher.modelhub.ui.screens.ModelsScreen
import dev.watcher.modelhub.ui.screens.SettingsScreen
import dev.watcher.modelhub.ui.theme.ModelHubTheme

@Composable
fun ModelHubApp() {
    val context = LocalContext.current
    val settings by Services.settings.state.collectAsState()

    ModelHubTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor,
        accentArgb = settings.accentArgb,
        cornerScale = settings.cornerScale,
    ) {
        CompositionLocalProvider(LocalUiConfig provides uiConfigFor(settings)) {
            var hardware by remember { mutableStateOf(HardwareInfo.read(context)) }
            var advice by remember { mutableStateOf(hardware.advice()) }
            var tab by rememberSaveable { mutableStateOf(0) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            val modelsState = remember { ModelsUiState() }
            val chatState = remember { ChatUiState() }

            fun refreshHardware() {
                hardware = HardwareInfo.read(context)
                advice = hardware.advice()
            }

            LaunchedEffect(tab) {
                if (tab == 1) refreshHardware()
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!showSettings) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_memory),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text("Модели") },
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_cloud_download),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text("Загрузки") },
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_chat),
                                        contentDescription = null,
                                    )
                                },
                                label = { Text("Чат") },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Crossfade(
                    targetState = if (showSettings) -1 else tab,
                    label = "screen",
                ) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        when (index) {
                            -1 -> SettingsScreen(
                                settings = settings,
                                hardware = hardware,
                                onChange = { transform -> Services.settings.update(transform) },
                                onBack = { showSettings = false },
                            )

                            0 -> ModelsScreen(
                                state = modelsState,
                                settings = settings,
                                hardware = hardware,
                                advice = advice,
                                onRefreshHardware = { refreshHardware() },
                                onOpenSettings = { showSettings = true },
                                onOpenChat = { tab = 2 },
                            )

                            1 -> DownloadsScreen(
                                hardware = hardware,
                                onOpenModels = { tab = 0 },
                            )

                            else -> ChatScreen(
                                state = chatState,
                                onOpenModels = { tab = 0 },
                            )
                        }
                    }
                }
            }
        }
    }
}
