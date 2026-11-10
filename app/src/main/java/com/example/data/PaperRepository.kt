package com.example.data

import android.content.Context
import android.os.Environment
import com.example.model.DashboardStats
import com.example.model.LogLevel
import com.example.model.PaperRecord
import com.example.model.ScanLogEntry
import com.example.model.ScanProgressState
import com.example.service.DownloadResult
import com.example.service.OpenAccessResolver
import com.example.service.PdfDownloader
import com.example.sound.ElevatorMusicPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class PaperRepository(
    private val context: Context,
    private val paperDao: PaperDao,
    private val resolver: OpenAccessResolver = OpenAccessResolver(),
    private val downloader: PdfDownloader = PdfDownloader(),
    private val elevatorSound: ElevatorMusicPlayer = ElevatorMusicPlayer()
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var scanJob: Job? = null

    private val _scanProgress = MutableStateFlow(ScanProgressState())
    val scanProgress: StateFlow<ScanProgressState> = _scanProgress.asStateFlow()

    private val _scanLogs = MutableStateFlow<List<ScanLogEntry>>(emptyList())
    val scanLogs: StateFlow<List<ScanLogEntry>> = _scanLogs.asStateFlow()

    private val _designatedFolder = MutableStateFlow<File>(getDefaultDownloadDir())
    val designatedFolder: StateFlow<File> = _designatedFolder.asStateFlow()

    private val _isSoundMuted = MutableStateFlow(false)
    val isSoundMuted: StateFlow<Boolean> = _isSoundMuted.asStateFlow()

    private var isScanPaused = false

    fun toggleSoundMuted() {
        val newVal = !_isSoundMuted.value
        _isSoundMuted.value = newVal
        elevatorSound.setMuted(newVal)
        addLog(LogLevel.INFO, if (newVal) "Jeopardy theme muted" else "Jeopardy theme enabled")
    }

    private fun getDefaultDownloadDir(): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dir = if (external != null) {
            File(external, "PaperFetch_PDFs")
        } else {
            File(context.filesDir, "PaperFetch_PDFs")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun setDesignatedFolder(file: File) {
        if (!file.exists()) file.mkdirs()
        _designatedFolder.value = file
        addLog(LogLevel.INFO, "Designated download folder updated: ${file.absolutePath}")
    }

    fun getAllPapers(): Flow<List<PaperRecord>> = paperDao.getAllPapersFlow()

    fun getDashboardStats(): Flow<DashboardStats> {
        return combine(
            paperDao.getTotalCountFlow(),
            paperDao.getDownloadedCountFlow(),
            paperDao.getPaywalledCountFlow(),
            paperDao.getFailedCountFlow(),
            paperDao.getTotalDownloadedBytesFlow()
        ) { total, downloaded, paywalled, failed, bytes ->
            val pending = total - (downloaded + paywalled + failed)
            DashboardStats(
                totalScanned = total,
                downloadedCount = downloaded,
                paywalledCount = paywalled,
                failedCount = failed,
                pendingCount = maxOf(0, pending),
                totalDownloadedBytes = bytes ?: 0L
            )
        }
    }

    fun startBatchScan(papers: List<PaperRecord>, sourceName: String) {
        if (papers.isEmpty()) {
            addLog(LogLevel.WARNING, "No valid DOIs found in '$sourceName'")
            return
        }

        scanJob?.cancel()
        isScanPaused = false

        val batchId = UUID.randomUUID().toString()
        _scanLogs.value = emptyList() // clear previous logs for new batch

        addLog(LogLevel.INFO, "Loaded ${papers.size} references from '$sourceName' (Batch: ${batchId.take(8)})")

        scope.launch {
            // Save initial records to DB
            paperDao.insertAllIgnore(papers.map { it.copy(batchId = batchId) })

            _scanProgress.value = ScanProgressState(
                isScanning = true,
                isPaused = false,
                currentStep = "Starting scan of ${papers.size} DOIs...",
                completedCount = 0,
                totalCount = papers.size,
                batchId = batchId
            )

            elevatorSound.startMusic(scope)
            executeScanLoop(papers)
        }
    }

    private suspend fun executeScanLoop(papers: List<PaperRecord>) {
        var completed = 0
        val targetDir = _designatedFolder.value
        elevatorSound.startMusic(scope)

        try {
            for (record in papers) {
                while (isScanPaused) {
                    _scanProgress.value = _scanProgress.value.copy(isPaused = true, currentStep = "Scan paused")
                    elevatorSound.stopMusic()
                    delay(300)
                }

                val doi = record.doi
                _scanProgress.value = _scanProgress.value.copy(
                    isScanning = true,
                    isPaused = false,
                    currentDoi = doi,
                    currentTitle = record.title.ifEmpty { "DOI: $doi" },
                    currentStep = "Resolving DOI: $doi via OpenAlex & Europe PMC...",
                    currentItemProgress = 0.1f
                )

                // Update DB status to RESOLVING
                val currentInDb = paperDao.getPaperByDoi(doi) ?: record
                paperDao.insertOrUpdate(
                    currentInDb.copy(
                        downloadStatus = "RESOLVING",
                        statusMessage = "Resolving open-access availability..."
                    )
                )

                addLog(LogLevel.INFO, "Searching free OA PDF for DOI: $doi", doi)

                try {
                    // 1. Resolve DOI for Open Access PDF link
                    val resolved = resolver.resolve(doi)

                    val paperTitle = if (resolved.title.isNotBlank()) resolved.title else currentInDb.title.ifBlank { "DOI: $doi" }
                    val paperAuthors = if (resolved.authors.isNotBlank()) resolved.authors else currentInDb.authors
                    val paperJournal = if (resolved.journal.isNotBlank()) resolved.journal else currentInDb.journal
                    val paperYear = resolved.year ?: currentInDb.year

                    if (resolved.isOa && !resolved.pdfUrl.isNullOrBlank()) {
                        addLog(
                            LogLevel.SUCCESS,
                            "Found ${resolved.oaStatus.uppercase()} OA PDF via ${resolved.sourceDescription}",
                            doi
                        )

                        _scanProgress.value = _scanProgress.value.copy(
                            currentTitle = paperTitle,
                            currentStep = "Downloading PDF from ${resolved.sourceDescription}...",
                            currentItemProgress = 0.4f
                        )

                        // Update DB with metadata and status DOWNLOADING
                        paperDao.insertOrUpdate(
                            currentInDb.copy(
                                title = paperTitle,
                                authors = paperAuthors,
                                journal = paperJournal,
                                year = paperYear,
                                oaStatus = resolved.oaStatus,
                                isOa = true,
                                license = resolved.license,
                                pdfUrl = resolved.pdfUrl,
                                downloadStatus = "DOWNLOADING",
                                statusMessage = "Downloading PDF file..."
                            )
                        )

                        // 2. Download the PDF
                        val downloadRes = downloader.downloadPdf(
                            pdfUrl = resolved.pdfUrl,
                            doi = doi,
                            title = paperTitle,
                            destinationDir = targetDir
                        ) { bytesRead, totalBytes ->
                            val ratio = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0.5f
                            _scanProgress.value = _scanProgress.value.copy(
                                currentItemProgress = 0.4f + (ratio * 0.55f)
                            )
                        }

                        when (downloadRes) {
                            is DownloadResult.Success -> {
                                val mb = String.format("%.2f", downloadRes.sizeBytes / (1024.0 * 1024.0))
                                paperDao.insertOrUpdate(
                                    currentInDb.copy(
                                        title = paperTitle,
                                        authors = paperAuthors,
                                        journal = paperJournal,
                                        year = paperYear,
                                        oaStatus = resolved.oaStatus,
                                        isOa = true,
                                        license = resolved.license,
                                        pdfUrl = resolved.pdfUrl,
                                        downloadStatus = "DOWNLOADED",
                                        statusMessage = "Downloaded successfully ($mb MB)",
                                        localFilePath = downloadRes.file.absolutePath,
                                        fileSizeBytes = downloadRes.sizeBytes,
                                        downloadedAt = System.currentTimeMillis()
                                    )
                                )
                                addLog(
                                    LogLevel.SUCCESS,
                                    "Saved PDF: ${downloadRes.file.name} ($mb MB)",
                                    doi
                                )
                            }
                            is DownloadResult.Error -> {
                                paperDao.insertOrUpdate(
                                    currentInDb.copy(
                                        title = paperTitle,
                                        authors = paperAuthors,
                                        journal = paperJournal,
                                        year = paperYear,
                                        oaStatus = resolved.oaStatus,
                                        isOa = true,
                                        license = resolved.license,
                                        pdfUrl = resolved.pdfUrl,
                                        downloadStatus = "FAILED",
                                        statusMessage = downloadRes.message
                                    )
                                )
                                addLog(
                                    LogLevel.ERROR,
                                    "Failed downloading PDF: ${downloadRes.message}",
                                    doi
                                )
                            }
                        }
                    } else {
                        // Paywalled or closed access
                        paperDao.insertOrUpdate(
                            currentInDb.copy(
                                title = paperTitle,
                                authors = paperAuthors,
                                journal = paperJournal,
                                year = paperYear,
                                oaStatus = "closed",
                                isOa = false,
                                license = null,
                                pdfUrl = null,
                                downloadStatus = "PAYWALLED",
                                statusMessage = "Paywalled / No legal free Open Access PDF found"
                            )
                        )
                        addLog(
                            LogLevel.WARNING,
                            "Paywalled / No free OA version found for '$paperTitle'",
                            doi
                        )
                    }
                } catch (e: CancellationException) {
                    addLog(LogLevel.INFO, "Scan cancelled by user")
                    throw e
                } catch (e: Exception) {
                    paperDao.insertOrUpdate(
                        currentInDb.copy(
                            downloadStatus = "FAILED",
                            statusMessage = "Error: ${e.message}"
                        )
                    )
                    addLog(LogLevel.ERROR, "Scan error on DOI $doi: ${e.message}", doi)
                }

                completed++
                _scanProgress.value = _scanProgress.value.copy(
                    completedCount = completed,
                    currentItemProgress = 1.0f
                )

                // Polite delay between requests
                delay(250)
            }

            _scanProgress.value = _scanProgress.value.copy(
                isScanning = false,
                isPaused = false,
                currentStep = "Scan completed! $completed DOIs processed.",
                currentItemProgress = 1f
            )
            addLog(LogLevel.SUCCESS, "Scan batch finished. Processed $completed references.")

            // Stop music and play arrival DING!
            elevatorSound.stopMusic()
            elevatorSound.playElevatorDing(scope)

        } catch (e: CancellationException) {
            elevatorSound.stopMusic()
            throw e
        } catch (e: Exception) {
            elevatorSound.stopMusic()
        }
    }

    fun pauseScan() {
        isScanPaused = true
        elevatorSound.stopMusic()
        _scanProgress.value = _scanProgress.value.copy(isPaused = true)
        addLog(LogLevel.INFO, "Scan paused by user")
    }

    fun resumeScan() {
        isScanPaused = false
        elevatorSound.startMusic(scope)
        _scanProgress.value = _scanProgress.value.copy(isPaused = false)
        addLog(LogLevel.INFO, "Resuming scan...")
    }

    fun cancelScan() {
        scanJob?.cancel()
        elevatorSound.stopMusic()
        isScanPaused = false
        _scanProgress.value = _scanProgress.value.copy(
            isScanning = false,
            isPaused = false,
            currentStep = "Scan cancelled"
        )
        addLog(LogLevel.WARNING, "Scan operation cancelled")
    }

    fun retryDoi(doi: String) {
        scope.launch {
            val record = paperDao.getPaperByDoi(doi) ?: return@launch
            addLog(LogLevel.INFO, "Retrying single DOI: $doi", doi)
            elevatorSound.startMusic(scope)
            executeScanLoop(listOf(record))
        }
    }

    fun retryAllMissing() {
        scope.launch {
            val missing = paperDao.getActiveQueue()
            if (missing.isNotEmpty()) {
                addLog(LogLevel.INFO, "Retrying ${missing.size} missing/failed DOIs...")
                startBatchScan(missing, "Retry Queue")
            }
        }
    }

    fun clearAllRecords() {
        scope.launch {
            scanJob?.cancel()
            paperDao.clearAll()
            _scanProgress.value = ScanProgressState()
            _scanLogs.value = emptyList()
            addLog(LogLevel.INFO, "Cleared all scanned papers and history")
        }
    }

    private fun addLog(level: LogLevel, message: String, doi: String = "") {
        val entry = ScanLogEntry(
            doi = doi,
            level = level,
            message = message
        )
        _scanLogs.value = listOf(entry) + _scanLogs.value.take(150)
    }
}
