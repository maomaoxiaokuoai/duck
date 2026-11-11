package com.duck.app.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.duck.app.database.objects.CommandTemplate
import com.duck.app.database.objects.DownloadedVideoInfo
import com.duck.app.database.objects.OptionShortcut

@Database(
    entities = [DownloadedVideoInfo::class, CommandTemplate::class, OptionShortcut::class],
    // v6 drops the CookieProfile table; Room cannot auto-migrate a table removal,
    // so older installs rebuild the (small, regenerable) local tables instead.
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoInfoDao(): VideoInfoDao
}