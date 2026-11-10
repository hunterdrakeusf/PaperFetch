package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.PaperRecord
import com.example.ui.PaperViewModel
import com.example.ui.components.DashboardCards
import com.example.ui.components.FolderDialog
import com.example.ui.components.ImportDialog
import com.example.ui.components.PaperCard
import com.example.ui.components.PaperDetailSheet
import com.example.ui.components.ScanLogSheet
import com.example.ui.components.ScanProgressBar
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OceanNavy
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealAccentDark
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: PaperViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                PaperFetchApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperFetchApp(viewModel: PaperViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val papers by viewModel.filteredPapers.collectAsState()
    val stats by viewModel.dashboardStats.collectAsState()
    val progressState by viewModel.scanProgress.collectAsState()
    val logs by viewModel.scanLogs.collectAsState()
    val currentFolder by viewModel.designatedFolder.collectAsState()
    val activeFilter by viewModel.filterStatus.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isMuted by viewModel.isSoundMuted.collectAsState()
    val selectedPaper by viewModel.selectedPaper.collectAsState()
    val showImportDialog by viewModel.showImportDialog.collectAsState()
    val showLogSheet by viewModel.showLogSheet.collectAsState()
    val showFolderDialog by viewModel.showFolderDialog.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val logSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Document Picker launcher for CSV / RIS
    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val fileName = getFileName(context, uri) ?: "imported_references.csv"
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotBlank()) {
                    viewModel.importContent(content, fileName)
                    Toast.makeText(context, "Scanning $fileName...", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Selected file is empty", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Storage Access Framework Folder Picker launcher
    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Designated folder set to selected storage", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(TealAccentDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "PaperFetch",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            )
                            Text(
                                text = "DOI Open-Access Harvester",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Jeopardy Theme Sound Toggle
                    IconButton(
                        onClick = { viewModel.toggleSoundMuted() },
                        modifier = Modifier.testTag("action_sound_toggle")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.MusicNote,
                            contentDescription = if (isMuted) "Unmute Jeopardy Theme" else "Mute Jeopardy Theme",
                            tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else TealAccentDark
                        )
                    }

                    // Designated Folder button
                    IconButton(
                        onClick = { viewModel.setShowFolderDialog(true) },
                        modifier = Modifier.testTag("action_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Designated Folder",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // More actions menu
                    Box {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.testTag("action_overflow_menu")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Retry Missing DOIs") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.retryAllMissing()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Export Manifest CSV") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    exportManifest(context, viewModel.generateManifestCsv(papers))
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Clear Library") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showClearConfirmDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.setShowImportDialog(true) },
                icon = {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                },
                text = { Text("Scan File / DOIs", fontWeight = FontWeight.Bold) },
                containerColor = TealAccentDark,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_import_scan")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // 1. Dashboard summary cards
            item(key = "dashboard_cards") {
                DashboardCards(
                    stats = stats,
                    activeFilter = activeFilter,
                    onFilterSelected = { viewModel.setFilter(it) }
                )
            }

            // 2. Real-time scan progress bar
            item(key = "scan_progress") {
                ScanProgressBar(
                    progressState = progressState,
                    isMuted = isMuted,
                    onToggleMute = { viewModel.toggleSoundMuted() },
                    onPause = { viewModel.pauseScan() },
                    onResume = { viewModel.resumeScan() },
                    onCancel = { viewModel.cancelScan() },
                    onViewLogs = { viewModel.setShowLogSheet(true) }
                )
            }

            // 3. Filter tabs & search row
            item(key = "filter_search_row") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search by title, author, or DOI...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("search_papers_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "ALL" to "All (${stats.totalScanned})",
                            "DOWNLOADED" to "Downloaded (${stats.downloadedCount})",
                            "PAYWALLED" to "Missing (${stats.paywalledCount})"
                        ).forEach { (filterKey, label) ->
                            FilterChip(
                                selected = activeFilter == filterKey,
                                onClick = { viewModel.setFilter(filterKey) },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("filter_chip_$filterKey")
                            )
                        }
                    }
                }
            }

            // 4. Papers List / Empty State
            if (papers.isEmpty()) {
                item(key = "empty_state") {
                    EmptyPapersState(
                        hasTotalPapers = stats.totalScanned > 0,
                        onOpenImportDialog = { viewModel.setShowImportDialog(true) },
                        onLoadSample = { viewModel.loadSample(0) },
                        onPickFile = {
                            pickDocumentLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/x-research-info-systems",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
            } else {
                items(papers, key = { it.doi }) { paper ->
                    PaperCard(
                        paper = paper,
                        onOpenPdf = { openPdfFile(context, it) },
                        onOpenWeb = { openWebUrl(context, it) },
                        onRetry = { viewModel.retryDoi(it) },
                        onDetails = { viewModel.selectPaper(it) }
                    )
                }
            }
        }
    }

    // Modal Bottom Sheets & Dialogs
    if (selectedPaper != null) {
        PaperDetailSheet(
            paper = selectedPaper,
            sheetState = detailSheetState,
            onDismiss = {
                coroutineScope.launch { detailSheetState.hide() }
                viewModel.selectPaper(null)
            },
            onOpenPdf = { openPdfFile(context, it) },
            onSharePdf = { sharePdfFile(context, it) },
            onOpenWeb = { openWebUrl(context, it) },
            onRetry = { viewModel.retryDoi(it) }
        )
    }

    if (showLogSheet) {
        ScanLogSheet(
            logs = logs,
            sheetState = logSheetState,
            onDismiss = {
                coroutineScope.launch { logSheetState.hide() }
                viewModel.setShowLogSheet(false)
            }
        )
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { viewModel.setShowImportDialog(false) },
            onPickFileFromDevice = {
                viewModel.setShowImportDialog(false)
                pickDocumentLauncher.launch(
                    arrayOf(
                        "text/csv",
                        "text/comma-separated-values",
                        "text/plain",
                        "application/x-research-info-systems",
                        "*/*"
                    )
                )
            },
            onLoadSample = { index ->
                viewModel.loadSample(index)
            },
            onImportText = { content, fileName ->
                viewModel.importContent(content, fileName)
            }
        )
    }

    if (showFolderDialog) {
        FolderDialog(
            currentFolder = currentFolder,
            onDismiss = { viewModel.setShowFolderDialog(false) },
            onChooseFolderSAF = {
                pickFolderLauncher.launch(null)
            },
            onResetDefaultFolder = {
                val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val dir = if (external != null) File(external, "PaperFetch_PDFs") else File(context.filesDir, "PaperFetch_PDFs")
                viewModel.setDesignatedFolder(dir)
                Toast.makeText(context, "Reset to default app downloads folder", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Reference Library?") },
            text = { Text("This will remove all parsed paper records and scan history from the dashboard. Downloaded PDF files will remain in your designated folder.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyPapersState(
    hasTotalPapers: Boolean,
    onOpenImportDialog: () -> Unit,
    onLoadSample: () -> Unit,
    onPickFile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (hasTotalPapers) "No Papers Matching Filter" else "No References Scanned Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasTotalPapers) {
                    "Try changing the filter or search keywords to view other scanned papers."
                } else {
                    "Import a CSV or RIS file with DOI numbers to automatically resolve and download free legal open-access research PDFs."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (!hasTotalPapers) {
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onLoadSample,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccentDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("quick_load_sample_button")
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Sample Dataset (8 DOIs)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("quick_pick_file_button")
                ) {
                    Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select .CSV or .RIS from Device")
                }
            }
        }
    }
}

// Helper functions for Intents and FileProvider
private fun openPdfFile(context: Context, paper: PaperRecord) {
    val path = paper.localFilePath
    if (path.isNullOrBlank()) {
        Toast.makeText(context, "PDF file path not found", Toast.LENGTH_SHORT).show()
        return
    }

    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "PDF file does not exist on local storage", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open PDF: ${paper.title}"))
    } catch (e: Exception) {
        Toast.makeText(context, "No PDF viewer app found on device: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun sharePdfFile(context: Context, paper: PaperRecord) {
    val path = paper.localFilePath ?: return
    val file = File(path)
    if (!file.exists()) return

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, paper.title)
            putExtra(Intent.EXTRA_TEXT, "Research Paper: ${paper.title}\nDOI: https://doi.org/${paper.doi}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Paper PDF"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openWebUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportManifest(context: Context, csvData: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "PaperFetch Scan Report")
            putExtra(Intent.EXTRA_TEXT, csvData)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export DOI Scan Manifest"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}
