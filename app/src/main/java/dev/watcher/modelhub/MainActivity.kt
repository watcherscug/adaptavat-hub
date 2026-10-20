package dev.watcher.modelhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.watcher.modelhub.ui.ModelHubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val chatState = androidx.lifecycle.ViewModelProvider(this)[dev.watcher.modelhub.ui.ChatUiState::class.java]
        setContent {
            ModelHubApp(chatState)
        }
    }
}
