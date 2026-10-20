package dev.watcher.modelhub.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

enum class Density(val label: String, val scale: Float) {
    COMPACT("Плотно", 0.78f),
    COMFORTABLE("Обычно", 1f),
    AIRY("Воздушно", 1.3f),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentArgb: Int? = null,
    val density: Density = Density.COMFORTABLE,
    val cornerScale: Float = 1f,
    val showTags: Boolean = true,
    val showStats: Boolean = true,
    val showAdvice: Boolean = true,
)

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("modelhub.settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state

    private fun load(): AppSettings = AppSettings(
        themeMode = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.SYSTEM },
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        accentArgb = prefs.getInt(KEY_ACCENT, 0).takeIf { it != 0 },
        density = Density.entries.getOrElse(prefs.getInt(KEY_DENSITY, 1)) { Density.COMFORTABLE },
        cornerScale = prefs.getFloat(KEY_CORNER, 1f),
        showTags = prefs.getBoolean(KEY_TAGS, true),
        showStats = prefs.getBoolean(KEY_STATS, true),
        showAdvice = prefs.getBoolean(KEY_ADVICE, true),
    )

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putInt(KEY_THEME, next.themeMode.ordinal)
            .putBoolean(KEY_DYNAMIC, next.dynamicColor)
            .putInt(KEY_ACCENT, next.accentArgb ?: 0)
            .putInt(KEY_DENSITY, next.density.ordinal)
            .putFloat(KEY_CORNER, next.cornerScale)
            .putBoolean(KEY_TAGS, next.showTags)
            .putBoolean(KEY_STATS, next.showStats)
            .putBoolean(KEY_ADVICE, next.showAdvice)
            .apply()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic"
        const val KEY_ACCENT = "accent"
        const val KEY_DENSITY = "density"
        const val KEY_CORNER = "corner"
        const val KEY_TAGS = "tags"
        const val KEY_STATS = "stats"
        const val KEY_ADVICE = "advice"
    }
}

val AccentPalette: List<Pair<String, Long>> = listOf(
    "Пиксель" to 0xFF0B57D0,
    "Индиго" to 0xFF4F5B92,
    "Изумруд" to 0xFF146C2E,
    "Графит" to 0xFF3C4043,
    "Терракота" to 0xFF9A4600,
    "Вишня" to 0xFFB3261E,
    "Океан" to 0xFF00639B,
    "Аметист" to 0xFF6750A4,
)
