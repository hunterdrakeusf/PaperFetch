package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paper_records")
data class PaperRecord(
    @PrimaryKey
    val doi: String,
    val title: String = "",
    val authors: String = "",
    val journal: String = "",
    val year: Int? = null,
    val oaStatus: String = "unknown", // gold, green, bronze, hybrid, closed, unknown
    val isOa: Boolean = false,
    val license: String? = null,
    val pdfUrl: String? = null,
    val downloadStatus: String = "PENDING", // PENDING, RESOLVING, DOWNLOADING, DOWNLOADED, PAYWALLED, FAILED
    val statusMessage: String = "Queued",
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long? = null,
    val sourceFileName: String = "",
    val batchId: String = ""
)

enum class DownloadStatus(val label: String) {
    PENDING("Pending"),
    RESOLVING("Resolving DOI..."),
    DOWNLOADING("Downloading PDF..."),
    DOWNLOADED("Downloaded"),
    PAYWALLED("Missing / Paywalled"),
    FAILED("Download Failed")
}

data class ScanProgressState(
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val currentDoi: String = "",
    val currentTitle: String = "",
    val currentStep: String = "",
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val currentItemProgress: Float = 0f, // 0..1 for download progress
    val batchId: String = ""
) {
    val overallProgress: Float
        get() = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
}

data class DashboardStats(
    val totalScanned: Int = 0,
    val downloadedCount: Int = 0,
    val paywalledCount: Int = 0,
    val failedCount: Int = 0,
    val pendingCount: Int = 0,
    val totalDownloadedBytes: Long = 0L
) {
    val successRatePercent: Int
        get() = if (totalScanned > 0) ((downloadedCount.toDouble() / totalScanned) * 100).toInt() else 0
}

data class ScanLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val doi: String = "",
    val level: LogLevel = LogLevel.INFO,
    val message: String = ""
)

enum class LogLevel {
    INFO, SUCCESS, WARNING, ERROR
}
