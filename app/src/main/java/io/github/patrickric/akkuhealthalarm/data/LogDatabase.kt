package io.github.patrickric.akkuhealthalarm.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "readings", indices = [Index("time")])
data class ReadingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val level: Int,
    val plugged: Int,
    val status: Int,
    val voltageMv: Int,
    val tempDeciC: Int,
    val currentMa: Int?,
    val chargeCounterMah: Int?,
)

@Entity(tableName = "events", indices = [Index("time")])
data class EventEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val type: String,
    val level: Int,
    val tempDeciC: Int,
    /** For alarms: how it was given out (AlarmPlayer.Delivery name), null for other events. */
    @ColumnInfo(defaultValue = "NULL") val delivery: String? = null,
    /** How often the alarm was repeated after the first time, and when last. */
    @ColumnInfo(defaultValue = "0") val repeats: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val lastRepeat: Long? = null,
)

/**
 * One charging session, from plugging in to unplugging. Kept independently of the history
 * keeping time, because the capacity trend over years is built from these rows.
 */
@Entity(tableName = "charge_sessions", indices = [Index("endTime")])
data class ChargeSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val startLevel: Int,
    val endLevel: Int,
    val plugged: Int,
    /** Charge put into the battery, from the measured current (mAh). */
    val chargedMah: Double,
    /** Change of the phone's own charge counter, if it reports one (mAh). */
    val counterDeltaMah: Int?,
    val avgCurrentMa: Int,
    val maxCurrentMa: Int,
    val maxTempDeciC: Int,
    /** Estimated full capacity from this session (mAh), or null if the session was too short. */
    val capacityMah: Int?,
    /** The phone reported "full" before unplugging; then the end point is exact. */
    @ColumnInfo(defaultValue = "0") val endedFull: Boolean = false,
    /** Why there is no capacity: SHORT (under 15 %), BROKEN (recording paused), IMPLAUSIBLE. */
    @ColumnInfo(defaultValue = "NULL") val noCapacityReason: String? = null,
    /** The phone's counter was only level x rated capacity; the capacity then comes from the current. */
    @ColumnInfo(defaultValue = "0") val counterFromLevel: Boolean = false,
)

object EventType {
    const val PLUGGED = "plugged"
    const val UNPLUGGED = "unplugged"
    const val ALARM_HIGH = "alarm_high"
    const val ALARM_LOW = "alarm_low"
    const val ALARM_HOT = "alarm_hot"
    const val ALARM_COLD = "alarm_cold"
}

@Dao
interface LogDao {
    @Insert
    suspend fun insertReading(entry: ReadingEntry)

    @Insert
    suspend fun insertEvent(entry: EventEntry): Long

    /** Counts a repeat on the newest row of this alarm type, the one the repeat belongs to. */
    @Query(
        "UPDATE events SET repeats = repeats + 1, lastRepeat = :time " +
            "WHERE id = (SELECT id FROM events WHERE type = :type ORDER BY time DESC LIMIT 1)",
    )
    suspend fun countRepeat(type: String, time: Long)

    @Query("SELECT * FROM readings WHERE time >= :since ORDER BY time")
    fun readingsSince(since: Long): Flow<List<ReadingEntry>>

    @Query("SELECT * FROM events ORDER BY time DESC LIMIT :limit")
    fun recentEvents(limit: Int): Flow<List<EventEntry>>

    @Query("SELECT * FROM readings ORDER BY time DESC LIMIT :limit")
    fun recentReadings(limit: Int): Flow<List<ReadingEntry>>

    @Query("DELETE FROM readings WHERE time < :before")
    suspend fun deleteReadingsBefore(before: Long)

    @Query("DELETE FROM events WHERE time < :before")
    suspend fun deleteEventsBefore(before: Long)

    @Query("SELECT MIN(time) FROM readings")
    fun oldestReadingTime(): Flow<Long?>

    @Insert
    suspend fun insertSession(session: ChargeSession)

    @Query("SELECT * FROM charge_sessions ORDER BY endTime DESC")
    fun sessions(): Flow<List<ChargeSession>>

    @Query("SELECT * FROM charge_sessions WHERE capacityMah IS NOT NULL AND counterDeltaMah IS NOT NULL AND counterFromLevel = 0")
    suspend fun counterSessions(): List<ChargeSession>

    @Update
    suspend fun updateSession(session: ChargeSession)

    @Query("SELECT * FROM readings WHERE time BETWEEN :from AND :to ORDER BY time")
    suspend fun readingsBetween(from: Long, to: Long): List<ReadingEntry>

    @Query("SELECT * FROM charge_sessions WHERE capacityMah IS NOT NULL ORDER BY endTime DESC LIMIT :limit")
    suspend fun recentCapacitySessions(limit: Int): List<ChargeSession>

    @Query("SELECT * FROM events WHERE type LIKE 'alarm_%' ORDER BY time DESC LIMIT :limit")
    fun recentAlarms(limit: Int): Flow<List<EventEntry>>
}

@Database(
    entities = [ReadingEntry::class, EventEntry::class, ChargeSession::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class LogDatabase : RoomDatabase() {
    abstract fun dao(): LogDao

    companion object {
        fun build(context: Context): LogDatabase =
            Room.databaseBuilder(context, LogDatabase::class.java, "log.db")
                // The watcher process writes, the app process shows the changes live.
                .enableMultiInstanceInvalidation()
                .build()
    }
}
