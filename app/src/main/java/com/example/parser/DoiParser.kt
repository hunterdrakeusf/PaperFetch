package com.example.parser

import com.example.model.PaperRecord
import com.example.service.RayyanCsvExporter

/**
 * Regex-based parser for extracting unique Digital Object Identifiers (DOIs)
 * and full bibliographic metadata from imported CSV (including Rayyan CSVs),
 * RIS, and plain text files.
 */
object DoiParser {

    /**
     * Regex pattern matching DOIs according to the International DOI Foundation standard.
     * Matches standard DOIs (10.XXXX/...) optionally prefixed with http(s)://doi.org/, dx.doi.org/, or doi:
     */
    private val DOI_PATTERN = Regex(
        """(?:https?://(?:dx\.)?doi\.org/|doi:\s*)?(10\.\d{4,9}/[^\s"<>]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Strict validation pattern for cleaned DOIs.
     */
    private val DOI_VALIDATION_REGEX = Regex(
        """^10\.\d{4,9}/[-._;()/:A-Za-z0-9]+$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * RIS tag line pattern: 2 alphanumeric characters, dash, and value.
     */
    private val RIS_LINE_REGEX = Regex(
        """^([A-Z0-9]{2})\s*-\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Validates if a string is a properly formatted DOI.
     */
    fun isValidDoi(doi: String): Boolean {
        return DOI_VALIDATION_REGEX.matches(doi.trim())
    }

    /**
     * Cleans and normalizes a candidate DOI string:
     * - Decodes URL-encoded characters (%2F -> /)
     * - Strips URL prefixes (https://doi.org/, http://dx.doi.org/, etc.) and 'doi:'
     * - Trims trailing sentence punctuation and closing brackets if unbalanced
     * - Converts to lowercase for consistent deduplication
     */
    fun normalizeDoi(raw: String): String? {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.contains("%2F", ignoreCase = true)) {
            trimmed = trimmed.replace("%2F", "/", ignoreCase = true).replace("%2f", "/", ignoreCase = true)
        }

        val match = DOI_PATTERN.find(trimmed) ?: return null
        val candidate = match.groupValues.getOrNull(1) ?: match.value
        val cleaned = cleanDoiSuffix(candidate)

        return if (isValidDoi(cleaned)) cleaned else null
    }

    /**
     * Extracts all unique DOIs from any raw text, CSV, or RIS content using regex.
     * Preserves order of first appearance and removes any duplicates.
     */
    fun extractUniqueDois(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val decodedText = if (text.contains("%2F", ignoreCase = true)) {
            text.replace("%2F", "/", ignoreCase = true).replace("%2f", "/", ignoreCase = true)
        } else text

        val uniqueDois = LinkedHashSet<String>()
        val matches = DOI_PATTERN.findAll(decodedText)

        for (match in matches) {
            val candidate = match.groupValues.getOrNull(1) ?: match.value
            val cleaned = cleanDoiSuffix(candidate)
            if (isValidDoi(cleaned)) {
                uniqueDois.add(cleaned)
            }
        }

        return uniqueDois.toList()
    }

    /**
     * Scans an imported RIS file and extracts all unique DOIs.
     */
    fun extractUniqueDoisFromRis(content: String): List<String> {
        val records = parseRis(content, sourceFileName = "", batchId = "")
        val fromRecords = records.map { it.doi }
        val fromText = extractUniqueDois(content)

        val uniqueSet = LinkedHashSet<String>()
        uniqueSet.addAll(fromRecords)
        uniqueSet.addAll(fromText)
        return uniqueSet.toList()
    }

    /**
     * Scans an imported CSV file and extracts all unique DOIs.
     */
    fun extractUniqueDoisFromCsv(content: String): List<String> {
        val records = parseCsv(content, sourceFileName = "", batchId = "")
        val fromRecords = records.map { it.doi }
        val fromText = extractUniqueDois(content)

        val uniqueSet = LinkedHashSet<String>()
        uniqueSet.addAll(fromRecords)
        uniqueSet.addAll(fromText)
        return uniqueSet.toList()
    }

    /**
     * Parses an RIS file into structured PaperRecord objects, extracting DOIs and bibliographic metadata
     * (Title, Authors, Journal, Year, Volume, Issue, Pages, ISSN, Publisher, Abstract, URL, Notes).
     */
    fun parseRis(content: String, sourceFileName: String, batchId: String): List<PaperRecord> {
        val records = mutableListOf<PaperRecord>()
        val seenDois = mutableSetOf<String>()
        val lines = content.lines()

        var currentDoi: String? = null
        var currentTitle = ""
        val currentAuthors = mutableListOf<String>()
        var currentJournal = ""
        var currentYear: Int? = null
        var currentVolume = ""
        var currentIssue = ""
        var startPage = ""
        var endPage = ""
        var currentIssn = ""
        var currentPublisher = ""
        var currentUrl = ""
        var currentAbstract = ""
        val currentNotes = mutableListOf<String>()

        fun commitRecord() {
            val doi = currentDoi?.let { normalizeDoi(it) }
            if (!doi.isNullOrBlank() && seenDois.add(doi)) {
                val pages = when {
                    startPage.isNotBlank() && endPage.isNotBlank() && startPage != endPage -> "$startPage-$endPage"
                    startPage.isNotBlank() -> startPage
                    else -> ""
                }
                records.add(
                    PaperRecord(
                        doi = doi,
                        title = currentTitle.trim(),
                        authors = currentAuthors.joinToString("; ").trim(),
                        journal = currentJournal.trim(),
                        year = currentYear,
                        volume = currentVolume.trim(),
                        issue = currentIssue.trim(),
                        pages = pages.trim(),
                        issn = currentIssn.trim(),
                        publisher = currentPublisher.trim(),
                        url = currentUrl.trim(),
                        abstractText = currentAbstract.trim(),
                        notes = currentNotes.joinToString(" | ").trim(),
                        sourceFileName = sourceFileName,
                        batchId = batchId
                    )
                )
            }
            currentDoi = null
            currentTitle = ""
            currentAuthors.clear()
            currentJournal = ""
            currentYear = null
            currentVolume = ""
            currentIssue = ""
            startPage = ""
            endPage = ""
            currentIssn = ""
            currentPublisher = ""
            currentUrl = ""
            currentAbstract = ""
            currentNotes.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val match = RIS_LINE_REGEX.find(trimmed)
            if (match != null) {
                val tag = match.groupValues[1].uppercase()
                val value = match.groupValues[2].trim()

                when (tag) {
                    "DO", "M3", "L3" -> {
                        val foundDoi = normalizeDoi(value)
                        if (foundDoi != null) currentDoi = foundDoi
                    }
                    "UR", "L1", "L2" -> {
                        if (currentUrl.isEmpty()) currentUrl = value
                        if (currentDoi == null) {
                            val foundDoi = normalizeDoi(value)
                            if (foundDoi != null) currentDoi = foundDoi
                        }
                    }
                    "TI", "T1" -> if (currentTitle.isEmpty()) currentTitle = value
                    "AU", "A1" -> if (value.isNotEmpty()) currentAuthors.add(value)
                    "JO", "JF", "JA", "T2" -> if (currentJournal.isEmpty()) currentJournal = value
                    "VL" -> if (currentVolume.isEmpty()) currentVolume = value
                    "IS" -> if (currentIssue.isEmpty()) currentIssue = value
                    "SP" -> if (startPage.isEmpty()) startPage = value
                    "EP" -> if (endPage.isEmpty()) endPage = value
                    "SN" -> if (currentIssn.isEmpty()) currentIssn = value
                    "PB" -> if (currentPublisher.isEmpty()) currentPublisher = value
                    "AB", "N2" -> if (currentAbstract.isEmpty()) currentAbstract = value
                    "N1" -> if (value.isNotEmpty()) currentNotes.add(value)
                    "PY", "Y1" -> {
                        val yearMatch = Regex("""\b(19|20)\d{2}\b""").find(value)
                        yearMatch?.let { currentYear = it.value.toIntOrNull() }
                    }
                    "ER" -> commitRecord()
                    else -> {
                        if (currentDoi == null) {
                            val foundDoi = normalizeDoi(value)
                            if (foundDoi != null) currentDoi = foundDoi
                        }
                    }
                }
            } else {
                if (currentDoi == null) {
                    val fallback = normalizeDoi(trimmed)
                    if (fallback != null) currentDoi = fallback
                }
            }
        }

        commitRecord() // In case file didn't end with ER

        // Ensure any remaining DOIs found via regex across the file are included as unique records
        val allFileDois = extractUniqueDois(content)
        for (doi in allFileDois) {
            if (seenDois.add(doi)) {
                records.add(
                    PaperRecord(
                        doi = doi,
                        sourceFileName = sourceFileName,
                        batchId = batchId
                    )
                )
            }
        }

        return records
    }

    /**
     * Parses a CSV or TSV file (including Rayyan CSV exports and PaperFetch CSV exports)
     * into PaperRecord objects using a full RFC 4180 multi-line CSV parser.
     */
    fun parseCsv(content: String, sourceFileName: String, batchId: String): List<PaperRecord> {
        val cleanContent = content.removePrefix("\uFEFF").trim()
        if (cleanContent.isEmpty()) return emptyList()

        // Detect delimiter (comma, semicolon, tab) from the header line
        val firstLine = cleanContent.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = when {
            firstLine.count { it == '\t' } >= 2 -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }

        val rows = parseCsvRows(cleanContent, delimiter)
        if (rows.isEmpty()) return emptyList()

        val headerRow = rows.first()
        val colMap = mutableMapOf<String, Int>()
        var hasHeader = false

        headerRow.forEachIndexed { index, rawHeader ->
            val normalizedHeader = rawHeader.lowercase().trim()
                .replace("\"", "")
                .replace("_", " ")
                .replace("-", " ")
                .replace(Regex("\\s+"), " ")

            when {
                normalizedHeader == "doi" || normalizedHeader == "digital object identifier" ||
                    (normalizedHeader.contains("doi") && !colMap.containsKey("doi")) -> {
                    colMap.putIfAbsent("doi", index)
                    hasHeader = true
                }
                normalizedHeader == "key" || normalizedHeader == "citation key" -> {
                    colMap.putIfAbsent("key", index)
                    hasHeader = true
                }
                normalizedHeader == "title" || normalizedHeader == "article title" || normalizedHeader == "document title" ||
                    (normalizedHeader.contains("title") && !colMap.containsKey("title")) -> {
                    colMap.putIfAbsent("title", index)
                    hasHeader = true
                }
                normalizedHeader == "authors" || normalizedHeader == "author" || normalizedHeader == "author list" || normalizedHeader == "creators" ||
                    (normalizedHeader.contains("author") && !colMap.containsKey("authors")) -> {
                    colMap.putIfAbsent("authors", index)
                    hasHeader = true
                }
                normalizedHeader == "journal" || normalizedHeader == "publication name" || normalizedHeader == "periodical" || normalizedHeader == "venue" || normalizedHeader == "source" ||
                    ((normalizedHeader.contains("journal") || normalizedHeader.contains("venue")) && !colMap.containsKey("journal")) -> {
                    colMap.putIfAbsent("journal", index)
                    hasHeader = true
                }
                normalizedHeader == "year" || normalizedHeader == "publication year" || normalizedHeader == "pub year" ||
                    (normalizedHeader == "date" && !colMap.containsKey("year")) -> {
                    colMap.putIfAbsent("year", index)
                    hasHeader = true
                }
                normalizedHeader == "volume" || normalizedHeader == "vol" -> {
                    colMap.putIfAbsent("volume", index)
                    hasHeader = true
                }
                normalizedHeader == "issue" || normalizedHeader == "number" -> {
                    colMap.putIfAbsent("issue", index)
                    hasHeader = true
                }
                normalizedHeader == "pages" || normalizedHeader == "page range" || normalizedHeader == "page" -> {
                    colMap.putIfAbsent("pages", index)
                    hasHeader = true
                }
                normalizedHeader == "issn" -> {
                    colMap.putIfAbsent("issn", index)
                    hasHeader = true
                }
                normalizedHeader == "publisher" -> {
                    colMap.putIfAbsent("publisher", index)
                    hasHeader = true
                }
                normalizedHeader == "url" || normalizedHeader == "link" || normalizedHeader == "web link" -> {
                    colMap.putIfAbsent("url", index)
                    hasHeader = true
                }
                normalizedHeader == "abstract" || normalizedHeader == "summary" || normalizedHeader == "description" -> {
                    colMap.putIfAbsent("abstract", index)
                    hasHeader = true
                }
                normalizedHeader == "notes" || normalizedHeader == "comments" -> {
                    colMap.putIfAbsent("notes", index)
                    hasHeader = true
                }
                normalizedHeader == "oa status" -> {
                    colMap.putIfAbsent("oa_status", index)
                    hasHeader = true
                }
                normalizedHeader == "is oa" -> {
                    colMap.putIfAbsent("is_oa", index)
                    hasHeader = true
                }
                normalizedHeader == "license" -> {
                    colMap.putIfAbsent("license", index)
                    hasHeader = true
                }
                normalizedHeader == "pdf url" -> {
                    colMap.putIfAbsent("pdf_url", index)
                    hasHeader = true
                }
                normalizedHeader == "download status" -> {
                    colMap.putIfAbsent("download_status", index)
                    hasHeader = true
                }
                normalizedHeader == "status message" -> {
                    colMap.putIfAbsent("status_message", index)
                    hasHeader = true
                }
                normalizedHeader == "local file path" -> {
                    colMap.putIfAbsent("local_file_path", index)
                    hasHeader = true
                }
                normalizedHeader == "file size bytes" -> {
                    colMap.putIfAbsent("file_size_bytes", index)
                    hasHeader = true
                }
                normalizedHeader == "downloaded at" -> {
                    colMap.putIfAbsent("downloaded_at", index)
                    hasHeader = true
                }
                normalizedHeader == "source file name" -> {
                    colMap.putIfAbsent("source_file_name", index)
                    hasHeader = true
                }
                normalizedHeader == "batch id" -> {
                    colMap.putIfAbsent("batch_id", index)
                    hasHeader = true
                }
            }
        }

        val records = mutableListOf<PaperRecord>()
        val seenDois = mutableSetOf<String>()
        val startRowIndex = if (hasHeader) 1 else 0

        fun cell(row: List<String>, colName: String): String {
            val idx = colMap[colName] ?: return ""
            return if (idx in row.indices) row[idx].trim() else ""
        }

        for (i in startRowIndex until rows.size) {
            val row = rows[i]
            if (row.isEmpty() || row.all { it.isBlank() }) continue

            var doi = cell(row, "doi").takeIf { it.isNotBlank() }?.let { normalizeDoi(it) }
            if (doi == null) {
                doi = cell(row, "key").takeIf { it.isNotBlank() }?.let { normalizeDoi(it) }
            }
            if (doi == null) {
                doi = cell(row, "url").takeIf { it.isNotBlank() }?.let { normalizeDoi(it) }
            }
            if (doi == null) {
                for (c in row) {
                    val found = normalizeDoi(c)
                    if (found != null) {
                        doi = found
                        break
                    }
                }
            }

            if (doi != null && seenDois.add(doi)) {
                val title = cell(row, "title")
                val authors = cell(row, "authors")
                val journal = cell(row, "journal")
                val yearMatch = Regex("""\b(19|20)\d{2}\b""").find(cell(row, "year"))
                val year = yearMatch?.value?.toIntOrNull()
                val volume = cell(row, "volume")
                val issue = cell(row, "issue")
                val pages = cell(row, "pages")
                val issn = cell(row, "issn")
                val publisher = cell(row, "publisher")
                val url = cell(row, "url")
                val abstractText = cell(row, "abstract")
                val rawNotes = cell(row, "notes")

                // Unpack structured PaperFetch tags from notes if present (e.g. Rayyan Strict CSV re-import)
                val packed = unpackNotesMetadata(rawNotes)

                val oaStatus = cell(row, "oa_status").ifBlank { packed.oaStatus ?: "unknown" }
                val isOaRaw = cell(row, "is_oa")
                val isOa = when {
                    isOaRaw.equals("true", ignoreCase = true) -> true
                    isOaRaw.equals("false", ignoreCase = true) -> false
                    packed.isOa != null -> packed.isOa
                    else -> false
                }
                val license = cell(row, "license").ifBlank { packed.license }?.takeIf { it.isNotBlank() }
                val pdfUrl = cell(row, "pdf_url").ifBlank { packed.pdfUrl }?.takeIf { it.isNotBlank() }
                val downloadStatus = cell(row, "download_status").ifBlank { packed.downloadStatus ?: "PENDING" }
                val statusMessage = cell(row, "status_message").ifBlank { packed.statusMessage ?: "Queued" }
                val localFilePath = cell(row, "local_file_path").ifBlank { packed.localFilePath }?.takeIf { it.isNotBlank() }
                val fileSizeBytes = cell(row, "file_size_bytes").toLongOrNull() ?: packed.fileSizeBytes ?: 0L
                val downloadedAt = RayyanCsvExporter.parseTimestamp(cell(row, "downloaded_at")) ?: packed.downloadedAt
                val importedSourceName = cell(row, "source_file_name").ifBlank { packed.sourceFileName ?: sourceFileName }
                val importedBatchId = cell(row, "batch_id").ifBlank { packed.batchId ?: batchId }

                records.add(
                    PaperRecord(
                        doi = doi,
                        title = title,
                        authors = authors,
                        journal = journal,
                        year = year,
                        volume = volume,
                        issue = issue,
                        pages = pages,
                        issn = issn,
                        publisher = publisher,
                        url = url,
                        abstractText = abstractText,
                        notes = packed.cleanNotes,
                        oaStatus = oaStatus,
                        isOa = isOa,
                        license = license,
                        pdfUrl = pdfUrl,
                        downloadStatus = downloadStatus,
                        statusMessage = statusMessage,
                        localFilePath = localFilePath,
                        fileSizeBytes = fileSizeBytes,
                        downloadedAt = downloadedAt,
                        sourceFileName = importedSourceName,
                        batchId = importedBatchId
                    )
                )
            }
        }

        // Only fall back to raw-text regex extraction if no structured rows were parsed
        if (records.isEmpty()) {
            val allFileDois = extractUniqueDois(cleanContent)
            for (doi in allFileDois) {
                if (seenDois.add(doi)) {
                    records.add(
                        PaperRecord(
                            doi = doi,
                            sourceFileName = sourceFileName,
                            batchId = batchId
                        )
                    )
                }
            }
        }

        return records
    }

    /**
     * Extracts unique DOIs from raw text and wraps them in PaperRecord instances.
     */
    fun extractDoisFromRawText(content: String, sourceFileName: String, batchId: String): List<PaperRecord> {
        val uniqueDois = extractUniqueDois(content)
        return uniqueDois.map { doi ->
            PaperRecord(
                doi = doi,
                sourceFileName = sourceFileName,
                batchId = batchId
            )
        }
    }

    private data class UnpackedNotes(
        val cleanNotes: String,
        val oaStatus: String? = null,
        val isOa: Boolean? = null,
        val license: String? = null,
        val pdfUrl: String? = null,
        val downloadStatus: String? = null,
        val statusMessage: String? = null,
        val localFilePath: String? = null,
        val fileSizeBytes: Long? = null,
        val downloadedAt: Long? = null,
        val sourceFileName: String? = null,
        val batchId: String? = null
    )

    private fun unpackNotesMetadata(rawNotes: String): UnpackedNotes {
        if (rawNotes.isBlank() || !rawNotes.contains("PAPERFETCH-")) {
            return UnpackedNotes(cleanNotes = rawNotes.trim())
        }
        val cleanParts = mutableListOf<String>()
        var oaStatus: String? = null
        var isOa: Boolean? = null
        var license: String? = null
        var pdfUrl: String? = null
        var downloadStatus: String? = null
        var statusMessage: String? = null
        var localFilePath: String? = null
        var fileSizeBytes: Long? = null
        var downloadedAt: Long? = null
        var sourceFileName: String? = null
        var batchId: String? = null

        for (part in rawNotes.split("|")) {
            val trimmed = part.trim()
            when {
                trimmed.startsWith("PAPERFETCH-STATUS:") -> {
                    downloadStatus = trimmed.removePrefix("PAPERFETCH-STATUS:").trim()
                }
                trimmed.startsWith("PAPERFETCH-OA:") -> {
                    val value = trimmed.removePrefix("PAPERFETCH-OA:").trim()
                    oaStatus = value.substringBefore(" ").trim()
                    if (value.contains("is_oa=true")) isOa = true
                    else if (value.contains("is_oa=false")) isOa = false
                }
                trimmed.startsWith("PAPERFETCH-LICENSE:") -> {
                    license = trimmed.removePrefix("PAPERFETCH-LICENSE:").trim()
                }
                trimmed.startsWith("PAPERFETCH-PDF-URL:") -> {
                    pdfUrl = trimmed.removePrefix("PAPERFETCH-PDF-URL:").trim()
                }
                trimmed.startsWith("PAPERFETCH-FILE:") -> {
                    localFilePath = trimmed.removePrefix("PAPERFETCH-FILE:").trim()
                }
                trimmed.startsWith("PAPERFETCH-SIZE:") -> {
                    fileSizeBytes = trimmed.removePrefix("PAPERFETCH-SIZE:").trim().toLongOrNull()
                }
                trimmed.startsWith("PAPERFETCH-DOWNLOADED-AT:") -> {
                    downloadedAt = RayyanCsvExporter.parseTimestamp(
                        trimmed.removePrefix("PAPERFETCH-DOWNLOADED-AT:").trim()
                    )
                }
                trimmed.startsWith("PAPERFETCH-MESSAGE:") -> {
                    statusMessage = trimmed.removePrefix("PAPERFETCH-MESSAGE:").trim()
                }
                trimmed.startsWith("PAPERFETCH-SOURCE:") -> {
                    sourceFileName = trimmed.removePrefix("PAPERFETCH-SOURCE:").trim()
                }
                trimmed.startsWith("PAPERFETCH-BATCH:") -> {
                    batchId = trimmed.removePrefix("PAPERFETCH-BATCH:").trim()
                }
                trimmed.isNotBlank() -> cleanParts.add(trimmed)
            }
        }

        return UnpackedNotes(
            cleanNotes = cleanParts.joinToString(" | "),
            oaStatus = oaStatus,
            isOa = isOa,
            license = license,
            pdfUrl = pdfUrl,
            downloadStatus = downloadStatus,
            statusMessage = statusMessage,
            localFilePath = localFilePath,
            fileSizeBytes = fileSizeBytes,
            downloadedAt = downloadedAt,
            sourceFileName = sourceFileName,
            batchId = batchId
        )
    }

    /**
     * Internal helper to clean DOI suffixes, removing trailing punctuation and unbalanced parentheses.
     */
    private fun cleanDoiSuffix(rawDoi: String): String {
        var doi = rawDoi.trim()

        if (doi.contains("%2F", ignoreCase = true)) {
            doi = doi.replace("%2F", "/", ignoreCase = true)
        }

        doi = doi.replaceFirst(
            Regex("""^(?:https?://(?:dx\.)?doi\.org/|doi:\s*)""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        while (doi.isNotEmpty() && (doi.last() in ".,;:?!'\"<>[]{}")) {
            doi = doi.dropLast(1)
        }

        while (doi.endsWith(")")) {
            val openCount = doi.count { it == '(' }
            val closeCount = doi.count { it == ')' }
            if (closeCount > openCount) {
                doi = doi.dropLast(1)
                while (doi.isNotEmpty() && (doi.last() in ".,;:?!'\"<>[]{}")) {
                    doi = doi.dropLast(1)
                }
            } else {
                break
            }
        }

        // Strip web/file extensions appended to DOIs in publisher URLs (e.g., .../10.1186/...pdf)
        doi = doi.replaceFirst(
            Regex("""(?:\.pdf|\.html?|\.xml|/pdf|/full)$""", RegexOption.IGNORE_CASE),
            ""
        )

        return doi.lowercase()
    }

    /**
     * RFC 4180 multi-line CSV parser handling quoted newlines, escaped quotes (""), and CRLF/LF line endings.
     */
    fun parseCsvRows(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentCell = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = content.length

        while (i < len) {
            val c = content[i]
            if (inQuotes) {
                if (c == '"') {
                    // Check for escaped double quote ""
                    if (i + 1 < len && content[i + 1] == '"') {
                        currentCell.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentCell.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        currentRow.add(currentCell.toString().trim())
                        currentCell.clear()
                    }
                    '\r' -> {
                        if (i + 1 < len && content[i + 1] == '\n') {
                            i++
                        }
                        currentRow.add(currentCell.toString().trim())
                        currentCell.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    '\n' -> {
                        currentRow.add(currentCell.toString().trim())
                        currentCell.clear()
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                    }
                    else -> currentCell.append(c)
                }
            }
            i++
        }

        if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentCell.toString().trim())
            rows.add(currentRow)
        }

        return rows
    }
}
