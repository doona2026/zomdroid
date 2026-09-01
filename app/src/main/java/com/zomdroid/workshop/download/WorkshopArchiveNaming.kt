package com.zomdroid.workshop.download

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Creates readable, filesystem-safe names without losing Workshop identity or version uniqueness. */
object WorkshopArchiveNaming {
    private val json = Json { ignoreUnknownKeys = true }
    private val invalidFileNameCharacters = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

    @JvmStatic
    fun forWorkshop(workshopId: Long, title: String?, timestampMillis: Long): String {
        val cleanedTitle = title.orEmpty()
            .trim()
            .map { if (invalidFileNameCharacters.contains(it)) '_' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trimEnd('.', ' ')
            .take(96)
            .trimEnd('.', ' ')
        val safeTitle = cleanedTitle.ifBlank { "Workshop $workshopId" }
        return "$safeTitle [$workshopId]_$timestampMillis.zip"
    }

    @JvmStatic
    fun titleFromMetadata(metadata: String?, workshopId: Long): String {
        val title = metadata?.let {
            runCatching { findTitle(json.parseToJsonElement(it)) }.getOrNull()
        }.orEmpty()
        return title.ifBlank { "Workshop $workshopId" }
    }

    private fun findTitle(element: JsonElement): String? = when (element) {
        is JsonObject -> runCatching { element["title"]?.jsonPrimitive?.content }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: element.values.asSequence().mapNotNull(::findTitle).firstOrNull()
        is JsonArray -> element.asSequence().mapNotNull(::findTitle).firstOrNull()
        else -> null
    }
}
