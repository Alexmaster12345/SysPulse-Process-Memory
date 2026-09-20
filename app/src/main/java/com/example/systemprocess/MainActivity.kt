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

private object AppSettings {
    var textScale by mutableStateOf(1.0f)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SystemProcessTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColors.Background
                ) {
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = baseDensity.density,
                            fontScale = baseDensity.fontScale * AppSettings.textScale
                        )
                    ) {
                        var showSplash by rememberSaveable { mutableStateOf(true) }
                        if (showSplash) {
                            SplashScreen(onFinished = { showSplash = false })
                        } else {
                            AppRoot()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
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

private enum class BottomTab(val label: String, val icon: String) {
    Home("Home", "🏠"),
    Search("Search", "🔍"),
    Threads("Threads", "🧩"),
    Analytics("Analytics", "📈"),
    Profile("Profile", "👤")
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val repository = remember { TelemetryRepository(context) }
    val state by repository.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Home) }

    DisposableEffect(repository) {
        repository.start()
        onDispose { repository.stop() }
    }
    LaunchedEffect(repository) {
        repository.poll(intervalMs = 1000L)
    }

    Scaffold(
        containerColor = AppColors.Background,
        bottomBar = {
            BottomNavBar(selectedTab = selectedTab, onSelect = { selectedTab = it })
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                BottomTab.Home -> HomeScreen(
                    state = state,
                    onOpenThreads = { selectedTab = BottomTab.Threads },
                    onOpenAnalytics = { selectedTab = BottomTab.Analytics }
                )
                BottomTab.Threads -> ThreadsScreen(state = state, onBack = { selectedTab = BottomTab.Home })
                BottomTab.Analytics -> AnalyticsScreen(
                    state = state,
                    onViewAllProcesses = { selectedTab = BottomTab.Threads },
                    onBack = { selectedTab = BottomTab.Home }
                )
                BottomTab.Search -> SearchScreen(state = state)
                BottomTab.Profile -> ProfileScreen()
            }
        }
    }
}

@Composable
private fun BottomNavBar(selectedTab: BottomTab, onSelect: (BottomTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Card)
            .border(width = 1.dp, color = AppColors.CardBorder)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val tint = if (selected) AppColors.AccentBlue else AppColors.SubtleText
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(tab) }
            ) {
                Text(tab.icon, color = tint, fontSize = 18.sp)
                Text(tab.label, color = tint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = AppColors.SubtleText, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

private data class QuickFilter(val label: String, val color: Color)
private data class DiagnosticTool(val icon: String, val iconBg: Color, val title: String, val subtitle: String)

@Composable
private fun SearchScreen(state: TelemetryUiState) {
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
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
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
private fun SectionLabel(text: String) {
    Text(text, color = AppColors.SubtleText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FlowChipsRow(filters: List<QuickFilter>, selected: String?, onSelect: (String) -> Unit) {
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
private fun DiagnosticToolCard(tool: DiagnosticTool, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
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

private enum class ProfileSubScreen { None, Security, CloudSync, Display }

private data class TextSizeOption(val label: String, val scale: Float)

private val textSizeOptions = listOf(
    TextSizeOption("Small", 0.85f),
    TextSizeOption("Default", 1.0f),
    TextSizeOption("Large", 1.15f),
    TextSizeOption("Extra Large", 1.3f)
)

private fun textScaleLabel(scale: Float): String =
    textSizeOptions.minByOrNull { kotlin.math.abs(it.scale - scale) }?.label ?: "Default"

@Composable
private fun DisplayTextScreen(onBack: () -> Unit) {
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


private data class HwCheck(val label: String, val icon: String, val supported: Boolean)

@Composable
private fun HardwareDiagnosticsScreen(onBack: () -> Unit) {
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
private fun HwCheckRow(check: HwCheck) {
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

@Composable
private fun ProfileScreen() {
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
private fun SecurityPrivacyScreen(onBack: () -> Unit) {
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
private fun CloudSyncScreen(onBack: () -> Unit) {
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
private fun ProfileHeader() {
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
private fun StatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
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
private fun PreferenceToggleRow(
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
private fun SystemRow(icon: String, iconBg: Color, label: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
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

@Composable
private fun IconTile(icon: String, background: Color) {
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
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppColors.CardBorder)
    )
}

@Composable
private fun HomeScreen(
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
private fun ThreadsScreen(state: TelemetryUiState, onBack: () -> Unit) {
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
private fun AnalyticsScreen(state: TelemetryUiState, onViewAllProcesses: () -> Unit, onBack: () -> Unit) {
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
        item { ExportSessionCard(state) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ExportSessionCard(state: TelemetryUiState) {
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
    }
}

@Composable
private fun ExportButton(
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
private fun ScreenTopBar(title: String, subtitle: String, onBack: () -> Unit, trailingIcon: String? = null) {
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
private fun DashboardHeader(state: TelemetryUiState) {
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

private enum class MonitorStatus(val label: String, val icon: String, val color: Color) {
    Ok("OK", "●", AppColors.AccentGreen),
    Warning("WARNING", "▲", AppColors.AccentOrange),
    Error("ERROR", "✕", AppColors.AccentRed)
}

private fun computeMonitorStatus(state: TelemetryUiState): MonitorStatus {
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
private fun SystemStatusBadge(state: TelemetryUiState) {
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
private fun TopMetricRow(state: TelemetryUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile("CPU LOAD", "${state.cpuLoadPercent} %", Modifier.weight(1f), AppColors.AccentCyan)
        MetricTile("RAM", String.format("%.1f GB", state.totalMemoryGb), Modifier.weight(1f), Color.White)
        MetricTile("THERMAL", "${state.deviceTempC}° C", Modifier.weight(1f), AppColors.AccentOrange)
    }
}

@Composable
private fun MetricTile(title: String, value: String, modifier: Modifier, valueColor: Color) {
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
private fun CpuThermalCard(state: TelemetryUiState) {
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

private fun coreStatusColor(status: String): Color = when (status) {
    "COOL" -> AppColors.AccentCyan
    "WARM" -> AppColors.AccentGreen
    "HOT" -> AppColors.AccentOrange
    "CRITICAL" -> AppColors.AccentRed
    else -> AppColors.SubtleText
}

@Composable
private fun MemoryTimelineCard(state: TelemetryUiState, onOpenMap: () -> Unit) {
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
private fun AllocationMapToggle(onOpenMap: () -> Unit) {
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
private fun BatteryCurveCard(state: TelemetryUiState) {
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
private fun ActiveProcessCard(state: TelemetryUiState, onOpenFullManager: () -> Unit) {
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

private fun processStateColor(p: ProcessEntry): Color =
    if (p.isForeground) AppColors.AccentGreen else AppColors.SubtleText

@Composable
private fun MemoryDistributionCard(state: TelemetryUiState) {
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
private fun AllocationHistoryCard(state: TelemetryUiState) {
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
private fun LegendDot(color: Color, label: String) {
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
private fun TopMemoryConsumersCard(state: TelemetryUiState, onViewAll: () -> Unit) {
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

private fun consumerColor(index: Int): Color = when (index % 4) {
    0 -> AppColors.AccentCyan
    1 -> AppColors.AccentGreen
    2 -> AppColors.AccentOrange
    else -> AppColors.AccentRed
}

private fun parseMemToKb(memText: String): Long = when {
    memText.endsWith("GB") -> (memText.removeSuffix("GB").toFloatOrNull() ?: 0f).times(1_048_576).toLong()
    memText.endsWith("MB") -> (memText.removeSuffix("MB").toFloatOrNull() ?: 0f).times(1024).toLong()
    else -> 0L
}

private fun shortAppName(processName: String): String =
    processName.substringAfterLast('.').replaceFirstChar { it.uppercase() }

private fun segmentColor(segment: MemorySegment): Color = when (segment.colorIndex) {
    0 -> AppColors.AccentCyan
    1 -> AppColors.AccentGreen
    2 -> AppColors.AccentOrange
    else -> AppColors.AccentRed
}

@Composable
private fun ProcessListCard(state: TelemetryUiState) {
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

@Composable
private fun CardShell(
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
private fun MiniLineChart(values: List<Float>, lineColor: Color, fillColor: Color, modifier: Modifier = Modifier) {
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
private fun DualLineChart(
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
private fun DonutChart(values: List<Float>, colors: List<Color>, centerLabel: String, modifier: Modifier = Modifier) {
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

private object AppColors {
    val Background = Color(0xFF0B0C0F)
    val Card = Color(0xFF16181E)
    val CardBorder = Color(0xFF2A2D36)
    val SubtleText = Color(0xFF8B91A3)
    val AccentCyan = Color(0xFF12CFFB)
    val AccentBlue = Color(0xFF2F6BFF)
    val AccentGreen = Color(0xFF87E10D)
    val AccentOrange = Color(0xFFFFAA17)
    val AccentRed = Color(0xFFFF4778)
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0C0F, showSystemUi = true)
@Composable
private fun AppPreview() {
    SystemProcessTheme {
        AppRoot()
    }
}
