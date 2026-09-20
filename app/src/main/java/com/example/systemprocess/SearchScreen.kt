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

internal data class QuickFilter(val label: String, val color: Color)

internal data class DiagnosticTool(val icon: String, val iconBg: Color, val title: String, val subtitle: String)

@Composable
internal fun SearchScreen(state: TelemetryUiState) {
    var showHardwareDiagnostics by rememberSaveable { mutableStateOf(false) }
    if (showHardwareDiagnostics) {
        HardwareDiagnosticsScreen(onBack = { showHardwareDiagnostics = false })
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var recentActivity by remember {
        mutableStateOf(
            listOf(
                "Memory leaks in com.spotify",
                "Thermal throttling events"
            )
        )
    }
    val quickFilters = remember {
        listOf(
            QuickFilter("High CPU", AppColors.AccentRed),
            QuickFilter("Heap Dumps", AppColors.AccentCyan),
            QuickFilter("Overheating", AppColors.AccentOrange),
            QuickFilter("Battery Drain", AppColors.AccentGreen),
            QuickFilter("System Threads", AppColors.SubtleText)
        )
    }
    var selectedFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val matchingProcesses = remember(query, state.processes) {
        if (query.isBlank()) emptyList()
        else state.processes.filter { it.name.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Card),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("‹", color = Color.White, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Search", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
            }
            item {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it }
                )
            }

            if (query.isNotBlank()) {
                item {
                    Text(
                        "${matchingProcesses.size} result(s) for \"$query\"",
                        color = AppColors.SubtleText,
                        fontSize = 12.sp
                    )
                }
                items(matchingProcesses) { process ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.Card)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(process.name, color = Color.White, fontSize = 15.sp)
                            Text("PID ${process.pid}  ${process.state}", color = AppColors.SubtleText, fontSize = 11.sp)
                        }
                        Text("${process.cpuPercent.toInt()} %", color = AppColors.AccentCyan, fontSize = 13.sp)
                    }
                }
                if (matchingProcesses.isEmpty()) {
                    item {
                        Text(
                            "No matching processes found.",
                            color = AppColors.SubtleText,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel("RECENT ACTIVITY")
                            Text(
                                "Clear",
                                color = AppColors.AccentCyan,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { recentActivity = emptyList() }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            recentActivity.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AppColors.Card)
                                        .clickable { query = entry }
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("↺", color = AppColors.SubtleText, fontSize = 14.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(entry, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Text("⤴", color = AppColors.SubtleText, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                item {
                    Column {
                        SectionLabel("QUICK FILTERS")
                        Spacer(Modifier.height(10.dp))
                        FlowChipsRow(
                            filters = quickFilters,
                            selected = selectedFilter,
                            onSelect = { selectedFilter = if (selectedFilter == it) null else it }
                        )
                    }
                }
                item {
                    Column {
                        SectionLabel("DIAGNOSTIC TOOLS")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            DiagnosticToolCard(
                                tool = DiagnosticTool("🐛", Color(0xFF123A4A), "Logcat", "Debug real-time system events"),
                                modifier = Modifier.weight(1f)
                            )
                            DiagnosticToolCard(
                                tool = DiagnosticTool("⚡", Color(0xFF1E3A17), "Perf Monitor", "Frame rate & jitter analysis"),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        DiagnosticToolCard(
                            tool = DiagnosticTool("🔧", Color(0xFF2A1E3A), "Hardware Test", "Check sensors & device capabilities"),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showHardwareDiagnostics = true }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
internal fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔍", fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search processes, logs, or metrics", color = AppColors.SubtleText, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = Brush.verticalGradient(listOf(AppColors.AccentCyan, AppColors.AccentCyan)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("🎙", fontSize = 14.sp, color = AppColors.SubtleText)
    }
}

@Composable
internal fun FlowChipsRow(filters: List<QuickFilter>, selected: String?, onSelect: (String) -> Unit) {
    filters.chunked(3).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { filter ->
                val isSelected = filter.label == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(filter.color.copy(alpha = if (isSelected) 0.35f else 0.16f))
                        .border(1.dp, filter.color.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .clickable { onSelect(filter.label) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(filter.label, color = filter.color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun DiagnosticToolCard(tool: DiagnosticTool, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tool.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(tool.icon, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(tool.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(tool.subtitle, color = AppColors.SubtleText, fontSize = 11.sp)
    }
}
