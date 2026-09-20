package com.example.systemprocess.telemetry

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile

/**
 * Collects real, on-device telemetry using only public, unrooted-safe Android APIs:
 * - Own-process CPU load via [Process.getElapsedCpuTime]
 * - System + per-app memory via [ActivityManager.MemoryInfo] and [Debug.MemoryInfo]
 * - Battery level/current/temperature via sticky [Intent.ACTION_BATTERY_CHANGED] + [BatteryManager]
 * - Thermal status via [PowerManager] (API 29+); per-core frequency via sysfs cpufreq nodes.
 *
 * Note: On Android 8.0+, unrooted apps cannot enumerate other apps' PIDs or read their
 * /proc/[pid] entries. [processes] therefore reflects what the platform legitimately exposes
 * (the host app's own process plus, where available, [ActivityManager.getRunningAppProcesses]
 * results) rather than a full system-wide process tree.
 */
class TelemetryRepository(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val _state = MutableStateFlow(TelemetryUiState())
    val state: StateFlow<TelemetryUiState> = _state.asStateFlow()

    private val memoryHistory = ArrayDeque<Float>()
    private val nativeHeapHistory = ArrayDeque<Float>()
    private val batteryHistory = ArrayDeque<Float>()

    private var lastCpuTimeMs = Process.getElapsedCpuTime()
    private var lastElapsedRealtimeMs = SystemClock.elapsedRealtime()
    private var sessionSeconds = 0

    private var thermalStatus: Int = PowerManager.THERMAL_STATUS_NONE
    private val thermalListener: ((Int) -> Unit)? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            { status -> thermalStatus = status }
        } else null

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { powerManager.addThermalStatusListener(it) }
            thermalStatus = powerManager.currentThermalStatus
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalListener?.let { powerManager.removeThermalStatusListener(it) }
        }
    }

    /** Suspends forever, emitting a refreshed [TelemetryUiState] into [state] every [intervalMs]. */
    suspend fun poll(intervalMs: Long = 1000L) {
        while (true) {
            _state.value = buildSnapshot()
            delay(intervalMs)
        }
    }

    private fun buildSnapshot(): TelemetryUiState {
        sessionSeconds += 1
        val cpuLoad = readCpuLoadPercent()
        val (totalGb, availGb) = readSystemMemoryGb()
        val breakdown = readMemoryBreakdownGb()
        val usedGb = breakdown.sumOf { it.valueGb.toDouble() }.toFloat()
        val (battLevel, battCurrentUa, battTempC) = readBatterySnapshot()
        val cores = readThermalCores()
        val deviceTemp = cores.map { it.tempC }.filter { it > 0 }.let { list ->
            if (list.isNotEmpty()) list.average().toInt() else battTempC.toInt()
        }

        pushHistory(memoryHistory, usedGb, maxSize = 30)
        val nativeHeapGb = breakdown.firstOrNull { it.label == "Native" }?.valueGb ?: 0f
        pushHistory(nativeHeapHistory, nativeHeapGb, maxSize = 30)
        pushHistory(batteryHistory, battLevel.toFloat(), maxSize = 48)

        return TelemetryUiState(
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            cpuLoadPercent = cpuLoad,
            totalMemoryGb = totalGb,
            availableMemoryGb = availGb,
            deviceTempC = deviceTemp,
            thermalState = thermalStatusLabel(thermalStatus),
            thermalCores = cores,
            memoryTimelineGb = memoryHistory.toList(),
            nativeHeapTimelineGb = nativeHeapHistory.toList(),
            batteryPercentHistory = batteryHistory.toList(),
            batteryLevelPercent = battLevel,
            batteryCurrentMicroAmps = battCurrentUa,
            batteryTempC = battTempC,
            memoryBreakdown = breakdown,
            processes = readProcesses(),
            sessionElapsedSeconds = sessionSeconds
        )
    }

    // ---- CPU ----

    private fun readCpuLoadPercent(): Int {
        val nowCpu = Process.getElapsedCpuTime()
        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaCpu = (nowCpu - lastCpuTimeMs).coerceAtLeast(0L)
        val deltaElapsed = (nowElapsed - lastElapsedRealtimeMs).coerceAtLeast(1L)
        lastCpuTimeMs = nowCpu
        lastElapsedRealtimeMs = nowElapsed
        val percent = (deltaCpu.toDouble() / deltaElapsed.toDouble() * 100.0)
        return percent.coerceIn(0.0, 100.0).toInt()
    }

    // ---- Memory ----

    private fun readSystemMemoryGb(): Pair<Float, Float> {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        val totalGb = info.totalMem / 1_073_741_824f
        val availGb = info.availMem / 1_073_741_824f
        return totalGb to availGb
    }

    private fun readMemoryBreakdownGb(): List<MemorySegment> {
        return try {
            val memInfoArray = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            val debugInfo = memInfoArray.firstOrNull() ?: return emptyList()
            fun statKb(key: String): Long =
                debugInfo.getMemoryStat(key)?.toLongOrNull() ?: 0L

            val javaHeapKb = statKb("summary.java-heap")
            val nativeHeapKb = statKb("summary.native-heap")
            val codeKb = statKb("summary.code")
            val graphicsKb = statKb("summary.graphics")
            val systemKb = statKb("summary.system") + statKb("summary.stack")

            listOf(
                MemorySegment("App Heap", javaHeapKb / 1_048_576f, 0),
                MemorySegment("Native", nativeHeapKb / 1_048_576f, 1),
                MemorySegment("Graphics", (graphicsKb + codeKb) / 1_048_576f, 2),
                MemorySegment("System", systemKb / 1_048_576f, 3)
            )
        } catch (t: Throwable) {
            emptyList()
        }
    }

    // ---- Battery ----

    private fun readBatterySnapshot(): Triple<Int, Long, Float> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val currentUa = try {
            batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (t: Throwable) {
            0L
        }
        return Triple(percent, currentUa, tempTenths / 10f)
    }

    // ---- Thermal / per-core ----

    private fun readThermalCores(): List<ThermalCore> {
        val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return (0 until cpuCount).map { index ->
            val freqMhz = readCpuFreqMhz(index)
            val tempC = readCoreTempC(index)
            ThermalCore(
                id = "C$index",
                frequencyMhz = freqMhz,
                tempC = tempC,
                status = coreStatusLabel(tempC)
            )
        }
    }

    private fun readCpuFreqMhz(coreIndex: Int): Int {
        return try {
            val path = "/sys/devices/system/cpu/cpu$coreIndex/cpufreq/scaling_cur_freq"
            val raf = RandomAccessFile(path, "r")
            val khz = raf.readLine()?.trim()?.toLongOrNull() ?: 0L
            raf.close()
            (khz / 1000L).toInt()
        } catch (t: Throwable) {
            0
        }
    }

    private fun readCoreTempC(coreIndex: Int): Int {
        return try {
            val zonesDir = File("/sys/class/thermal")
            val zone = zonesDir.listFiles { f -> f.name.startsWith("thermal_zone") }
                ?.sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }
                ?.getOrNull(coreIndex)
            val tempFile = zone?.resolve("temp")
            val raw = tempFile?.takeIf { it.canRead() }?.readText()?.trim()?.toLongOrNull() ?: return 0
            // Kernel commonly reports milli-Celsius; normalize.
            if (raw > 1000) (raw / 1000).toInt() else raw.toInt()
        } catch (t: Throwable) {
            0
        }
    }

    private fun coreStatusLabel(tempC: Int): String = when {
        tempC <= 0 -> "N/A"
        tempC < 40 -> "COOL"
        tempC < 46 -> "WARM"
        tempC < 55 -> "HOT"
        else -> "CRITICAL"
    }

    private fun thermalStatusLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Nominal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
        else -> "Unknown"
    }

    // ---- Processes (own app only, per Android 8.0+ restrictions) ----

    private fun readProcesses(): List<ProcessEntry> {
        return try {
            val runningProcesses = activityManager.runningAppProcesses.orEmpty()
            runningProcesses.map { info ->
                val isForeground = info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                val memKb = try {
                    activityManager.getProcessMemoryInfo(intArrayOf(info.pid))
                        .firstOrNull()?.totalPss ?: 0
                } catch (t: Throwable) {
                    0
                }
                ProcessEntry(
                    name = info.processName,
                    pid = info.pid,
                    cpuPercent = if (info.pid == Process.myPid()) lastReportedCpuPercent() else 0f,
                    memText = formatMem(memKb),
                    state = if (isForeground) "Running" else "Background",
                    isForeground = isForeground
                )
            }.sortedByDescending { it.isForeground }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun lastReportedCpuPercent(): Float = _state.value.cpuLoadPercent.toFloat()

    private fun formatMem(kb: Int): String = when {
        kb <= 0 -> "0MB"
        kb >= 1_048_576 -> String.format("%.1fGB", kb / 1_048_576f)
        else -> "${kb / 1024}MB"
    }

    private fun pushHistory(deque: ArrayDeque<Float>, value: Float, maxSize: Int) {
        deque.addLast(value)
        while (deque.size > maxSize) deque.removeFirst()
    }
}
