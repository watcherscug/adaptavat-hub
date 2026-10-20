package dev.watcher.modelhub.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.util.Locale

private const val GB = 1024.0 * 1024.0 * 1024.0

data class HardwareInfo(
    val totalRamGb: Double,
    val availRamGb: Double,
    val cores: Int,
    val freeStorageGb: Double,
    val abi: String,
) {
    companion object {
        fun read(context: Context): HardwareInfo {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
            val stat = StatFs(context.filesDir.absolutePath)
            return HardwareInfo(
                totalRamGb = mi.totalMem / GB,
                availRamGb = mi.availMem / GB,
                cores = Runtime.getRuntime().availableProcessors(),
                freeStorageGb = stat.availableBytes / GB,
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            )
        }
    }
}

data class Advice(
    val sweetSpotB: Double,
    val maxParamsB: Double,
    val quant: String,
    val budgetGb: Double,
    val headline: String,
    val detail: String,
)

fun HardwareInfo.advice(): Advice {
    val budget = (minOf(availRamGb, totalRamGb * 0.6) - 1.2).coerceAtLeast(0.4)
    val maxParams = (budget / 0.62).coerceAtLeast(0.5)

    val sweetSpot = when {
        maxParams < 1.5 -> 0.5
        maxParams < 3.0 -> 1.5
        maxParams < 6.0 -> 3.0
        maxParams < 10.0 -> 4.0
        maxParams < 16.0 -> 8.0
        maxParams < 24.0 -> 14.0
        else -> 32.0
    }

    val quant = when {
        totalRamGb < 5 -> "Q4_0"
        totalRamGb < 8 -> "Q4_K_M"
        else -> "Q5_K_M"
    }

    val sweet = if (sweetSpot % 1.0 == 0.0) sweetSpot.toInt().toString() else sweetSpot.toString()

    return Advice(
        sweetSpotB = sweetSpot,
        maxParamsB = maxParams,
        quant = quant,
        budgetGb = budget,
        headline = "$sweet B · $quant",
        detail = buildString {
            append("Бюджет ≈ ")
            append(formatGb(budget))
            append(" ГБ из ")
            append(formatGb(totalRamGb))
            append(" ГБ RAM. Резерв под систему и KV-кэш уже вычтен.")
            if (maxParams < 1.0) append(" Такой телефон лучше оставить под 0.5–1B.")
        },
    )
}

fun HardwareInfo.fits(sizeGb: Double): Boolean =
    sizeGb + 1.2 <= minOf(availRamGb, totalRamGb * 0.75)

fun formatGb(value: Double): String = String.format(Locale.US, "%.1f", value)

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val gb = bytes / GB
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.2f ГБ", gb)
    } else {
        val mb = bytes / (1024.0 * 1024.0)
        String.format(Locale.US, "%.0f МБ", mb)
    }
}

fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}
