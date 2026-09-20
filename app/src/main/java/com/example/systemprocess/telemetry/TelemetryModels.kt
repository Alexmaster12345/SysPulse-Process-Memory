package com.example.systemprocess.telemetry

import androidx.compose.runtime.Stable

@Stable
data class TelemetryUiState(
    val deviceModel: String = android.os.Build.MODEL,
    val androidVersion: String = android.os.Build.VERSION.RELEASE,
    val cpuLoadPercent: Int = 0,
    val totalMemoryGb: Float = 0f,
    val availableMemoryGb: Float = 0f,
    val deviceTempC: Int = 0,
    val thermalState: String = "Unknown",
    val thermalCores: List<ThermalCore> = emptyList(),
    val memoryTimelineGb: List<Float> = emptyList(),
    val nativeHeapTimelineGb: List<Float> = emptyList(),
    val batteryPercentHistory: List<Float> = emptyList(),
    val batteryLevelPercent: Int = 0,
    val batteryCurrentMicroAmps: Long = 0L,
    val batteryTempC: Float = 0f,
    val memoryBreakdown: List<MemorySegment> = emptyList(),
    val processes: List<ProcessEntry> = emptyList(),
    val sessionElapsedSeconds: Int = 0
)

@Stable
data class ThermalCore(
    val id: String,
    val frequencyMhz: Int,
    val tempC: Int,
    val status: String
)

@Stable
data class MemorySegment(val label: String, val valueGb: Float, val colorIndex: Int)

@Stable
data class ProcessEntry(
    val name: String,
    val pid: Int,
    val cpuPercent: Float,
    val memText: String,
    val state: String,
    val isForeground: Boolean
)
