package com.zomdroid.workshop.library

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

data class ParsedModInfo(
    val fields: Map<String, String> = emptyMap(),
    val name: String? = null,
    val id: String? = null,
    val description: String? = null,
    val poster: String? = null,
    val icon: String? = null,
    val readable: Boolean = true,
)

object ModInfoParser {
    @JvmStatic
    fun parse(file: File): ParsedModInfo {
        if (!file.isFile) return ParsedModInfo(readable = false)

        val lines = readLinesWithFallback(file) ?: return ParsedModInfo(readable = false)
        return parseLines(lines)
    }

    @JvmStatic
    fun parseLines(lines: Iterable<String>): ParsedModInfo {
        val fields = linkedMapOf<String, String>()
        lines.forEach { rawLine ->
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
            val separator = line.indexOf('=')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty() && !fields.containsKey(key)) fields[key] = value
        }

        return ParsedModInfo(
            fields = fields,
            name = fields["name"].orEmpty().trim().ifEmpty { null },
            id = fields["id"].orEmpty().trim().ifEmpty { null },
            description = fields["description"].orEmpty().trim().ifEmpty { null },
            poster = fields["poster"].orEmpty().trim().ifEmpty { null },
            icon = fields["icon"].orEmpty().trim().ifEmpty { null },
        )
    }

    private fun readLinesWithFallback(file: File): List<String>? {
        return runCatching { file.readLines(StandardCharsets.UTF_8) }.getOrElse {
            runCatching { file.readLines(Charset.forName("ISO-8859-1")) }.getOrNull()
        }
    }
}
