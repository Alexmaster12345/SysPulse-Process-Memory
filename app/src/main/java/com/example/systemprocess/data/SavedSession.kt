package com.example.systemprocess.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A persisted snapshot of a telemetry session, saved on demand by the user. */
@Entity(tableName = "saved_sessions")
data class SavedSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedAt: Long,
    val deviceModel: String,
    val androidVersion: String,
    val durationSeconds: Int,
    val cpuLoadPercent: Int,
    val usedMemoryGb: Float,
    val totalMemoryGb: Float,
    val deviceTempC: Int,
    val thermalState: String,
    val batteryLevelPercent: Int,
    val batteryCurrentMicroAmps: Long,
    val batteryTempC: Float,
    val processCount: Int
)
