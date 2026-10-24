package com.bookflow.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bookflow.app.domain.model.ReadingLog
import com.bookflow.app.domain.model.ReadingStats
import com.bookflow.app.domain.repository.ReadingStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

private val Context.readingStatsStore by preferencesDataStore(name = "bookflow_reading_stats")

class DataStoreReadingStatsRepository(private val context: Context) : ReadingStatsRepository {
    private val logKey = stringPreferencesKey("daily_seconds")
    private val goalKey = intPreferencesKey("daily_goal_minutes")
    // Outlives screens and ViewModels, so the final flush of a reading session isn't cancelled
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val stats: Flow<ReadingStats> = context.readingStatsStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            ReadingStats.from(
                secondsByDay = ReadingLog.decode(prefs[logKey]),
                dailyGoalMinutes = prefs[goalKey] ?: ReadingStats.DEFAULT_DAILY_GOAL_MINUTES,
                today = LocalDate.now()
            )
        }

    override fun addReadingTime(seconds: Long) {
        if (seconds <= 0) return
        writeScope.launch {
            context.readingStatsStore.edit { prefs ->
                val today = LocalDate.now()
                val log = ReadingLog.decode(prefs[logKey]).toMutableMap()
                log[today] = (log[today] ?: 0L) + seconds
                prefs[logKey] = ReadingLog.encode(log, today)
            }
        }
    }

    override suspend fun setDailyGoal(minutes: Int) {
        context.readingStatsStore.edit { it[goalKey] = minutes.coerceIn(ReadingStats.GOAL_RANGE_MINUTES) }
    }
}
