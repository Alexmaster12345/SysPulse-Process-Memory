package com.example.systemprocess

import android.os.Bundle
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.systemprocess.telemetry.MemorySegment
import com.example.systemprocess.telemetry.ProcessEntry
import com.example.systemprocess.telemetry.TelemetryRepository
import com.example.systemprocess.telemetry.SessionExporter
import com.example.systemprocess.telemetry.TelemetryUiState
import com.example.systemprocess.telemetry.ThermalCore
import com.example.systemprocess.ui.theme.SystemProcessTheme
import kotlin.math.min

@Composable
internal fun HomeScreen(
    state: TelemetryUiState,
    onOpenThreads: () -> Unit,
    onOpenAnalytics: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { DashboardHeader(state) }
        item { TopMetricRow(state) }
        item { CpuThermalCard(state) }
        item { MemoryTimelineCard(state, onOpenMap = onOpenAnalytics) }
        item { BatteryCurveCard(state) }
        item { ActiveProcessCard(state, onOpenFullManager = onOpenThreads) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun DashboardHeader(state: TelemetryUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Device Telemetry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text(
                "${state.deviceModel.uppercase()} · V${state.androidVersion}",
                color = AppColors.SubtleText,
                fontSize = 12.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SystemStatusBadge(state)
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppColors.Card),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", color = Color.White)
            }
        }
    }
}

internal enum class MonitorStatus(val label: String, val icon: String, val color: Color) {
    Ok("OK", "●", AppColors.AccentGreen),
    Warning("WARNING", "▲", AppColors.AccentOrange),
    Error("ERROR", "✕", AppColors.AccentRed)
}

internal fun computeMonitorStatus(state: TelemetryUiState): MonitorStatus {
    val usedMemPercent = if (state.totalMemoryGb > 0f)
        ((state.totalMemoryGb - state.availableMemoryGb) / state.totalMemoryGb * 100f) else 0f
    val thermal = state.thermalState.lowercase()

    val errorConditions = listOf(
        state.cpuLoadPercent >= 90,
        usedMemPercent >= 92f,
        state.deviceTempC >= 55,
        thermal.contains("severe") || thermal.contains("critical") || thermal.contains("emergency") || thermal.contains("shutdown"),
        state.batteryLevelPercent in 1..5,
        state.batteryTempC >= 48f
    )
    val warningConditions = listOf(
        state.cpuLoadPercent >= 75,
        usedMemPercent >= 80f,
        state.deviceTempC >= 45,
        thermal.contains("light") || thermal.contains("moderate"),
        state.batteryLevelPercent in 6..15,
        state.batteryTempC >= 40f
    )
    return when {
        errorConditions.any { it } -> MonitorStatus.Error
        warningConditions.any { it } -> MonitorStatus.Warning
        else -> MonitorStatus.Ok
    }
}

@Composable
internal fun SystemStatusBadge(state: TelemetryUiState) {
    val status = computeMonitorStatus(state)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(status.color.copy(alpha = 0.15f))
            .border(1.dp, status.color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(status.icon, color = status.color, fontSize = 10.sp)
        Spacer(Modifier.width(6.dp))
        Text(status.label, color = status.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun TopMetricRow(state: TelemetryUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile("CPU LOAD", "${state.cpuLoadPercent} %", Modifier.weight(1f), AppColors.AccentCyan)
        MetricTile("RAM", String.format("%.1f GB", state.totalMemoryGb), Modifier.weight(1f), Color.White)
        MetricTile("THERMAL", "${state.deviceTempC}° C", Modifier.weight(1f), AppColors.AccentOrange)
    }
}

@Composable
internal fun CpuThermalCard(state: TelemetryUiState) {
    CardShell("CPU Thermal States", right = "● ${state.thermalState}") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.thermalCores.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { core ->
                        val color = coreStatusColor(core.status)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color.copy(alpha = 0.14f))
                                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(core.id, color = AppColors.SubtleText, fontSize = 11.sp)
                            Text(
                                if (core.tempC > 0) "${core.tempC}° C" else "N/A",
                                color = color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(core.status, color = color, fontSize = 11.sp)
                            if (core.frequencyMhz > 0) {
                                Text("${core.frequencyMhz} MHz", color = AppColors.SubtleText, fontSize = 10.sp)
                            }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                val segments = listOf(
                    AppColors.AccentCyan,
                    AppColors.AccentGreen,
                    AppColors.AccentOrange,
                    AppColors.AccentRed
                )
                segments.forEach { color ->
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(color))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cool", color = AppColors.SubtleText, fontSize = 11.sp)
                Text("Warm", color = AppColors.SubtleText, fontSize = 11.sp)
                Text("Hot", color = AppColors.SubtleText, fontSize = 11.sp)
                Text("Critical", color = AppColors.SubtleText, fontSize = 11.sp)
            }
        }
    }
}

internal fun coreStatusColor(status: String): Color = when (status) {
    "COOL" -> AppColors.AccentCyan
    "WARM" -> AppColors.AccentGreen
    "HOT" -> AppColors.AccentOrange
    "CRITICAL" -> AppColors.AccentRed
    else -> AppColors.SubtleText
}

@Composable
internal fun MemoryTimelineCard(state: TelemetryUiState, onOpenMap: () -> Unit) {
    CardShell("Memory Allocation", right = "live · ${state.sessionElapsedSeconds}s") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Used / Total RAM", color = AppColors.SubtleText, fontSize = 12.sp)
            AllocationMapToggle(onOpenMap = onOpenMap)
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(18.dp).height(3.dp).background(AppColors.AccentCyan))
            Spacer(Modifier.width(6.dp))
            Text("trace 0", color = AppColors.SubtleText, fontSize = 11.sp)
        }
        MiniLineChart(
            values = state.memoryTimelineGb,
            lineColor = AppColors.AccentCyan,
            fillColor = AppColors.AccentCyan.copy(alpha = 0.16f),
            modifier = Modifier.fillMaxWidth().height(130.dp)
        )
    }
}

@Composable
internal fun AllocationMapToggle(onOpenMap: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Background)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.AccentCyan)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Allocation", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { onOpenMap() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Map", color = AppColors.SubtleText, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun BatteryCurveCard(state: TelemetryUiState) {
    CardShell("Battery Discharge Curve", right = "${state.batteryLevelPercent}% now") {
        Text("Capacity remaining", color = AppColors.SubtleText, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        MiniLineChart(
            values = state.batteryPercentHistory,
            lineColor = AppColors.AccentGreen,
            fillColor = AppColors.AccentGreen.copy(alpha = 0.17f),
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${String.format("%.1f", state.batteryCurrentMicroAmps / 1000f)} mA",
                color = AppColors.SubtleText,
                fontSize = 11.sp
            )
            Text("${state.batteryTempC}° C battery", color = AppColors.SubtleText, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun ActiveProcessCard(state: TelemetryUiState, onOpenFullManager: () -> Unit) {
    CardShell("Running Threads", right = "${state.processes.size} active") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.clickable { onOpenFullManager() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FULL MANAGER", color = AppColors.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Text("›", color = AppColors.AccentCyan, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.processes.isEmpty()) {
                Text(
                    "No process data available on this Android version.",
                    color = AppColors.SubtleText,
                    fontSize = 12.sp
                )
            }
            state.processes.take(6).forEach { p ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(processStateColor(p))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, color = Color.White, fontSize = 15.sp)
                        Text("PID ${p.pid}", color = AppColors.SubtleText, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${p.cpuPercent.toInt()} %", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(p.memText, color = AppColors.SubtleText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

internal fun processStateColor(p: ProcessEntry): Color =
    if (p.isForeground) AppColors.AccentGreen else AppColors.SubtleText
