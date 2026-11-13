package com.duck.app.db

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duck.app.model.DownloadLog
import kotlinx.coroutines.flow.Flow

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_logs ADD COLUMN thumbnailPath TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE download_logs ADD COLUMN author TEXT")
    }
}

@Dao
interface DownloadLogDao {
    @Query("SELECT * FROM download_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DownloadLog>>

    @Insert
    suspend fun insertLog(log: DownloadLog)

    @Query("DELETE FROM download_logs")
    suspend fun deleteAllLogs()

    @Delete
    suspend fun deleteLog(log: DownloadLog)

    @Query("SELECT COUNT(*) FROM download_logs WHERE url = :url")
    suspend fun hasUrl(url: String): Int
}

/**
 * Adds the post metadata the history sheet shows.
 *
 * Every column is nullable: entries written before this migration have no publish date
 * or counts, and NULL renders as "not reported" rather than a misleading zero.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE download_logs ADD COLUMN createTime INTEGER")
        database.execSQL("ALTER TABLE download_logs ADD COLUMN platform TEXT")
        database.execSQL("ALTER TABLE download_logs ADD COLUMN likeCount INTEGER")
        database.execSQL("ALTER TABLE download_logs ADD COLUMN commentCount INTEGER")
        database.execSQL("ALTER TABLE download_logs ADD COLUMN shareCount INTEGER")
        database.execSQL("ALTER TABLE download_logs ADD COLUMN collectCount INTEGER")
    }
}


/** Adds the clip length the preview card shows. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE download_logs ADD COLUMN duration INTEGER")
    }
}


/** Adds the media kind each saved entry belongs to. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE download_logs ADD COLUMN mediaKind TEXT")
    }
}

@Database(entities = [DownloadLog::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadLogDao(): DownloadLogDao
}
