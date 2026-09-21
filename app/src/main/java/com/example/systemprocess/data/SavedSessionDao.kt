package com.example.systemprocess.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSessionDao {

    @Query("SELECT * FROM saved_sessions ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedSession>>

    @Insert
    suspend fun insert(session: SavedSession): Long

    @Delete
    suspend fun delete(session: SavedSession)

    @Query("DELETE FROM saved_sessions")
    suspend fun clear()
}
