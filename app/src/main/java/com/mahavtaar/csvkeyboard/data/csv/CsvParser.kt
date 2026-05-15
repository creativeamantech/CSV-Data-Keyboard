package com.mahavtaar.csvkeyboard.data.csv

import android.content.Context
import android.net.Uri
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import java.io.BufferedReader
import java.io.InputStreamReader

data class ParseResult(val headers: List<String>, val rows: List<CsvRow>)
sealed class CsvError : Exception() {
    object FileNotFound : CsvError()
    object EmptyFile : CsvError()
    data class Malformed(val line: Int, override val message: String) : CsvError()
    data class Unknown(override val cause: Throwable) : CsvError()
}

class CsvParser {
    fun parse(uri: Uri, context: Context, delimiter: Char = ','): Result<ParseResult> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return Result.failure(CsvError.FileNotFound)
            val reader = BufferedReader(InputStreamReader(inputStream))

            val lines = reader.readLines()
            if (lines.isEmpty()) {
                return Result.failure(CsvError.EmptyFile)
            }

            // Remove BOM from the first line if present
            val rawFirstLine = lines[0]
            val firstLine = if (rawFirstLine.startsWith("\uFEFF")) rawFirstLine.substring(1) else rawFirstLine

            // Fix edge case 2: filter out blank column names to avoid Room PK constraints issues
            val allHeaders = splitLine(firstLine, delimiter).map { it.trim() }
            val validHeadersIndices = allHeaders.indices.filter { allHeaders[it].isNotBlank() }
            val headers = validHeadersIndices.map { allHeaders[it] }

            if (headers.isEmpty()) {
                return Result.failure(CsvError.EmptyFile)
            }

            val rows = mutableListOf<CsvRow>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.isBlank()) continue

                val values = splitLine(line, delimiter)
                val data = mutableMapOf<String, String>()
                for (j in validHeadersIndices.indices) {
                    val actualIndex = validHeadersIndices[j]
                    data[headers[j]] = values.getOrNull(actualIndex)?.trim() ?: ""
                }
                rows.add(CsvRow(rowIndex = i - 1, data = data))
            }

            Result.success(ParseResult(headers, rows))
        } catch (e: Exception) {
            Result.failure(CsvError.Unknown(e))
        }
    }

    // Advanced CSV parser that handles quoted strings and commas inside quotes
    internal fun splitLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var currentValue = StringBuilder()
        var skipNext = false

        for (i in line.indices) {
            if (skipNext) {
                skipNext = false
                continue
            }

            val char = line[i]

            if (char == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    // Escaped quote
                    currentValue.append('"')
                    skipNext = true
                } else {
                    inQuotes = !inQuotes
                }
            } else if (char == delimiter && !inQuotes) {
                result.add(currentValue.toString())
                currentValue = StringBuilder()
            } else {
                currentValue.append(char)
            }
        }
        result.add(currentValue.toString())
        return result
    }
}
