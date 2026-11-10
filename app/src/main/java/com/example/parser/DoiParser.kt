package com.example.parser

import com.example.model.PaperRecord

/**
 * Regex-based parser for extracting unique Digital Object Identifiers (DOIs)
 * from imported CSV, RIS, and plain text files.
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
     * (Title, Authors, Journal, Year). Ensures all unique DOIs are included without duplicates.
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

        fun commitRecord() {
            val doi = currentDoi?.let { normalizeDoi(it) }
            if (!doi.isNullOrBlank() && seenDois.add(doi)) {
                records.add(
                    PaperRecord(
                        doi = doi,
                        title = currentTitle.trim(),
                        authors = currentAuthors.joinToString("; ").trim(),
                        journal = currentJournal.trim(),
                        year = currentYear,
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
                    "UR" -> {
                        if (currentDoi == null) {
                            val foundDoi = normalizeDoi(value)
                            if (foundDoi != null) currentDoi = foundDoi
                        }
                    }
                    "TI", "T1" -> {
                        if (currentTitle.isEmpty()) currentTitle = value
                    }
                    "AU", "A1" -> {
                        if (value.isNotEmpty()) currentAuthors.add(value)
                    }
                    "JO", "JF", "JA", "T2" -> {
                        if (currentJournal.isEmpty()) currentJournal = value
                    }
                    "PY", "Y1" -> {
                        val yearMatch = Regex("""\b(19|20)\d{2}\b""").find(value)
                        yearMatch?.let { currentYear = it.value.toIntOrNull() }
                    }
                    "ER" -> {
                        commitRecord()
                    }
                    else -> {
                        // Scan other fields (e.g. notes N1, abstract AB) if DOI not yet set for this entry
                        if (currentDoi == null) {
                            val foundDoi = normalizeDoi(value)
                            if (foundDoi != null) currentDoi = foundDoi
                        }
                    }
                }
            } else {
                // If there's an embedded DOI on unformatted lines
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
     * Parses a CSV or TSV file into PaperRecord objects, extracting DOIs and bibliographic metadata.
     * Identifies DOI columns, scans cells with regex, and guarantees all unique DOIs are extracted.
     */
    fun parseCsv(content: String, sourceFileName: String, batchId: String): List<PaperRecord> {
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // Detect delimiter (comma, semicolon, tab)
        val firstLine = lines.first()
        val delimiter = when {
            firstLine.count { it == '\t' } >= 2 -> '\t'
            firstLine.count { it == ';' } > firstLine.count { it == ',' } -> ';'
            else -> ','
        }

        val rows = lines.map { parseCsvLine(it, delimiter) }
        val headerRow = rows.firstOrNull() ?: return emptyList()

        var doiColIndex = -1
        var titleColIndex = -1
        var authorColIndex = -1
        var journalColIndex = -1
        var yearColIndex = -1

        for ((index, col) in headerRow.withIndex()) {
            val lower = col.lowercase().trim().replace("\"", "").replace("_", " ")
            when {
                lower == "doi" || lower.contains("doi") || lower.contains("digital object") -> {
                    if (doiColIndex == -1) doiColIndex = index
                }
                lower.contains("title") -> {
                    if (titleColIndex == -1) titleColIndex = index
                }
                lower.contains("author") -> {
                    if (authorColIndex == -1) authorColIndex = index
                }
                lower.contains("journal") || lower.contains("venue") || lower.contains("publisher") -> {
                    if (journalColIndex == -1) journalColIndex = index
                }
                lower.contains("year") || lower.contains("date") -> {
                    if (yearColIndex == -1) yearColIndex = index
                }
            }
        }

        val records = mutableListOf<PaperRecord>()
        val seenDois = mutableSetOf<String>()
        val startRowIndex = if (doiColIndex != -1) 1 else 0

        for (i in startRowIndex until rows.size) {
            val row = rows[i]
            if (row.isEmpty()) continue

            var doi: String? = null
            var title = ""
            var authors = ""
            var journal = ""
            var year: Int? = null

            if (doiColIndex != -1 && doiColIndex < row.size) {
                doi = normalizeDoi(row[doiColIndex])
            }

            // If header didn't point directly to a valid DOI, scan the row's columns
            if (doi == null) {
                for (cell in row) {
                    val found = normalizeDoi(cell)
                    if (found != null) {
                        doi = found
                        break
                    }
                }
            }

            if (doi != null && seenDois.add(doi)) {
                if (titleColIndex != -1 && titleColIndex < row.size) {
                    title = row[titleColIndex].trim().replace("\"", "")
                }
                if (authorColIndex != -1 && authorColIndex < row.size) {
                    authors = row[authorColIndex].trim().replace("\"", "")
                }
                if (journalColIndex != -1 && journalColIndex < row.size) {
                    journal = row[journalColIndex].trim().replace("\"", "")
                }
                if (yearColIndex != -1 && yearColIndex < row.size) {
                    val match = Regex("""\b(19|20)\d{2}\b""").find(row[yearColIndex])
                    year = match?.value?.toIntOrNull()
                }

                records.add(
                    PaperRecord(
                        doi = doi,
                        title = title,
                        authors = authors,
                        journal = journal,
                        year = year,
                        sourceFileName = sourceFileName,
                        batchId = batchId
                    )
                )
            }
        }

        // Catch any remaining unique DOIs found anywhere in the CSV text via regex
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

    /**
     * Internal helper to clean DOI suffixes, removing trailing punctuation and unbalanced parentheses.
     */
    private fun cleanDoiSuffix(rawDoi: String): String {
        var doi = rawDoi.trim()

        // Replace URL-encoded slash (%2F)
        if (doi.contains("%2F", ignoreCase = true)) {
            doi = doi.replace("%2F", "/", ignoreCase = true)
        }

        // Remove URL and prefix prefixes if still attached
        doi = doi.replaceFirst(
            Regex("""^(?:https?://(?:dx\.)?doi\.org/|doi:\s*)""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        // Remove trailing punctuation marks: .,;:?!'\"<>[]{}
        while (doi.isNotEmpty() && (doi.last() in ".,;:?!'\"<>[]{}")) {
            doi = doi.dropLast(1)
        }

        // Handle trailing parenthesis: strip if unbalanced
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

        return doi.lowercase()
    }

    /**
     * Robust CSV line splitter taking quoted fields into account.
     */
    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (char in line) {
            if (char == '\"') {
                inQuotes = !inQuotes
            } else if (char == delimiter && !inQuotes) {
                result.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(char)
            }
        }
        result.add(sb.toString().trim())
        return result
    }
}
