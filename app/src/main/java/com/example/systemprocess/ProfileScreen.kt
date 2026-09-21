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
import com.example.systemprocess.data.SavedSession
import com.example.systemprocess.ui.theme.SystemProcessTheme
import kotlin.math.min

internal enum class ProfileSubScreen { None, Security, CloudSync, Display, History }

internal data class TextSizeOption(val label: String, val scale: Float)

internal val textSizeOptions = listOf(
    TextSizeOption("Small", 0.85f),
    TextSizeOption("Default", 1.0f),
    TextSizeOption("Large", 1.15f),
    TextSizeOption("Extra Large", 1.3f)
)

internal fun textScaleLabel(scale: Float): String =
    textSizeOptions.minByOrNull { kotlin.math.abs(it.scale - scale) }?.label ?: "Default"

@Composable
internal fun DisplayTextScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { ScreenTopBar("Display & Text Size", "Adjust text size app-wide", onBack) }
        item {
            Column {
                SectionLabel("TEXT SIZE")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    textSizeOptions.forEachIndexed { index, option ->
                        val selected = kotlin.math.abs(AppSettings.textScale - option.scale) < 0.001f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { AppSettings.textScale = option.scale }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                option.label,
                                color = Color.White,
                                fontSize = (15 * option.scale).sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Text("✓", color = AppColors.AccentCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("${(option.scale * 100).toInt()}%", color = AppColors.SubtleText, fontSize = 12.sp)
                            }
                        }
                        if (index != textSizeOptions.lastIndex) RowDivider()
                    }
                }
            }
        }
        item {
            Column {
                SectionLabel("PREVIEW")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("Device Telemetry", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("CPU load, memory, thermal and battery metrics update in real time.", color = AppColors.SubtleText, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("42%  ·  3.1 GB  ·  38°C", color = AppColors.AccentCyan, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
        item {
            Text(
                "Text size scales all labels throughout the app. Layout spacing stays fixed.",
                color = AppColors.SubtleText,
                fontSize = 12.sp
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun ProfileScreen(
    savedSessions: List<SavedSession> = emptyList(),
    onDeleteSession: (SavedSession) -> Unit = {},
    onClearSessions: () -> Unit = {}
) {
    var subScreen by rememberSaveable { mutableStateOf(ProfileSubScreen.None) }

    when (subScreen) {
        ProfileSubScreen.Security -> {
            SecurityPrivacyScreen(onBack = { subScreen = ProfileSubScreen.None })
            return
        }
        ProfileSubScreen.CloudSync -> {
            CloudSyncScreen(onBack = { subScreen = ProfileSubScreen.None })
            return
        }
        ProfileSubScreen.Display -> {
            DisplayTextScreen(onBack = { subScreen = ProfileSubScreen.None })
            return
        }
        ProfileSubScreen.History -> {
            SessionHistoryScreen(
                sessions = savedSessions,
                onDelete = onDeleteSession,
                onClear = onClearSessions,
                onBack = { subScreen = ProfileSubScreen.None }
            )
            return
        }
        ProfileSubScreen.None -> Unit
    }

    var criticalPushAlerts by rememberSaveable { mutableStateOf(true) }
    var thermalThrottlingLog by rememberSaveable { mutableStateOf(true) }
    var autoDeleteOldLogs by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        item { ProfileHeader() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("REPORTS GENERATED", "142", Color.White, Modifier.weight(1f))
                StatTile("ALERTS HANDLED", "890", AppColors.AccentGreen, Modifier.weight(1f))
            }
        }
        item {
            Column {
                SectionLabel("MONITORING PREFERENCES")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    PreferenceToggleRow(
                        icon = "🔔",
                        iconBg = Color(0xFF123A3A),
                        label = "Critical Push Alerts",
                        checked = criticalPushAlerts,
                        onCheckedChange = { criticalPushAlerts = it }
                    )
                    RowDivider()
                    PreferenceToggleRow(
                        icon = "🔥",
                        iconBg = Color(0xFF3A2A12),
                        label = "Thermal Throttling Log",
                        checked = thermalThrottlingLog,
                        onCheckedChange = { thermalThrottlingLog = it }
                    )
                    RowDivider()
                    PreferenceToggleRow(
                        icon = "🗄",
                        iconBg = Color(0xFF2A2D36),
                        label = "Auto-delete Old Logs",
                        checked = autoDeleteOldLogs,
                        onCheckedChange = { autoDeleteOldLogs = it }
                    )
                }
            }
        }
        item {
            Column {
                SectionLabel("SYSTEM")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    SystemRow(
                        icon = "🛡",
                        iconBg = Color(0xFF2A2D36),
                        label = "Security & Privacy",
                        onClick = { subScreen = ProfileSubScreen.Security },
                        trailing = { Text("›", color = AppColors.SubtleText, fontSize = 18.sp) }
                    )
                    RowDivider()
                    SystemRow(
                        icon = "💾",
                        iconBg = Color(0xFF2A2D36),
                        label = "Saved Sessions",
                        onClick = { subScreen = ProfileSubScreen.History },
                        trailing = {
                            Text(
                                "${savedSessions.size}",
                                color = AppColors.AccentOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                    RowDivider()
                    SystemRow(
                        icon = "🔠",
                        iconBg = Color(0xFF2A2D36),
                        label = "Display & Text Size",
                        onClick = { subScreen = ProfileSubScreen.Display },
                        trailing = {
                            Text(
                                textScaleLabel(AppSettings.textScale),
                                color = AppColors.AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                    RowDivider()
                    SystemRow(
                        icon = "☁",
                        iconBg = Color(0xFF2A2D36),
                        label = "Cloud Sync Status",
                        onClick = { subScreen = ProfileSubScreen.CloudSync },
                        trailing = {
                            Text(
                                "SYNCED",
                                color = AppColors.AccentGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A1418))
                    .border(1.dp, AppColors.AccentRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Logout Engineer Session", color = AppColors.AccentRed, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun SessionHistoryScreen(
    sessions: List<SavedSession>,
    onDelete: (SavedSession) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val dateFormat = remember {
        java.text.SimpleDateFormat("MMM d, HH:mm:ss", java.util.Locale.US)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            ScreenTopBar(
                title = "Saved Sessions",
                subtitle = "${sessions.size} persisted snapshot(s)",
                onBack = onBack,
                trailingIcon = if (sessions.isNotEmpty()) "🗑" else null,
                onTrailingClick = onClear
            )
        }
        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No saved sessions yet.\nUse \"Save to History\" on the Memory Map screen.",
                        color = AppColors.SubtleText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SavedSessionRow(session, dateFormat.format(java.util.Date(session.savedAt)), onDelete)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SavedSessionRow(session: SavedSession, timeLabel: String, onDelete: (SavedSession) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(timeLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${session.deviceModel} · ${session.durationSeconds}s session",
                    color = AppColors.SubtleText,
                    fontSize = 11.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.AccentRed.copy(alpha = 0.15f))
                    .clickable { onDelete(session) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Delete", color = AppColors.AccentRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SessionMetric("CPU", "${session.cpuLoadPercent}%", AppColors.AccentCyan)
            SessionMetric("RAM", String.format("%.1fGB", session.usedMemoryGb), AppColors.AccentGreen)
            SessionMetric("TEMP", "${session.deviceTempC}°C", AppColors.AccentOrange)
            SessionMetric("BATT", "${session.batteryLevelPercent}%", Color.White)
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, color = AppColors.SubtleText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SecurityPrivacyScreen(onBack: () -> Unit) {
    var appLock by rememberSaveable { mutableStateOf(true) }
    var biometricUnlock by rememberSaveable { mutableStateOf(false) }
    var anonymizeLogs by rememberSaveable { mutableStateOf(true) }
    var crashReporting by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { ScreenTopBar("Security & Privacy", "Access control & data handling", onBack) }
        item {
            Column {
                SectionLabel("ACCESS CONTROL")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    PreferenceToggleRow("🔒", Color(0xFF2A2D36), "App Lock", appLock) { appLock = it }
                    RowDivider()
                    PreferenceToggleRow("🫆", Color(0xFF2A2D36), "Biometric Unlock", biometricUnlock) { biometricUnlock = it }
                }
            }
        }
        item {
            Column {
                SectionLabel("DATA PRIVACY")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    PreferenceToggleRow("🕶", Color(0xFF2A2D36), "Anonymize Logs", anonymizeLogs) { anonymizeLogs = it }
                    RowDivider()
                    PreferenceToggleRow("📡", Color(0xFF2A2D36), "Crash Reporting", crashReporting) { crashReporting = it }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun CloudSyncScreen(onBack: () -> Unit) {
    var autoSync by rememberSaveable { mutableStateOf(true) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item { ScreenTopBar("Cloud Sync Status", "Backup & synchronization", onBack) }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF14261C))
                    .border(1.dp, AppColors.AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☁", fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("All data synced", color = AppColors.AccentGreen, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Last sync: just now", color = AppColors.SubtleText, fontSize = 12.sp)
                }
            }
        }
        item {
            Column {
                SectionLabel("SYNC PREFERENCES")
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Card)
                        .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                ) {
                    PreferenceToggleRow("🔄", Color(0xFF2A2D36), "Auto Sync Sessions", autoSync) { autoSync = it }
                    RowDivider()
                    PreferenceToggleRow("📶", Color(0xFF2A2D36), "Sync on Wi-Fi Only", wifiOnly) { wifiOnly = it }
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Card)
                    .border(1.dp, AppColors.CardBorder, RoundedCornerShape(16.dp))
                    .clickable { }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Sync Now", color = AppColors.AccentCyan, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun ProfileHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppColors.Card)
                    .border(2.dp, AppColors.AccentCyan, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 40.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.AccentCyan)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("PRO", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Marcus Devlin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text("Senior Systems Architect · #8804", color = AppColors.SubtleText, fontSize = 12.sp)
    }
}

@Composable
internal fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Card)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(label, color = AppColors.SubtleText, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    }
}

@Composable
internal fun PreferenceToggleRow(
    icon: String,
    iconBg: Color,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, iconBg)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.AccentCyan,
                uncheckedThumbColor = AppColors.SubtleText,
                uncheckedTrackColor = AppColors.CardBorder
            )
        )
    }
}

@Composable
internal fun SystemRow(icon: String, iconBg: Color, label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, iconBg)
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}
