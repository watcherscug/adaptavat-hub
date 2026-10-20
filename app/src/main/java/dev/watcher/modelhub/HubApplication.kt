package dev.watcher.modelhub

import android.app.Application
import dev.watcher.modelhub.data.Services

class HubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Services.init(this)
    }
}
