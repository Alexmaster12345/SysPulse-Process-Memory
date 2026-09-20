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
internal fun ThreadsScreen(state: TelemetryUiState, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { ScreenTopBar(title = "Processes", subtitle = "${state.processes.size} Total Threads", onBack = onBack) }
        item { ProcessListCard(state) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun ProcessListCard(state: TelemetryUiState) {
    CardShell("Processes", right = "${state.processes.size} visible") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("All", "User", "System", "Services").forEachIndexed { i, tab ->
                val selected = i == 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AppColors.AccentCyan.copy(alpha = 0.2f) else AppColors.Card)
                        .border(
                            1.dp,
                            if (selected) AppColors.AccentCyan else AppColors.CardBorder,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(tab, color = if (selected) AppColors.AccentCyan else AppColors.SubtleText, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.processes.isEmpty()) {
            Text(
                "Android 8.0+ restricts unrooted apps from enumerating other apps' processes.",
                color = AppColors.SubtleText,
                fontSize = 12.sp
            )
        }
        state.processes.forEach { p ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, color = Color.White)
                    Text("PID ${p.pid}  ${p.state}", color = AppColors.SubtleText, fontSize = 11.sp)
                }
                Text(
                    "${p.cpuPercent.toInt()} %",
                    color = processStateColor(p),
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.End
                )
                Text(p.memText, color = Color.White, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
            }
        }
    }
}
