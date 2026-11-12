package com.duck.app.db

import com.duck.app.model.DownloadLog
import kotlinx.coroutines.flow.Flow

interface DownloadLogRepository {
    fun getAllLogs(): Flow<List<DownloadLog>>
    suspend fun insertLog(log: DownloadLog)
    suspend fun deleteLog(log: DownloadLog)
    suspend fun deleteAllLogs()
    suspend fun hasUrl(url: String): Boolean
}
