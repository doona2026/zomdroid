package com.zomdroid.workshop.thirdparty

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Test

class GgntwFallbackClientTest {
    @Test
    fun parsesRawAndJsonUrlsOnlyForTrustedHttpsHosts() {
        assertThat(GgntwFallbackClient.parseDownloadUrl("https://cdn.ggntw.com/files/mod.zip").host)
            .isEqualTo("cdn.ggntw.com")
        assertThat(GgntwFallbackClient.parseDownloadUrl("{\"url\":\"https://ggntw.com/mod.zip\"}").encodedPath)
            .isEqualTo("/mod.zip")
        assertThat(GgntwFallbackClient.parseDownloadUrl("https://ouo.io/LZvrPf").host)
            .isEqualTo("ouo.io")
        assertThat(GgntwFallbackClient.parseDownloadUrl("https://steamusercontent-a.akamaihd.net/ugc/mod.zip").host)
            .isEqualTo("steamusercontent-a.akamaihd.net")
    }

    @Test
    fun rejectsHttpUntrustedAndTraversalUrls() {
        listOf(
            "http://cdn.ggntw.com/mod.zip",
            "https://evil.example/mod.zip",
            "https://cdn.ggntw.com/files/../mod.zip",
        ).forEach { raw ->
            assertThat(runCatching { GgntwFallbackClient.parseDownloadUrl(raw) }.exceptionOrNull())
                .isInstanceOf(Exception::class.java)
        }
    }

    @Test
    fun rejectsMissingResponseUrl() {
        assertThat(runCatching { GgntwFallbackClient.parseDownloadUrl("{}") }.exceptionOrNull())
            .isInstanceOf(IOException::class.java)
    }

    @Test
    fun requestsRawJsonAndReportsHttpErrors() = runBlocking {
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(MockResponse.Builder().body("https://cdn.ggntw.com/mod.zip").build())
            server.enqueue(MockResponse.Builder().body("{\"link\":\"https://cdn.ggntw.com/mod-2.zip\"}").build())
            server.enqueue(MockResponse.Builder().code(502).body("bad gateway").build())
            val client = GgntwFallbackClient(OkHttpClient(), server.url("/steam.request"))

            assertThat(client.requestDownloadUrl(123L).encodedPath).isEqualTo("/mod.zip")
            assertThat(client.requestDownloadUrl(124L).encodedPath).isEqualTo("/mod-2.zip")
            assertThat(runCatching { client.requestDownloadUrl(125L) }.exceptionOrNull())
                .isInstanceOf(IOException::class.java)
            assertThat(server.takeRequest().method).isEqualTo("POST")
        } finally {
            server.close()
        }
    }
}
