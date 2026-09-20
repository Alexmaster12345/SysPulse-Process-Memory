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
internal fun SectionLabel(text: String) {
    Text(text, color = AppColors.SubtleText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun IconTile(icon: String, background: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 14.sp)
    }
}

@Composable
internal fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppColors.CardBorder)
    )
}

@Composable
internal fun ScreenTopBar(title: String, subtitle: String, onBack: () -> Unit, trailingIcon: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(AppColors.Card)
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Text("←", color = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(subtitle, color = AppColors.SubtleText, fontSize = 12.sp)
        }
        if (trailingIcon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppColors.Card),
                contentAlignment = Alignment.Center
            ) {
                Text(trailingIcon, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
internal fun MetricTile(title: String, value: String, modifier: Modifier, valueColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(title, color = AppColors.SubtleText, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = AppColors.SubtleText, fontSize = 11.sp)
    }
}

@Composable
internal fun CardShell(
    title: String,
    right: String? = null,
    rightContent: (@Composable () -> Unit)? = null,
    centerTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        if (centerTitle && rightContent == null && right == null) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                when {
                    rightContent != null -> rightContent()
                    right != null -> Text(right, color = AppColors.SubtleText, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
internal fun MiniLineChart(values: List<Float>, lineColor: Color, fillColor: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Collecting data…", color = AppColors.SubtleText, fontSize = 12.sp)
        }
        return
    }
    Canvas(modifier = modifier) {
        val minValue = values.minOrNull() ?: 0f
        val maxValue = values.maxOrNull() ?: 1f
        val range = (maxValue - minValue).coerceAtLeast(0.001f)
        val widthStep = size.width / (values.size - 1).coerceAtLeast(1)

        val points = values.mapIndexed { i, value ->
            val x = i * widthStep
            val normalized = (value - minValue) / range
            val y = size.height - normalized * (size.height * 0.9f)
            Offset(x, y)
        }

        for (i in 1 until points.size) {
            drawLine(
                color = lineColor,
                start = points[i - 1],
                end = points[i],
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }

        val canvasHeight = size.height
        val fillPoints = buildList {
            add(Offset(points.first().x, canvasHeight))
            addAll(points)
            add(Offset(points.last().x, canvasHeight))
        }
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(fillPoints.first().x, fillPoints.first().y)
                fillPoints.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            },
            brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent))
        )
    }
}

@Composable
internal fun DualLineChart(
    primary: List<Float>,
    secondary: List<Float>,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    if (primary.size < 2 || secondary.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Collecting data…", color = AppColors.SubtleText, fontSize = 12.sp)
        }
        return
    }
    Canvas(modifier = modifier) {
        fun toPoints(values: List<Float>): List<Offset> {
            val minValue = values.minOrNull() ?: 0f
            val maxValue = values.maxOrNull() ?: 1f
            val range = (maxValue - minValue).coerceAtLeast(0.001f)
            val widthStep = size.width / (values.size - 1).coerceAtLeast(1)
            return values.mapIndexed { i, value ->
                val x = i * widthStep
                val normalized = (value - minValue) / range
                val y = size.height - normalized * (size.height * 0.85f) - (size.height * 0.05f)
                Offset(x, y)
            }
        }

        fun drawTrace(points: List<Offset>, color: Color) {
            for (i in 1 until points.size) {
                drawLine(
                    color = color,
                    start = points[i - 1],
                    end = points[i],
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }

        drawTrace(toPoints(secondary), secondaryColor)
        drawTrace(toPoints(primary), primaryColor)
    }
}

@Composable
internal fun DonutChart(values: List<Float>, colors: List<Color>, centerLabel: String, modifier: Modifier = Modifier) {
    val total = values.sum().coerceAtLeast(0.001f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val diameter = min(size.width, size.height)
            val stroke = diameter * 0.2f
            var startAngle = -90f
            values.forEachIndexed { i, value ->
                val sweep = value / total * 360f
                drawArc(
                    color = colors.getOrElse(i) { Color.Gray },
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f),
                    size = Size(diameter, diameter),
                    style = Stroke(width = stroke)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            centerLabel.split("\n").forEach { line ->
                Text(
                    line,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (line == centerLabel.split("\n").first()) 20.sp else 12.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
