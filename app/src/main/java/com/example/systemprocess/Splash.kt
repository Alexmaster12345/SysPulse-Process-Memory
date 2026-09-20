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
internal fun SplashScreen(onFinished: () -> Unit) {
    val chipScale = remember { Animatable(0.4f) }
    val chipAlpha = remember { Animatable(0f) }
    val waveProgress = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(Unit) {
        chipAlpha.animateTo(1f, tween(400))
        chipScale.animateTo(1f, tween(600, easing = EaseOutBack))
        waveProgress.animateTo(1f, tween(900, easing = LinearEasing))
        textAlpha.animateTo(1f, tween(500))
        kotlinx.coroutines.delay(700)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .size(180.dp)
                    .alpha(chipAlpha.value)
                    .scale(chipScale.value * pulseScale)
            ) {
                val w = size.width
                fun p(v: Float) = v / 108f * w
                val cyan = AppColors.AccentCyan
                val green = AppColors.AccentGreen

                // diamond backdrop
                val diamond = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(54f), p(20f)); lineTo(p(88f), p(54f))
                    lineTo(p(54f), p(88f)); lineTo(p(20f), p(54f)); close()
                }
                drawPath(diamond, Color(0xFF12181F))

                // pins
                fun pin(x: Float, y: Float, pw: Float, ph: Float) =
                    drawRect(cyan, topLeft = Offset(p(x), p(y)), size = Size(p(pw), p(ph)))
                for (x in listOf(50f, 57f, 64f)) { pin(x, 33f, 2.4f, 6f); pin(x, 69f, 2.4f, 6f) }
                for (y in listOf(50f, 57f, 64f)) { pin(33f, y, 6f, 2.4f); pin(69f, y, 6f, 2.4f) }

                // chip body outline
                drawRoundRect(
                    color = cyan,
                    topLeft = Offset(p(39f), p(39f)),
                    size = Size(p(30f), p(30f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(p(4f), p(4f)),
                    style = Stroke(width = p(4f))
                )
                // inner core
                drawRect(cyan, topLeft = Offset(p(48f), p(48f)), size = Size(p(12f), p(12f)))

                // animated waveform
                val pts = listOf(
                    Offset(p(6f), p(56f)), Offset(p(13f), p(56f)), Offset(p(17f), p(44f)),
                    Offset(p(21f), p(64f)), Offset(p(25f), p(56f)), Offset(p(33f), p(56f))
                )
                val totalSegs = pts.size - 1
                val drawn = waveProgress.value * totalSegs
                for (i in 0 until totalSegs) {
                    if (i >= drawn) break
                    val frac = (drawn - i).coerceIn(0f, 1f)
                    val start = pts[i]
                    val end = pts[i + 1]
                    val cur = Offset(start.x + (end.x - start.x) * frac, start.y + (end.y - start.y) * frac)
                    drawLine(green, start, cur, strokeWidth = p(2.6f), cap = StrokeCap.Round)
                }
            }
            Spacer(Modifier.height(28.dp))
            Row(modifier = Modifier.alpha(textAlpha.value)) {
                Text("SYS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Text("V", color = AppColors.AccentCyan, fontWeight = FontWeight.Bold, fontSize = 32.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SYSTEM PROCESS MONITOR",
                color = AppColors.SubtleText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
