package com.example

import com.example.model.PaperRecord
import com.example.parser.DoiParser
import com.example.service.ExportFormat
import com.example.service.ExportScope
import com.example.service.RayyanCsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testDoiNormalization() {
    assertEquals("10.1371/journal.pone.0245463", DoiParser.normalizeDoi("https://doi.org/10.1371/journal.pone.0245463"))
    assertEquals("10.1038/s41586-020-2012-7", DoiParser.normalizeDoi("doi:10.1038/s41586-020-2012-7."))
    assertEquals("10.1145/3065386", DoiParser.normalizeDoi("  10.1145/3065386, "))
    assertEquals("10.1016/j.cell.2019.02.007", DoiParser.normalizeDoi("https://doi.org/10.1016%2Fj.cell.2019.02.007"))
  }

  @Test
  fun testExtractUniqueDois_DeduplicationAndCleaning() {
    val messyText = """
      Check these papers: (doi: 10.1038/s41586-020-2012-7).
      Duplicate: https://doi.org/10.1038/s41586-020-2012-7
      Another paper: 10.1371/journal.pone.0245463; and 10.1186/s12859-020-03666-4!
      Duplicate again: 10.1371/journal.pone.0245463
      URL encoded: https://doi.org/10.1016%2Fj.cell.2019.02.007
    """.trimIndent()

    val unique = DoiParser.extractUniqueDois(messyText)
    assertEquals(4, unique.size)
    assertEquals("10.1038/s41586-020-2012-7", unique[0])
    assertEquals("10.1371/journal.pone.0245463", unique[1])
    assertEquals("10.1186/s12859-020-03666-4", unique[2])
    assertEquals("10.1016/j.cell.2019.02.007", unique[3])
  }

  @Test
  fun testExtractUniqueDoisFromCsv() {
    val csvWithDuplicates = """
      Title,DOI,Alt_Link,Notes
      "Paper One","10.1371/journal.pone.0245463","https://doi.org/10.1371/journal.pone.0245463","First note"
      "Paper Two","10.1186/s12859-020-03666-4","","See also 10.1371/journal.pone.0245463"
      "Paper Three","","https://doi.org/10.1038/s41467-020-19818-2","Embedded 10.1186/s12859-020-03666-4"
    """.trimIndent()

    val uniqueDois = DoiParser.extractUniqueDoisFromCsv(csvWithDuplicates)
    assertEquals(3, uniqueDois.size)
    assertTrue(uniqueDois.contains("10.1371/journal.pone.0245463"))
    assertTrue(uniqueDois.contains("10.1186/s12859-020-03666-4"))
    assertTrue(uniqueDois.contains("10.1038/s41467-020-19818-2"))
  }

  @Test
  fun testExtractUniqueDoisFromRis() {
    val risWithDuplicates = """
      TY  - JOUR
      TI  - Paper One
      DO  - 10.1371/journal.pone.0245463
      UR  - https://doi.org/10.1371/journal.pone.0245463
      ER  - 

      TY  - JOUR
      TI  - Paper Two
      M3  - 10.1186/s12859-020-03666-4
      N1  - Duplicate reference 10.1371/journal.pone.0245463
      ER  - 

      TY  - JOUR
      TI  - Paper Three (duplicate of One)
      DO  - 10.1371/journal.pone.0245463
      ER  - 
    """.trimIndent()

    val uniqueDois = DoiParser.extractUniqueDoisFromRis(risWithDuplicates)
    assertEquals(2, uniqueDois.size)
    assertEquals("10.1371/journal.pone.0245463", uniqueDois[0])
    assertEquals("10.1186/s12859-020-03666-4", uniqueDois[1])
  }

  @Test
  fun testRisParsing() {
    val sampleRis = """
      TY  - JOUR
      TI  - Deep learning for genomics
      AU  - Smith, John
      JO  - PLOS ONE
      VL  - 16
      IS  - 4
      SP  - 101
      EP  - 118
      SN  - 1932-6203
      PB  - Public Library of Science
      PY  - 2021
      DO  - 10.1371/journal.pone.0245463
      AB  - Multi-omics deep learning summary.
      ER  - 

      TY  - JOUR
      TI  - Fast sequence alignment
      DO  - 10.1186/s12859-020-03666-4
      ER  - 
    """.trimIndent()

    val records = DoiParser.parseRis(sampleRis, "test.ris", "b1")
    assertEquals(2, records.size)
    assertEquals("10.1371/journal.pone.0245463", records[0].doi)
    assertEquals("Deep learning for genomics", records[0].title)
    assertEquals("Smith, John", records[0].authors)
    assertEquals(2021, records[0].year)
    assertEquals("16", records[0].volume)
    assertEquals("4", records[0].issue)
    assertEquals("101-118", records[0].pages)
    assertEquals("1932-6203", records[0].issn)
    assertEquals("Public Library of Science", records[0].publisher)
    assertEquals("Multi-omics deep learning summary.", records[0].abstractText)
    assertEquals("10.1186/s12859-020-03666-4", records[1].doi)
  }

  @Test
  fun testCsvParsing() {
    val sampleCsv = """
      Title,Year,DOI
      "Attention Is All You Need",2017,"10.48550/arxiv.1706.03762"
      "Language Models",2020,"10.48550/arxiv.2005.14165"
    """.trimIndent()

    val records = DoiParser.parseCsv(sampleCsv, "test.csv", "b2")
    assertEquals(2, records.size)
    assertEquals("10.48550/arxiv.1706.03762", records[0].doi)
    assertEquals("Attention Is All You Need", records[0].title)
  }

  @Test
  fun testDoiValidation() {
    assertTrue(DoiParser.isValidDoi("10.1371/journal.pone.0245463"))
    assertTrue(DoiParser.isValidDoi("10.1038/s41586-020-2012-7"))
    assertTrue(DoiParser.isValidDoi("10.48550/arxiv.1706.03762"))
    assertFalse(DoiParser.isValidDoi("not_a_doi"))
    assertFalse(DoiParser.isValidDoi("10.123")) // missing slash & suffix
    assertFalse(DoiParser.isValidDoi("11.1234/abc")) // invalid prefix
  }

  @Test
  fun testRayyanExport_DownloadedOnlyAndAllFieldsRoundTrip() {
    val downloadedPaper = PaperRecord(
      doi = "10.1371/journal.pone.0245463",
      title = "Deep \"Genomic\" Learning, Part 1",
      authors = "Smith, John; Doe, Jane",
      journal = "PLOS ONE",
      year = 2021,
      volume = "16",
      issue = "2",
      pages = "e0245463",
      issn = "1932-6203",
      publisher = "Public Library of Science",
      url = "https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0245463",
      abstractText = "Line 1 of abstract.\nLine 2 with \"quotes\" and, commas.",
      notes = "Screened by reviewer A",
      oaStatus = "gold",
      isOa = true,
      license = "cc-by",
      pdfUrl = "https://journals.plos.org/plosone/article/file?id=10.1371/journal.pone.0245463&type=printable",
      downloadStatus = "DOWNLOADED",
      statusMessage = "Downloaded successfully (1.42 MB)",
      localFilePath = "/storage/emulated/0/PaperFetch_PDFs/deep_genomic.pdf",
      fileSizeBytes = 1488977L,
      downloadedAt = 1700000000000L,
      sourceFileName = "sample_refs.ris",
      batchId = "batch-123"
    )

    val paywalledPaper = PaperRecord(
      doi = "10.1038/s41586-020-2012-7",
      title = "Closed Paper",
      downloadStatus = "PAYWALLED"
    )

    val csv = RayyanCsvExporter.exportToCsv(
      papers = listOf(downloadedPaper, paywalledPaper),
      scope = ExportScope.DOWNLOADED_ONLY,
      format = ExportFormat.RAYYAN_ALL_FIELDS
    )

    val rows = DoiParser.parseCsvRows(csv, ',')
    // 1 header row + 1 downloaded row
    assertEquals(2, rows.size)
    assertEquals(RayyanCsvExporter.ALL_HEADERS, rows[0])

    // Re-import back into PaperFetch
    val reimported = DoiParser.parseCsv(csv, "exported.csv", "new-batch")
    assertEquals(1, reimported.size)

    val restored = reimported[0]
    assertEquals(downloadedPaper.doi, restored.doi)
    assertEquals(downloadedPaper.title, restored.title)
    assertEquals(downloadedPaper.authors, restored.authors)
    assertEquals(downloadedPaper.journal, restored.journal)
    assertEquals(downloadedPaper.year, restored.year)
    assertEquals(downloadedPaper.volume, restored.volume)
    assertEquals(downloadedPaper.issue, restored.issue)
    assertEquals(downloadedPaper.pages, restored.pages)
    assertEquals(downloadedPaper.issn, restored.issn)
    assertEquals(downloadedPaper.publisher, restored.publisher)
    assertEquals(downloadedPaper.url, restored.url)
    assertEquals(downloadedPaper.abstractText, restored.abstractText)
    assertEquals(downloadedPaper.notes, restored.notes)
    assertEquals(downloadedPaper.oaStatus, restored.oaStatus)
    assertEquals(downloadedPaper.isOa, restored.isOa)
    assertEquals(downloadedPaper.license, restored.license)
    assertEquals(downloadedPaper.pdfUrl, restored.pdfUrl)
    assertEquals(downloadedPaper.downloadStatus, restored.downloadStatus)
    assertEquals(downloadedPaper.statusMessage, restored.statusMessage)
    assertEquals(downloadedPaper.localFilePath, restored.localFilePath)
    assertEquals(downloadedPaper.fileSizeBytes, restored.fileSizeBytes)
    assertEquals(downloadedPaper.downloadedAt, restored.downloadedAt)
    assertEquals(downloadedPaper.sourceFileName, restored.sourceFileName)
    assertEquals(downloadedPaper.batchId, restored.batchId)
  }

  @Test
  fun testRayyanExport_Strict14ColumnsAndLosslessReimport() {
    val downloadedPaper = PaperRecord(
      doi = "10.1186/s12859-020-03666-4",
      title = "Fast Sequence Alignment",
      authors = "Lee, Alice",
      journal = "BMC Bioinformatics",
      year = 2020,
      volume = "21",
      issue = "1",
      pages = "120",
      issn = "1471-2105",
      publisher = "Springer Nature",
      url = "https://bmcbioinformatics.biomedcentral.com/articles/10.1186/s12859-020-03666-4",
      abstractText = "Fast alignment algorithm abstract.",
      notes = "RAYYAN-INCLUSION: {\"Reviewer\"=>\"Included\"}",
      oaStatus = "gold",
      isOa = true,
      license = "cc-by",
      pdfUrl = "https://bmcbioinformatics.biomedcentral.com/counter/pdf/10.1186/s12859-020-03666-4.pdf",
      downloadStatus = "DOWNLOADED",
      statusMessage = "Downloaded successfully (0.95 MB)",
      localFilePath = "/data/user/0/com.example/files/fast_seq.pdf",
      fileSizeBytes = 996147L,
      downloadedAt = 1700000500000L,
      sourceFileName = "bmc.csv",
      batchId = "batch-999"
    )

    val strictCsv = RayyanCsvExporter.exportToCsv(
      papers = listOf(downloadedPaper),
      scope = ExportScope.DOWNLOADED_ONLY,
      format = ExportFormat.RAYYAN_STRICT
    )

    val rows = DoiParser.parseCsvRows(strictCsv, ',')
    assertEquals(2, rows.size)
    assertEquals(RayyanCsvExporter.RAYYAN_STANDARD_HEADERS, rows[0])

    val reimported = DoiParser.parseCsv(strictCsv, "rayyan_strict.csv", "fallback-batch")
    assertEquals(1, reimported.size)

    val restored = reimported[0]
    assertEquals(downloadedPaper.doi, restored.doi)
    assertEquals(downloadedPaper.title, restored.title)
    assertEquals(downloadedPaper.notes, restored.notes)
    assertEquals(downloadedPaper.oaStatus, restored.oaStatus)
    assertEquals(downloadedPaper.isOa, restored.isOa)
    assertEquals(downloadedPaper.license, restored.license)
    assertEquals(downloadedPaper.pdfUrl, restored.pdfUrl)
    assertEquals(downloadedPaper.downloadStatus, restored.downloadStatus)
    assertEquals(downloadedPaper.statusMessage, restored.statusMessage)
    assertEquals(downloadedPaper.localFilePath, restored.localFilePath)
    assertEquals(downloadedPaper.fileSizeBytes, restored.fileSizeBytes)
    assertEquals(downloadedPaper.downloadedAt, restored.downloadedAt)
    assertEquals(downloadedPaper.sourceFileName, restored.sourceFileName)
    assertEquals(downloadedPaper.batchId, restored.batchId)
  }

  @Test
  fun testImportNativeRayyanCsvWithBomAndMultilineAbstract() {
    val nativeRayyanCsv = "\uFEFF" +
      "key,title,authors,journal,issn,volume,issue,pages,year,publisher,url,abstract,doi,notes\r\n" +
      "\"rayyan-1\",\"CRISPR Screening\", \"Zhang, Wei; Kim, Min\", \"Nature Biotechnology\", \"1087-0156\", \"41\", \"3\", \"300-312\", \"2023\", \"Springer\", \"https://doi.org/10.1038/s41587-023-01678-y\", \"First paragraph.\n\nSecond paragraph of abstract.\", \"10.1038/s41587-023-01678-y\", \"RAYYAN-INCLUSION: {\"\"Alex\"\"=>\"\"Included\"\"}\"\r\n"

    val imported = DoiParser.parseCsv(nativeRayyanCsv, "rayyan_export.csv", "b-native")
    assertEquals(1, imported.size)
    val record = imported[0]
    assertEquals("10.1038/s41587-023-01678-y", record.doi)
    assertEquals("CRISPR Screening", record.title)
    assertEquals("Zhang, Wei; Kim, Min", record.authors)
    assertEquals("Nature Biotechnology", record.journal)
    assertEquals("1087-0156", record.issn)
    assertEquals("41", record.volume)
    assertEquals("3", record.issue)
    assertEquals("300-312", record.pages)
    assertEquals(2023, record.year)
    assertEquals("Springer", record.publisher)
    assertTrue(record.abstractText.contains("First paragraph."))
    assertTrue(record.abstractText.contains("Second paragraph of abstract."))
    assertTrue(record.notes.contains("RAYYAN-INCLUSION"))
  }
}
