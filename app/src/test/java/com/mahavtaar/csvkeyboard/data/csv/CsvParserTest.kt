package com.mahavtaar.csvkeyboard.data.csv

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    private val parser = CsvParser()

    @Test
    fun testSplitLineSimple() {
        val line = "a,b,c"
        val expected = listOf("a", "b", "c")
        assertEquals(expected, parser.splitLine(line, ','))
    }

    @Test
    fun testSplitLineWithQuotes() {
        val line = "a,\"b,c\",d"
        val expected = listOf("a", "b,c", "d")
        assertEquals(expected, parser.splitLine(line, ','))
    }

    @Test
    fun testSplitLineWithEmptyFields() {
        val line = "a,,c"
        val expected = listOf("a", "", "c")
        assertEquals(expected, parser.splitLine(line, ','))
    }
}
