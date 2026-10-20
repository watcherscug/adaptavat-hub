package dev.watcher.modelhub.plugins

/** API v1: compile against this interface; do not bundle a duplicate copy in a plugin. */
interface AdppPlugin {
    fun execute(api: PluginApi, command: String, argumentsJson: String): String
}

interface PluginApi {
    fun version(): Int
    fun log(message: String)
    fun workspace(): String
    fun models(): String
    fun invoke(command: String, argumentsJson: String): String
}

data class PluginCatalogItem(
    val id: String,
    val name: String,
    val version: String,
    val language: String,
    val description: String,
    val downloadUrl: String,
)
