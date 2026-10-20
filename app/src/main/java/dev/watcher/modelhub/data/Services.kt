package dev.watcher.modelhub.data

import android.content.Context

object Services {
    lateinit var settings: SettingsStore
        private set
    lateinit var downloads: DownloadManager
        private set

    val hf: HuggingFace by lazy { HuggingFace() }

    fun init(context: Context) {
        if (!::settings.isInitialized) settings = SettingsStore(context)
        if (!::downloads.isInitialized) downloads = DownloadManager(context)
    }
}
