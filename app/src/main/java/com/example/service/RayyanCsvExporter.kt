package com.example.service

import com.example.model.PaperRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ExportScope(val label: String) {
    DOWNLOADED_ONLY("Downloaded Files Only"),
    ALL("All Scanned References"),
    MISSING("Missing / Paywalled Only")
}

enum class ExportFormat(val label: String, val description: String) {
    RAYYAN_ALL_FIELDS(
        label = "Rayyan + All Fields CSV",
        description = "All 14 standard Rayyan headers plus all 11 PaperFetch status, OA & local file columns (25 columns, lossless round-trip)."
    ),
    RAYYAN_STRICT(
        label = "Rayyan Strict CSV (14 Columns)",
        description = "Strictly standard Rayyan import headers only; PaperFetch OA & local file details are packed into the notes column."
    )
}

object RayyanCsvExporter {

    /**
     * Standard Rayyan CSV column headers (lowercase, matching Rayyan's official CSV import/export schema).
     */
    val RAYYAN_STANDARD_HEADERS = listOf(
        "key",
        "title",
        "authors",
        "journal",
        "issn",
        "volume",
        "issue",
        "pages",
        "year",
        "publisher",
        "url",
        "abstract",
        "doi",
        "notes"
    )

    /**
     * Additional PaperFetch-specific tracking & local file columns.
     */
    val PAPERFETCH_EXTRA_HEADERS = listOf(
        "oa_status",
        "is_oa",
        "license",
        "pdf_url",
        "download_status",
        "status_message",
        "local_file_path",
        "file_size_bytes",
        "downloaded_at",
        "source_file_name",
        "batch_id"
    )

    val ALL_HEADERS = RAYYAN_STANDARD_HEADERS + PAPERFETCH_EXTRA_HEADERS

    fun filterForScope(papers: List<PaperRecord>, scope: ExportScope): List<PaperRecord> {
        return when (scope) {
            ExportScope.DOWNLOADED_ONLY -> papers.filter { it.downloadStatus == "DOWNLOADED" }
            ExportScope.MISSING -> papers.filter { it.downloadStatus == "PAYWALLED" || it.downloadStatus == "FAILED" }
            ExportScope.ALL -> papers
        }
    }

    fun exportToCsv(
        papers: List<PaperRecord>,
        scope: ExportScope = ExportScope.DOWNLOADED_ONLY,
        format: ExportFormat = ExportFormat.RAYYAN_ALL_FIELDS
    ): String {
        val targetPapers = filterForScope(papers, scope)
        val headers = when (format) {
            ExportFormat.RAYYAN_ALL_FIELDS -> ALL_HEADERS
            ExportFormat.RAYYAN_STRICT -> RAYYAN_STANDARD_HEADERS
        }

        val sb = StringBuilder()
        sb.append(headers.joinToString(",") { escapeCsvField(it) }).append("\r\n")

        for (paper in targetPapers) {
            val rowValues = buildRow(paper, format)
            sb.append(rowValues.joinToString(",") { escapeCsvField(it) }).append("\r\n")
        }

        return sb.toString()
    }

    fun defaultFileName(scope: ExportScope): String {
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = when (scope) {
            ExportScope.DOWNLOADED_ONLY -> "downloaded_rayyan"
            ExportScope.MISSING -> "missing_rayyan"
            ExportScope.ALL -> "all_rayyan"
        }
        return "paperfetch_${suffix}_$dateStamp.csv"
    }

    fun writeCsvToFile(csvContent: String, destinationFile: File): File {
        destinationFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        destinationFile.writeText(csvContent, Charsets.UTF_8)
        return destinationFile
    }

    private fun buildRow(paper: PaperRecord, format: ExportFormat): List<String> {
        val key = paper.doi
        val title = paper.title.ifBlank { "DOI: ${paper.doi}" }
        val url = paper.url.ifBlank {
            paper.pdfUrl?.takeIf { it.isNotBlank() } ?: "https://doi.org/${paper.doi}"
        }
        val yearStr = paper.year?.toString() ?: ""
        val downloadedAtIso = paper.downloadedAt?.let { formatIsoTimestamp(it) } ?: ""

        val notesValue = when (format) {
            ExportFormat.RAYYAN_ALL_FIELDS -> paper.notes
            ExportFormat.RAYYAN_STRICT -> buildPackedNotes(paper, downloadedAtIso)
        }

        val rayyanColumns = listOf(
            key,
            title,
            paper.authors,
            paper.journal,
            paper.issn,
            paper.volume,
            paper.issue,
            paper.pages,
            yearStr,
            paper.publisher,
            url,
            paper.abstractText,
            paper.doi,
            notesValue
        )

        return when (format) {
            ExportFormat.RAYYAN_STRICT -> rayyanColumns
            ExportFormat.RAYYAN_ALL_FIELDS -> rayyanColumns + listOf(
                paper.oaStatus,
                paper.isOa.toString(),
                paper.license ?: "",
                paper.pdfUrl ?: "",
                paper.downloadStatus,
                paper.statusMessage,
                paper.localFilePath ?: "",
                paper.fileSizeBytes.toString(),
                downloadedAtIso,
                paper.sourceFileName,
                paper.batchId
            )
        }
    }

    private fun buildPackedNotes(paper: PaperRecord, downloadedAtIso: String): String {
        val existingParts = paper.notes
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("PAPERFETCH-") }
            .toMutableList()

        existingParts.add("PAPERFETCH-STATUS: ${paper.downloadStatus}")
        existingParts.add("PAPERFETCH-OA: ${paper.oaStatus} (is_oa=${paper.isOa})")
        if (!paper.license.isNullOrBlank()) existingParts.add("PAPERFETCH-LICENSE: ${paper.license}")
        if (!paper.pdfUrl.isNullOrBlank()) existingParts.add("PAPERFETCH-PDF-URL: ${paper.pdfUrl}")
        if (!paper.localFilePath.isNullOrBlank()) existingParts.add("PAPERFETCH-FILE: ${paper.localFilePath}")
        if (paper.fileSizeBytes > 0) existingParts.add("PAPERFETCH-SIZE: ${paper.fileSizeBytes}")
        if (downloadedAtIso.isNotBlank()) existingParts.add("PAPERFETCH-DOWNLOADED-AT: $downloadedAtIso")
        if (paper.statusMessage.isNotBlank()) existingParts.add("PAPERFETCH-MESSAGE: ${paper.statusMessage}")
        if (paper.sourceFileName.isNotBlank()) existingParts.add("PAPERFETCH-SOURCE: ${paper.sourceFileName}")
        if (paper.batchId.isNotBlank()) existingParts.add("PAPERFETCH-BATCH: ${paper.batchId}")

        return existingParts.joinToString(" | ")
    }

    fun formatIsoTimestamp(epochMillis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochMillis))
    }

    fun parseTimestamp(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        trimmed.toLongOrNull()?.let { return it }
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(trimmed)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun escapeCsvField(value: String): String {
        val normalized = value.replace("\r\n", "\n").replace("\r", "\n")
        val escaped = normalized.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
