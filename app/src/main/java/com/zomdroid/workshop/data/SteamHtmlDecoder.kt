package com.zomdroid.workshop.data

internal object SteamHtmlDecoder {
    private val numericEntityRegex = Regex("""&#(x?[0-9A-Fa-f]+);""")
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val emoticonImageRegex = Regex("""(?is)<img\b[^>]*\balt="([^"]+)"[^>]*\bclass="[^"]*\bemoticon\b[^"]*"[^>]*>""")
    private val bbCodeGenericTagRegex = Regex("""\[(?:/?[A-Za-z][A-Za-z0-9_]*|\*)(?:=[^\]]+)?\]""")
    private val bbCodeMediaTagRegex = Regex(
        """(?is)\[(?:img|previewyoutube|previewyoutubehd)[^\]]*].*?\[/(?:img|previewyoutube|previewyoutubehd)]""",
    )
    private val bbCodeImageRegex = Regex(
        """(?is)\[img[^\]]*]\s*(.*?)\s*\[/img]""",
    )
    private val htmlImageRegex = Regex(
        """(?is)<img\b[^>]*\bsrc\s*=\s*[\"']([^\"']+)[\"'][^>]*>""",
    )
    private val fullScreenshotRegex = Regex(
        """(?is)\{\s*['\"]previewid['\"]\s*:\s*['\"][^'\"]+['\"]\s*,\s*['\"]url['\"]\s*:\s*['\"]([^'\"]+)['\"]""",
    )
    private val screenshotMapRegex = Regex(
        """(?is)['\"]\d+['\"]\s*:\s*['\"]([^'\"]+)['\"]""",
    )
    private val whitespaceRegex = Regex("""\s+""")
    private val inlineWhitespaceRegex = Regex("""[^\S\n]+""")

    fun stripTagsAndDecode(value: String): String = decode(value.replace(htmlTagRegex, " "))

    fun decode(value: String): String {
        return decodeEntities(value)
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun decodePreservingLineBreaks(value: String): String =
        decodeEntities(value)
            .replace(Regex("""\r\n?"""), "\n")
            .lines()
            .joinToString("\n") { line ->
                line.replace(inlineWhitespaceRegex, " ").trim()
            }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

    fun decodeWorkshopApiDescription(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(bbCodeMediaTagRegex, " ")
                .replace(Regex("""(?i)\[\*\]"""), "\n• ")
                .replace(Regex("""(?i)\[/?(?:h[1-6]|list|olist|quote|p|center|left|right)\]"""), "\n")
                .replace(htmlTagRegex, " ")
                .replace(bbCodeGenericTagRegex, " "),
        )
    }

    fun decodeWorkshopHtmlDescription(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)<li[^>]*>"""), "\n• ")
                .replace(Regex("""(?i)</li\s*>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(htmlTagRegex, " "),
        )
    }

    fun extractWorkshopImageUrls(value: String): List<String> = buildList {
        imageMatches(value).forEach { add(it.url) }
    }.distinct()

    fun extractWorkshopGalleryImageUrls(value: String): List<String> {
        val fullUrls = fullScreenshotRegex.findAll(value)
            .map { normalizeImageUrl(decodeEntities(it.groupValues[1])) }
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (fullUrls.isNotEmpty()) {
            return fullUrls
        }
        return screenshotMapRegex.findAll(value)
            .map { normalizeImageUrl(decodeEntities(it.groupValues[1])) }
            .filter(String::isNotBlank)
            .distinct()
            .toList()
    }

    fun decodeWorkshopDescriptionParts(value: String, isHtml: Boolean): List<WorkshopDescriptionPart> {
        if (value.isBlank()) return emptyList()
        val matches = imageMatches(value)
        if (matches.isEmpty()) {
            return listOf(WorkshopDescriptionPart(decodeDescriptionText(value, isHtml), null))
        }
        val parts = mutableListOf<WorkshopDescriptionPart>()
        var cursor = 0
        matches.forEach { match ->
            val text = decodeDescriptionText(value.substring(cursor, match.start), isHtml)
            if (text.isNotBlank()) parts += WorkshopDescriptionPart(text, null)
            parts += WorkshopDescriptionPart("", match.url)
            cursor = match.end
        }
        val trailingText = decodeDescriptionText(value.substring(cursor), isHtml)
        if (trailingText.isNotBlank()) parts += WorkshopDescriptionPart(trailingText, null)
        return parts
    }

    private fun imageMatches(value: String): List<ImageMatch> {
        val bbCodeMatches = bbCodeImageRegex.findAll(value).mapNotNull { match ->
            normalizeImageUrl(decodeEntities(match.groupValues[1])).takeIf(String::isNotBlank)?.let {
                ImageMatch(match.range.first, match.range.last + 1, it)
            }
        }
        val htmlMatches = htmlImageRegex.findAll(value).mapNotNull { match ->
            normalizeImageUrl(decodeEntities(match.groupValues[1])).takeIf(String::isNotBlank)?.let {
                ImageMatch(match.range.first, match.range.last + 1, it)
            }
        }
        return (bbCodeMatches + htmlMatches).sortedBy { it.start }.distinctBy { it.start }.toList()
    }

    private fun decodeDescriptionText(value: String, isHtml: Boolean): String =
        if (isHtml) decodeWorkshopHtmlDescription(value) else decodeWorkshopApiDescription(value)

    private data class ImageMatch(val start: Int, val end: Int, val url: String)

    private fun normalizeImageUrl(value: String): String {
        val url = value.trim()
        return when {
            url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true) -> url
            url.startsWith("//") -> "https:$url"
            else -> ""
        }
    }

    fun decodeWorkshopChangeNotes(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)<li[^>]*>"""), "- ")
                .replace(Regex("""(?i)</li\s*>"""), "\n")
                .replace(Regex("""(?i)<(?:ul|ol)[^>]*>"""), "\n")
                .replace(Regex("""(?i)</(?:ul|ol)\s*>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(htmlTagRegex, " "),
        )
    }

    fun decodeWorkshopComment(value: String): String {
        if (value.isBlank()) {
            return ""
        }

        return decodePreservingLineBreaks(
            value
                .replace(emoticonImageRegex) { match -> " ${match.groupValues[1]} " }
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p\s*>"""), "\n\n")
                .replace(Regex("""(?i)</div\s*>"""), "\n")
                .replace(htmlTagRegex, " "),
        )
    }

    private fun decodeEntities(value: String): String {
        val withNumericEntities = numericEntityRegex.replace(value) { match ->
            val token = match.groupValues[1]
            val codePoint = if (token.startsWith("x", ignoreCase = true)) {
                token.substring(1).toIntOrNull(16)
            } else {
                token.toIntOrNull()
            }
            codePoint?.let { String(Character.toChars(it)) } ?: match.value
        }

        return withNumericEntities
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}

internal data class WorkshopDescriptionPart(val text: String, val imageUrl: String?)
