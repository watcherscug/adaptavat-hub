package example

import dev.watcher.modelhub.plugins.AdppPlugin
import dev.watcher.modelhub.plugins.PluginApi

class InventoryPlugin : AdppPlugin {
    override fun execute(api: PluginApi, command: String, argumentsJson: String): String {
        require(command == "summary")
        api.log("Kotlin plugin API v${api.version()}")
        return api.models()
    }
}
