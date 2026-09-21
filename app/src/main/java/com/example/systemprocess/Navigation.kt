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
import com.example.systemprocess.telemetry.TelemetryViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.systemprocess.telemetry.SessionExporter
import com.example.systemprocess.telemetry.TelemetryUiState
import com.example.systemprocess.telemetry.ThermalCore
import com.example.systemprocess.ui.theme.SystemProcessTheme
import kotlin.math.min

internal enum class BottomTab(val label: String, val icon: String) {
    Home("Home", "🏠"),
    Search("Search", "🔍"),
    Threads("Threads", "🧩"),
    Analytics("Analytics", "📈"),
    Profile("Profile", "👤")
}

@Composable
internal fun AppRoot() {
    val viewModel: TelemetryViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val savedSessions by viewModel.savedSessions.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(BottomTab.Home) }

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
                    onBack = { selectedTab = BottomTab.Home },
                    onSaveSession = { viewModel.saveCurrentSession() }
                )
                BottomTab.Search -> SearchScreen(state = state)
                BottomTab.Profile -> ProfileScreen(
                    savedSessions = savedSessions,
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onClearSessions = { viewModel.clearSessions() }
                )
            }
        }
    }
}

@Composable
internal fun BottomNavBar(selectedTab: BottomTab, onSelect: (BottomTab) -> Unit) {
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
internal fun PlaceholderScreen(title: String, message: String) {
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
