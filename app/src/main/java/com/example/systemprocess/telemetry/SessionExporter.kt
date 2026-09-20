package com.example.systemprocess.telemetry

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializes a [TelemetryUiState] snapshot (plus its recorded history series) to CSV or JSON,
 * writes it to the app cache, and returns a shareable content [Uri] via [FileProvider].
 *
 * Nothing here requires extra permissions: files live in the app's own cache and are shared
 * through a scoped FileProvider grant.
 */
object SessionExporter {

    enum class Format(val extension: String, val mime: String) {
        CSV("csv", "text/csv"),
        JSON("json", "application/json")
    }

    fun export(context: Context, state: TelemetryUiState, format: Format): Uri {
        val content = when (format) {
            Format.CSV -> buildCsv(state)
            Format.JSON -> buildJson(state)
        }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "sysv_session_$stamp.${format.extension}")
        file.writeText(content)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Writes the export and launches a system share sheet for it. */
    fun exportAndShare(context: Context, state: TelemetryUiState, format: Format) {
        val uri = export(context, state, format)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SYS V telemetry session")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(share, "Export session").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    private fun buildCsv(s: TelemetryUiState): String = buildString {
        appendLine("# SYS V telemetry session export")
        appendLine("# generated,${isoNow()}")
        appendLine()

        appendLine("section,metric,value")
        appendLine("device,model,${csv(s.deviceModel)}")
        appendLine("device,android_version,${csv(s.androidVersion)}")
        appendLine("session,elapsed_seconds,${s.sessionElapsedSeconds}")
        appendLine("cpu,load_percent,${s.cpuLoadPercent}")
        appendLine("memory,total_gb,${s.totalMemoryGb}")
        appendLine("memory,available_gb,${s.availableMemoryGb}")
        appendLine("thermal,device_temp_c,${s.deviceTempC}")
        appendLine("thermal,state,${csv(s.thermalState)}")
        appendLine("battery,level_percent,${s.batteryLevelPercent}")
        appendLine("battery,current_micro_amps,${s.batteryCurrentMicroAmps}")
        appendLine("battery,temp_c,${s.batteryTempC}")
        appendLine()

        appendLine("memory_breakdown_label,value_gb")
        s.memoryBreakdown.forEach { appendLine("${csv(it.label)},${it.valueGb}") }
        appendLine()

        appendLine("thermal_core_id,frequency_mhz,temp_c,status")
        s.thermalCores.forEach { appendLine("${csv(it.id)},${it.frequencyMhz},${it.tempC},${csv(it.status)}") }
        appendLine()

        appendLine("process_name,pid,cpu_percent,mem,state,foreground")
        s.processes.forEach {
            appendLine("${csv(it.name)},${it.pid},${it.cpuPercent},${csv(it.memText)},${csv(it.state)},${it.isForeground}")
        }
        appendLine()

        appendLine("history_index,memory_used_gb,native_heap_gb,battery_percent")
        val maxLen = maxOf(s.memoryTimelineGb.size, s.nativeHeapTimelineGb.size, s.batteryPercentHistory.size)
        for (i in 0 until maxLen) {
            appendLine(
                "$i,${s.memoryTimelineGb.getOrNull(i) ?: ""}," +
                    "${s.nativeHeapTimelineGb.getOrNull(i) ?: ""}," +
                    "${s.batteryPercentHistory.getOrNull(i) ?: ""}"
            )
        }
    }

    private fun buildJson(s: TelemetryUiState): String = buildString {
        appendLine("{")
        appendLine("  \"generated\": \"${isoNow()}\",")
        appendLine("  \"device\": { \"model\": ${jstr(s.deviceModel)}, \"androidVersion\": ${jstr(s.androidVersion)} },")
        appendLine("  \"sessionElapsedSeconds\": ${s.sessionElapsedSeconds},")
        appendLine("  \"cpu\": { \"loadPercent\": ${s.cpuLoadPercent} },")
        appendLine("  \"memory\": { \"totalGb\": ${s.totalMemoryGb}, \"availableGb\": ${s.availableMemoryGb} },")
        appendLine("  \"thermal\": { \"deviceTempC\": ${s.deviceTempC}, \"state\": ${jstr(s.thermalState)} },")
        appendLine(
            "  \"battery\": { \"levelPercent\": ${s.batteryLevelPercent}, " +
                "\"currentMicroAmps\": ${s.batteryCurrentMicroAmps}, \"tempC\": ${s.batteryTempC} },"
        )
        appendLine("  \"memoryBreakdown\": [")
        appendLine(s.memoryBreakdown.joinToString(",\n") {
            "    { \"label\": ${jstr(it.label)}, \"valueGb\": ${it.valueGb} }"
        })
        appendLine("  ],")
        appendLine("  \"thermalCores\": [")
        appendLine(s.thermalCores.joinToString(",\n") {
            "    { \"id\": ${jstr(it.id)}, \"frequencyMhz\": ${it.frequencyMhz}, \"tempC\": ${it.tempC}, \"status\": ${jstr(it.status)} }"
        })
        appendLine("  ],")
        appendLine("  \"processes\": [")
        appendLine(s.processes.joinToString(",\n") {
            "    { \"name\": ${jstr(it.name)}, \"pid\": ${it.pid}, \"cpuPercent\": ${it.cpuPercent}, " +
                "\"mem\": ${jstr(it.memText)}, \"state\": ${jstr(it.state)}, \"foreground\": ${it.isForeground} }"
        })
        appendLine("  ],")
        appendLine("  \"history\": {")
        appendLine("    \"memoryUsedGb\": ${numArray(s.memoryTimelineGb)},")
        appendLine("    \"nativeHeapGb\": ${numArray(s.nativeHeapTimelineGb)},")
        appendLine("    \"batteryPercent\": ${numArray(s.batteryPercentHistory)}")
        appendLine("  }")
        append("}")
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    private fun jstr(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun numArray(values: List<Float>): String =
        values.joinToString(prefix = "[", postfix = "]")
}
