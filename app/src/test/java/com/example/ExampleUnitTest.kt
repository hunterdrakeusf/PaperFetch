package com.example

import com.example.parser.DoiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testDoiNormalization() {
    assertEquals("10.1371/journal.pone.0245463", DoiParser.normalizeDoi("https://doi.org/10.1371/journal.pone.0245463"))
    assertEquals("10.1038/s41586-020-2012-7", DoiParser.normalizeDoi("doi:10.1038/s41586-020-2012-7."))
    assertEquals("10.1145/3065386", DoiParser.normalizeDoi("  10.1145/3065386, "))
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
}
