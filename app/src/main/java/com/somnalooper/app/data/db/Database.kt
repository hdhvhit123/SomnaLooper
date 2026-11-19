package com.somnalooper.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A saved "favourite recipe" mix. [layers] is encoded with [MixCodec]. */
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val layers: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = 0,
)

/** An audio file imported by the user (persisted SAF uri permission, no storage permission needed). */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis(),
)

/** Listening history entry. */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val label: String,
    val stopReason: String? = null,
)

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<PresetEntity>>

    @Insert
    suspend fun insert(p: PresetEntity): Long

    @Update
    suspend fun update(p: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE presets SET lastUsedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long = System.currentTimeMillis())
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Insert
    suspend fun insert(t: TrackEntity): Long

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tracks SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<SessionEntity>>

    @Insert
    suspend fun insert(s: SessionEntity): Long

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Database(entities = [PresetEntity::class, TrackEntity::class, SessionEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun presets(): PresetDao
    abstract fun tracks(): TrackDao
    abstract fun sessions(): SessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "somnalooper.db").build()
    }
}
