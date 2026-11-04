package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PaperDatabase
import com.example.data.PaperRepository
import com.example.data.SampleData
import com.example.model.DashboardStats
import com.example.model.PaperRecord
import com.example.model.ScanLogEntry
import com.example.model.ScanProgressState
import com.example.parser.DoiParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

class PaperViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PaperDatabase.getInstance(application)
    private val repository = PaperRepository(
        context = application,
        paperDao = database.paperDao()
    )

    val scanProgress: StateFlow<ScanProgressState> = repository.scanProgress
    val scanLogs: StateFlow<List<ScanLogEntry>> = repository.scanLogs
    val designatedFolder: StateFlow<File> = repository.designatedFolder
    val isSoundMuted: StateFlow<Boolean> = repository.isSoundMuted
    val dashboardStats: StateFlow<DashboardStats> = repository.getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    private val _filterStatus = MutableStateFlow("ALL") // ALL, DOWNLOADED, PAYWALLED, FAILED
    val filterStatus: StateFlow<String> = _filterStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPaper = MutableStateFlow<PaperRecord?>(null)
    val selectedPaper: StateFlow<PaperRecord?> = _selectedPaper.asStateFlow()

    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog: StateFlow<Boolean> = _showImportDialog.asStateFlow()

    private val _showLogSheet = MutableStateFlow(false)
    val showLogSheet: StateFlow<Boolean> = _showLogSheet.asStateFlow()

    private val _showFolderDialog = MutableStateFlow(false)
    val showFolderDialog: StateFlow<Boolean> = _showFolderDialog.asStateFlow()

    // Filtered and searched papers flow
    val filteredPapers: StateFlow<List<PaperRecord>> = combine(
        repository.getAllPapers(),
        _filterStatus,
        _searchQuery
    ) { allPapers, filter, query ->
        allPapers.filter { paper ->
            val matchesFilter = when (filter) {
                "DOWNLOADED" -> paper.downloadStatus == "DOWNLOADED"
                "PAYWALLED" -> paper.downloadStatus == "PAYWALLED"
                "FAILED" -> paper.downloadStatus == "FAILED"
                "ACTIVE" -> paper.downloadStatus in listOf("PENDING", "RESOLVING", "DOWNLOADING")
                else -> true
            }

            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                paper.title.contains(query, ignoreCase = true) ||
                paper.doi.contains(query, ignoreCase = true) ||
                paper.authors.contains(query, ignoreCase = true) ||
                paper.journal.contains(query, ignoreCase = true)
            }

            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importContent(content: String, fileName: String) {
        val lower = fileName.lowercase()
        val isRis = lower.endsWith(".ris") || content.contains("TY  -") || content.contains("ER  -")
        val isCsv = lower.endsWith(".csv") || lower.endsWith(".tsv") || lower.endsWith(".txt")

        val batchId = java.util.UUID.randomUUID().toString()
        val parsedPapers = when {
            isRis -> DoiParser.parseRis(content, fileName, batchId)
            isCsv -> DoiParser.parseCsv(content, fileName, batchId)
            else -> DoiParser.extractDoisFromRawText(content, fileName, batchId)
        }

        repository.startBatchScan(parsedPapers, fileName)
        _showImportDialog.value = false
    }

    fun loadSample(index: Int) {
        if (index in SampleData.SAMPLES.indices) {
            val sample = SampleData.SAMPLES[index]
            importContent(sample.content, sample.name)
        }
    }

    fun setFilter(filter: String) {
        _filterStatus.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPaper(paper: PaperRecord?) {
        _selectedPaper.value = paper
    }

    fun setShowImportDialog(show: Boolean) {
        _showImportDialog.value = show
    }

    fun setShowLogSheet(show: Boolean) {
        _showLogSheet.value = show
    }

    fun setShowFolderDialog(show: Boolean) {
        _showFolderDialog.value = show
    }

    fun pauseScan() = repository.pauseScan()
    fun resumeScan() = repository.resumeScan()
    fun cancelScan() = repository.cancelScan()
    fun toggleSoundMuted() = repository.toggleSoundMuted()
    fun retryDoi(doi: String) = repository.retryDoi(doi)
    fun retryAllMissing() = repository.retryAllMissing()
    fun clearAll() = repository.clearAllRecords()
    fun setDesignatedFolder(file: File) = repository.setDesignatedFolder(file)

    fun generateManifestCsv(papers: List<PaperRecord>): String {
        val sb = StringBuilder()
        sb.append("DOI,Title,Authors,Journal,Year,OA_Status,Download_Status,File_Size_Bytes,Local_File_Path\n")
        for (p in papers) {
            val cleanTitle = "\"" + p.title.replace("\"", "\"\"") + "\""
            val cleanAuthors = "\"" + p.authors.replace("\"", "\"\"") + "\""
            val cleanJournal = "\"" + p.journal.replace("\"", "\"\"") + "\""
            sb.append("${p.doi},$cleanTitle,$cleanAuthors,$cleanJournal,${p.year ?: ""},${p.oaStatus},${p.downloadStatus},${p.fileSizeBytes},\"${p.localFilePath ?: ""}\"\n")
        }
        return sb.toString()
    }
}
