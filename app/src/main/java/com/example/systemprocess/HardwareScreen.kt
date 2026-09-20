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

internal data class HwCheck(val label: String, val icon: String, val supported: Boolean)

@Composable
internal fun HardwareDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val checks = remember {
        val pm = context.packageManager
        fun feat(f: String) = pm.hasSystemFeature(f)
        listOf(
            HwCheck("Camera", "📷", feat(PackageManager.FEATURE_CAMERA_ANY)),
            HwCheck("Fingerprint Sensor", "🫆", feat(PackageManager.FEATURE_FINGERPRINT)),
            HwCheck("NFC", "📡", feat(PackageManager.FEATURE_NFC)),
            HwCheck("Bluetooth LE", "🔵", feat(PackageManager.FEATURE_BLUETOOTH_LE)),
            HwCheck("Wi-Fi", "📶", feat(PackageManager.FEATURE_WIFI)),
            HwCheck("GPS", "🛰", feat(PackageManager.FEATURE_LOCATION_GPS)),
            HwCheck("Cellular / Telephony", "☎", feat(PackageManager.FEATURE_TELEPHONY)),
            HwCheck("Accelerometer", "📐", feat(PackageManager.FEATURE_SENSOR_ACCELEROMETER)),
            HwCheck("Gyroscope", "🌀", feat(PackageManager.FEATURE_SENSOR_GYROSCOPE)),
            HwCheck("Compass", "🧭", feat(PackageManager.FEATURE_SENSOR_COMPASS)),
            HwCheck("Barometer", "🌡", feat(PackageManager.FEATURE_SENSOR_BAROMETER)),
            HwCheck("Proximity Sensor", "📲", feat(PackageManager.FEATURE_SENSOR_PROXIMITY)),
            HwCheck("Heart Rate Sensor", "❤", feat(PackageManager.FEATURE_SENSOR_HEART_RATE)),
            HwCheck("USB Host", "🔌", feat(PackageManager.FEATURE_USB_HOST)),
            HwCheck("Vulkan Graphics", "🎮", feat(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL))
        )
    }

    val sensors = remember {
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        sm?.getSensorList(Sensor.TYPE_ALL)?.map { "${it.name} · ${it.vendor}" } ?: emptyList()
    }

    val supportedCount = checks.count { it.supported }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { ScreenTopBar("Hardware Diagnostics", "Device capability report", onBack) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Card)
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}", color = AppColors.SubtleText, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$supportedCount/${checks.size}", color = AppColors.AccentGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("SUPPORTED", color = AppColors.SubtleText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Column {
                SectionLabel("HARDWARE FEATURES")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    checks.forEachIndexed { index, check ->
                        HwCheckRow(check)
                        if (index != checks.lastIndex) RowDivider()
                    }
                }
            }
        }
        item {
            Column {
                SectionLabel("SENSORS DETECTED (${sensors.size})")
                Spacer(Modifier.height(10.dp))
                if (sensors.isEmpty()) {
                    Text("No sensors reported by this device.", color = AppColors.SubtleText, fontSize = 12.sp)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.Card)
                            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        sensors.forEach { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("•", color = AppColors.AccentCyan, fontSize = 14.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(s, color = AppColors.SubtleText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun HwCheckRow(check: HwCheck) {
    val statusColor = if (check.supported) AppColors.AccentGreen else AppColors.AccentRed
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(check.icon, fontSize = 15.sp)
        Spacer(Modifier.width(12.dp))
        Text(check.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (check.supported) "✓" else "✕", color = statusColor, fontSize = 11.sp)
            Spacer(Modifier.width(5.dp))
            Text(if (check.supported) "SUPPORTED" else "N/A", color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
