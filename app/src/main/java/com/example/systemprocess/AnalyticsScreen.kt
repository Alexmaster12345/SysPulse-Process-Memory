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
internal fun AnalyticsScreen(
    state: TelemetryUiState,
    onViewAllProcesses: () -> Unit,
    onBack: () -> Unit,
    onSaveSession: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            ScreenTopBar(
                title = "Memory Map",
                subtitle = "Real-time Allocation",
                onBack = onBack,
                trailingIcon = "🖌"
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item { MemoryDistributionCard(state) }
        item { AllocationHistoryCard(state) }
        item { TopMemoryConsumersCard(state, onViewAll = onViewAllProcesses) }
        item { ExportSessionCard(state, onSaveSession = onSaveSession) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun ExportSessionCard(state: TelemetryUiState, onSaveSession: () -> Unit = {}) {
    val context = LocalContext.current
    fun runExport(format: SessionExporter.Format) {
        try {
            SessionExporter.exportAndShare(context, state, format)
        } catch (t: Throwable) {
            android.widget.Toast.makeText(
                context,
                "Export failed: ${t.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    CardShell("Export Session", right = "raw metrics") {
        Spacer(Modifier.height(4.dp))
        Text(
            "Save the current CPU, memory, thermal, battery and process snapshot (with recorded history) for profiling.",
            color = AppColors.SubtleText,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ExportButton(
                label = "Export CSV",
                icon = "📄",
                color = AppColors.AccentGreen,
                modifier = Modifier.weight(1f),
                onClick = { runExport(SessionExporter.Format.CSV) }
            )
            ExportButton(
                label = "Export JSON",
                icon = "{ }",
                color = AppColors.AccentCyan,
                modifier = Modifier.weight(1f),
                onClick = { runExport(SessionExporter.Format.JSON) }
            )
        }
        Spacer(Modifier.height(12.dp))
        ExportButton(
            label = "Save to History",
            icon = "💾",
            color = AppColors.AccentOrange,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSaveSession()
                android.widget.Toast.makeText(
                    context,
                    "Session saved to history",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
internal fun ExportButton(
    label: String,
    icon: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = color, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
internal fun MemoryDistributionCard(state: TelemetryUiState) {
    CardShell("Memory Distribution", right = null, centerTitle = true) {
        Spacer(Modifier.height(16.dp))
        DonutChart(
            values = state.memoryBreakdown.map { it.valueGb.coerceAtLeast(0f) },
            colors = state.memoryBreakdown.map { segmentColor(it) },
            centerLabel = String.format("%.1f\nGB USED", state.memoryBreakdown.sumOf { it.valueGb.toDouble() }),
            modifier = Modifier.fillMaxWidth().height(200.dp)
        )
        Spacer(Modifier.height(16.dp))
        state.memoryBreakdown.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { segment ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(segmentColor(segment))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(segment.label.uppercase(), color = AppColors.SubtleText, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            String.format("%.2f GB", segment.valueGb),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
internal fun AllocationHistoryCard(state: TelemetryUiState) {
    CardShell("Allocation History", right = "LAST 5 MIN") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(color = AppColors.AccentCyan, label = "Total used")
            LegendDot(color = AppColors.AccentGreen, label = "Native heap")
        }
        Spacer(Modifier.height(12.dp))
        DualLineChart(
            primary = state.memoryTimelineGb,
            secondary = state.nativeHeapTimelineGb,
            primaryColor = AppColors.AccentCyan,
            secondaryColor = AppColors.AccentGreen,
            modifier = Modifier.fillMaxWidth().height(150.dp)
        )
    }
}

@Composable
internal fun TopMemoryConsumersCard(state: TelemetryUiState, onViewAll: () -> Unit) {
    CardShell(
        title = "Top Memory Consumers",
        rightContent = {
            Text(
                "View All",
                color = AppColors.AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onViewAll() }
            )
        }
    ) {
        val consumers = state.processes.sortedByDescending { parseMemToKb(it.memText) }.take(5)
        if (consumers.isEmpty()) {
            Text(
                "No per-app memory data available on this Android version.",
                color = AppColors.SubtleText,
                fontSize = 12.sp
            )
        }
        val maxKb = (consumers.maxOfOrNull { parseMemToKb(it.memText) } ?: 1L).coerceAtLeast(1L)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            consumers.forEachIndexed { index, process ->
                val color = consumerColor(index)
                val fraction = (parseMemToKb(process.memText).toFloat() / maxKb.toFloat()).coerceIn(0.02f, 1f)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(shortAppName(process.name), color = Color.White, fontSize = 15.sp)
                        }
                        Text(process.memText, color = AppColors.SubtleText, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.CardBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                        )
                    }
                }
            }
        }
    }
}

internal fun consumerColor(index: Int): Color = when (index % 4) {
    0 -> AppColors.AccentCyan
    1 -> AppColors.AccentGreen
    2 -> AppColors.AccentOrange
    else -> AppColors.AccentRed
}

internal fun parseMemToKb(memText: String): Long = when {
    memText.endsWith("GB") -> (memText.removeSuffix("GB").toFloatOrNull() ?: 0f).times(1_048_576).toLong()
    memText.endsWith("MB") -> (memText.removeSuffix("MB").toFloatOrNull() ?: 0f).times(1024).toLong()
    else -> 0L
}

internal fun shortAppName(processName: String): String =
    processName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

internal fun segmentColor(segment: MemorySegment): Color = when (segment.colorIndex) {
    0 -> AppColors.AccentCyan
    1 -> AppColors.AccentGreen
    2 -> AppColors.AccentOrange
    else -> AppColors.AccentRed
}
