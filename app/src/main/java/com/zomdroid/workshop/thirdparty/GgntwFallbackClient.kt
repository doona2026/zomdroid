package com.zomdroid.workshop.thirdparty

import com.zomdroid.workshop.library.ModLibraryEntry
import com.zomdroid.workshop.library.ModLibraryFile
import com.zomdroid.workshop.library.ModLibraryRepository
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Explicitly invoked ggntw.com fallback. It never receives Steam credentials or tokens. */
class GgntwFallbackClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val requestEndpoint: HttpUrl = "https://api.ggntw.com/steam.request".toHttpUrl(),
    private val allowedHost: (String) -> Boolean = ::isAllowedHost,
) {
    suspend fun requestDownloadUrl(workshopId: Long): HttpUrl = withContext(Dispatchers.IO) {
        require(workshopId > 0) { "Workshop ID must be positive" }
        val payload = "{\"url\":\"https://steamcommunity.com/sharedfiles/filedetails/?id=$workshopId\"}"
        val request = Request.Builder()
            .url(requestEndpoint)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://ggntw.com")
            .header("Referer", "https://ggntw.com/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("ggntw request failed: HTTP ${response.code}")
            parseDownloadUrl(response.body?.string().orEmpty(), allowedHost)
        }
    }

    suspend fun downloadToLibrary(
        workshopId: Long,
        title: String,
        library: ModLibraryRepository,
        description: String = "",
        previewUrl: String = "",
        updatedAtEpochSeconds: Long? = null,
        stagingRoot: File,
        destinationRoot: File,
        clock: () -> Long = { System.currentTimeMillis() },
    ): ModLibraryEntry = withContext(Dispatchers.IO) {
        val url = requestDownloadUrl(workshopId)
        val root = destinationRoot.canonicalFile
        require(root.exists() || root.mkdirs()) { "Cannot create ggntw staging directory" }
        val staging = stagingRoot.canonicalFile
        require(staging.exists() || staging.mkdirs()) { "Cannot create private ggntw staging directory" }
        val finalFile = File(root, "workshop_${workshopId}_ggntw_${clock()}.zip").canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        require(finalFile.path.startsWith(prefix)) { "Invalid ggntw output path" }
        val temporary = File(staging, ".${finalFile.name}.part")
        val publishing = File(root, ".${finalFile.name}.part")
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("ggntw download failed: HTTP ${response.code}")
                val body = response.body ?: throw IOException("ggntw returned an empty response")
                body.byteStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
            }
            FileInputStream(temporary).use { input ->
                publishing.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (!publishing.renameTo(finalFile)) throw IOException("Cannot publish ggntw download")
            temporary.delete()
            library.recordCompleted(
                appId = 108600L,
                publishedFileId = workshopId,
                title = title,
                description = description,
                previewUrl = previewUrl,
                updatedAtEpochSeconds = updatedAtEpochSeconds,
                completedPath = finalFile,
                files = listOf(com.zomdroid.workshop.download.DownloadCenterFile(
                    relativePath = finalFile.name,
                    sizeBytes = finalFile.length(),
                    modifiedEpochMillis = finalFile.lastModified(),
                )),
                source = "ggntw",
            )
        } catch (error: Throwable) {
            temporary.delete()
            publishing.delete()
            finalFile.delete()
            throw error
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val TRAVERSAL_PATH_PATTERN = Regex("(?i)(^|/)(?:\\.\\.|%2e%2e)(?:/|$)")

        @JvmStatic
        fun parseDownloadUrl(raw: String, allowedHost: (String) -> Boolean = ::isAllowedHost): HttpUrl {
            val trimmed = raw.trim()
            val candidate = if (trimmed.startsWith("http", ignoreCase = true)) {
                trimmed
            } else {
                runCatching {
                    Json { ignoreUnknownKeys = true }
                        .parseToJsonElement(trimmed).jsonObject
                        .let { it["url"]?.jsonPrimitive?.content ?: it["link"]?.jsonPrimitive?.content }
                }.getOrNull() ?: throw IOException("ggntw returned no download URL")
            }
            val url = runCatching { candidate.toHttpUrl() }.getOrNull()
                ?: throw IOException("ggntw returned an invalid URL")
            val rawPath = candidate.substringBefore('?').substringBefore('#')
            require(!TRAVERSAL_PATH_PATTERN.containsMatchIn(rawPath)) {
                "ggntw URL contains an invalid path"
            }
            require(url.scheme == "https") { "ggntw download URL must use HTTPS" }
            require(url.username.isEmpty() && url.password.isEmpty()) { "ggntw URL must not contain credentials" }
            require(allowedHost(url.host)) { "ggntw returned an untrusted host" }
            require(url.pathSegments.none { it == ".." || it.isBlank() && url.encodedPath.contains("..") }) {
                "ggntw URL contains an invalid path"
            }
            return url
        }

        @JvmStatic
        fun isAllowedHost(host: String): Boolean =
            host == "ggntw.com" || host.endsWith(".ggntw.com")
    }
}
