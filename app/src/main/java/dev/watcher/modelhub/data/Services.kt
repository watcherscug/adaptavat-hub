package dev.watcher.modelhub.data

import android.content.Context

object Services {
    lateinit var settings: SettingsStore
        private set
    lateinit var downloads: DownloadManager
        private set

    val hf: HuggingFace by lazy { HuggingFace() }
    lateinit var workshop: dev.watcher.modelhub.plugins.Workshop
        private set

    fun init(context: Context) {
        if (!::settings.isInitialized) settings = SettingsStore(context)
        if (!::downloads.isInitialized) downloads = DownloadManager(context)
        if (!::workshop.isInitialized) workshop = dev.watcher.modelhub.plugins.Workshop(context.applicationContext)
    }
}
