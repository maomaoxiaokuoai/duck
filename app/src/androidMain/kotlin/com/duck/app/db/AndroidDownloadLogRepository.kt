package com.duck.app.db

import com.duck.app.model.DownloadLog
import kotlinx.coroutines.flow.Flow

class AndroidDownloadLogRepository(private val dao: DownloadLogDao) : DownloadLogRepository {
    override fun getAllLogs(): Flow<List<DownloadLog>> = dao.getAllLogs()
    override suspend fun insertLog(log: DownloadLog) = dao.insertLog(log)
    override suspend fun deleteLog(log: DownloadLog) = dao.deleteLog(log)
    override suspend fun deleteAllLogs() = dao.deleteAllLogs()
    override suspend fun hasUrl(url: String): Boolean = dao.hasUrl(url) > 0
}
