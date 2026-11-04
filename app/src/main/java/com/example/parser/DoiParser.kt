package com.example.parser

import com.example.model.PaperRecord

object DoiParser {

    private val DOI_REGEX = Regex("""10\.\d{4,9}/[-._;()/:A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

    data class ParsedEntry(
        val doi: String,
        val title: String = "",
        val authors: String = "",
        val journal: String = "",
        val year: Int? = null
    )

    fun normalizeDoi(raw: String): String? {
        val trimmed = raw.trim()
        val match = DOI_REGEX.find(trimmed) ?: return null
        var doi = match.value
        // Clean trailing punctuation
        doi = doi.trimEnd('.', ',', ';', ':', ')', ']', '}')
        return doi.lowercase()
    }

    fun parseRis(content: String, sourceFileName: String, batchId: String): List<PaperRecord> {
        val records = mutableListOf<PaperRecord>()
        val lines = content.lines()

        var currentDoi: String? = null
        var currentTitle = ""
        val currentAuthors = mutableListOf<String>()
        var currentJournal = ""
        var currentYear: Int? = null

        fun commitRecord() {
            val doi = currentDoi?.let { normalizeDoi(it) }
            if (!doi.isNullOrBlank()) {
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

        val risLineRegex = Regex("""^([A-Z0-9]{2})\s*-\s*(.*)$""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val match = risLineRegex.find(trimmed)
            if (match != null) {
                val tag = match.groupValues[1].uppercase()
                val value = match.groupValues[2].trim()

                when (tag) {
                    "DO", "M3" -> {
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

        // Fallback: If no structured DOIs were captured via tags, regex scan the entire content
        if (records.isEmpty()) {
            return extractDoisFromRawText(content, sourceFileName, batchId)
        }

        // Deduplicate by DOI
        return records.distinctBy { it.doi }
    }

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

            if (doi != null) {
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

        // If structured CSV parsing found nothing, fallback to raw text regex
        if (records.isEmpty()) {
            return extractDoisFromRawText(content, sourceFileName, batchId)
        }

        return records.distinctBy { it.doi }
    }

    fun extractDoisFromRawText(content: String, sourceFileName: String, batchId: String): List<PaperRecord> {
        val matches = DOI_REGEX.findAll(content)
        val list = mutableListOf<PaperRecord>()
        val seen = mutableSetOf<String>()

        for (match in matches) {
            val clean = normalizeDoi(match.value)
            if (clean != null && seen.add(clean)) {
                list.add(
                    PaperRecord(
                        doi = clean,
                        sourceFileName = sourceFileName,
                        batchId = batchId
                    )
                )
            }
        }
        return list
    }

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
