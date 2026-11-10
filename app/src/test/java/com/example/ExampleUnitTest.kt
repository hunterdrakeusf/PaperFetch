package com.example

import com.example.parser.DoiParser
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
      PY  - 2021
      DO  - 10.1371/journal.pone.0245463
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
}
