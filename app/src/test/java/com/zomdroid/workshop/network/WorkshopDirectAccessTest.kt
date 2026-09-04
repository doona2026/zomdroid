package com.zomdroid.workshop.network

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WorkshopDirectAccessTest {
    @Test
    fun forwardedUrlKeepsLogicalPathAndQueryButUsesForwardHost() {
        val route = WattToolkitWorkshopRoute(
            logicalHosts = setOf("steamcommunity.com"),
            forwardTargets = listOf("https://forward.example/"),
            fakeServerName = "forward.example",
        )

        val forwarded = route.buildForwardedUrl(
            "https://steamcommunity.com/workshop/browse/?appid=108600&p=2".toHttpUrl(),
        )

        assertThat(forwarded.host).isEqualTo("forward.example")
        assertThat(forwarded.encodedPath).isEqualTo("/workshop/browse/")
        assertThat(forwarded.query).isEqualTo("appid=108600&p=2")
        assertThat(route.matchesLogicalHost("steamcommunity.com")).isTrue()
        assertThat(route.matchesLogicalHost("forward.example")).isFalse()
    }

    @Test
    fun logicalUrlFromForwardedRedirectIsNormalizedBackToSteamHost() {
        val route = WattToolkitWorkshopRoute(
            logicalHosts = setOf("steamcommunity.com"),
            forwardTargets = listOf("https://forward.example/"),
            fakeServerName = "forward.example",
        )

        val normalized = route.normalizeLogicalUrl(
            url = "https://forward.example/sharedfiles/filedetails/?id=42".toHttpUrl(),
            fallbackLogicalHost = "steamcommunity.com",
        )

        assertThat(normalized.host).isEqualTo("steamcommunity.com")
        assertThat(normalized.encodedPath).isEqualTo("/sharedfiles/filedetails/")
        assertThat(normalized.query).isEqualTo("id=42")
    }

    @Test
    fun builtInCommunityRouteIsAvailableWithoutRemoteRouteFetch() {
        val route = defaultBootstrapRouteForProfile(SteamCommunityWattToolkitRouteProfile)

        assertThat(route).isNotNull()
        assertThat(route!!.logicalHosts).contains("steamcommunity.com")
        assertThat(route.forwardTargets).containsExactly("https://www.valvesoftware.com")
    }

    @Test
    fun routeCandidatesPreserveConfiguredOrderForFallback() {
        val route = WattToolkitWorkshopRoute(
            logicalHosts = setOf("steamcommunity.com"),
            forwardTargets = listOf("https://one.example", "https://two.example"),
        )

        assertThat(route.forwardTargetCandidates().map { it.forwardTargets.first() })
            .containsExactly("https://one.example", "https://two.example")
            .inOrder()
    }

    @Test
    fun interceptorSendsForwardRequestWithLogicalSteamHost() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("workshop")
                .build(),
        )
        server.start()
        try {
            val profile = WattToolkitRouteProfile(
                name = "test",
                cacheFileName = "test.json",
                supportedHosts = setOf("steamcommunity.com"),
                bootstrapForwardTargets = listOf(server.url("/").toString()),
            )
            val resolver = WattToolkitWorkshopRouteResolver(
                routeProfile = profile,
                routeStore = NoOpWattToolkitWorkshopRouteStore,
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(WorkshopDirectAccessInterceptor(resolver, OkHttpClient()))
                .build()

            client.newCall(
                Request.Builder()
                    .url("https://steamcommunity.com/workshop/browse/?appid=108600&p=2")
                    .header("Cookie", "steamLoginSecure=secret")
                    .header("Authorization", "Bearer secret")
                    .build(),
            ).execute().use { response ->
                assertThat(response.body?.string()).isEqualTo("workshop")
                assertThat(response.request.url.host).isEqualTo("steamcommunity.com")
            }

            val forwardedRequest = server.takeRequest()
            assertThat(forwardedRequest.url.encodedPath).isEqualTo("/workshop/browse/")
            assertThat(forwardedRequest.url.query).isEqualTo("appid=108600&p=2")
            assertThat(forwardedRequest.headers["Host"]).isEqualTo("steamcommunity.com")
            assertThat(forwardedRequest.headers["Cookie"]).isNull()
            assertThat(forwardedRequest.headers["Authorization"]).isNull()
        } finally {
            server.close()
        }
    }

    @Test
    fun interceptorTriesNextForwardTargetAfterRelayFailure() {
        val failedServer = MockWebServer()
        val workingServer = MockWebServer()
        failedServer.enqueue(MockResponse.Builder().code(503).body("unavailable").build())
        workingServer.enqueue(MockResponse.Builder().code(200).body("workshop").build())
        failedServer.start()
        workingServer.start()
        try {
            val profile = WattToolkitRouteProfile(
                name = "test",
                cacheFileName = "test.json",
                supportedHosts = setOf("steamcommunity.com"),
                bootstrapForwardTargets = listOf(
                    failedServer.url("/").toString(),
                    workingServer.url("/").toString(),
                ),
            )
            val resolver = WattToolkitWorkshopRouteResolver(
                routeProfile = profile,
                routeStore = NoOpWattToolkitWorkshopRouteStore,
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(WorkshopDirectAccessInterceptor(resolver, OkHttpClient()))
                .build()

            client.newCall(
                Request.Builder().url("https://steamcommunity.com/workshop/browse/").build(),
            ).execute().use { response ->
                assertThat(response.code).isEqualTo(200)
                assertThat(response.body?.string()).isEqualTo("workshop")
            }
            assertThat(failedServer.takeRequest().url.encodedPath).isEqualTo("/workshop/browse/")
            assertThat(workingServer.takeRequest().url.encodedPath).isEqualTo("/workshop/browse/")
        } finally {
            failedServer.close()
            workingServer.close()
        }
    }

    @Test
    fun resolverParsesWattProjectGroupsAndCachesRoute() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(
                    """{"🦓":[{"Items":[{"MatchDomainNames":"steamcommunity.com","ListenDomainNames":"","ForwardDomainNames":"https://relay.example","ProxyType":0,"Checked":true}]}]}""",
                )
                .build(),
        )
        server.start()
        val tempDir = Files.createTempDirectory("watt-route-test").toFile()
        try {
            val profile = WattToolkitRouteProfile(
                name = "test",
                cacheFileName = "route.json",
                supportedHosts = setOf("steamcommunity.com"),
                bootstrapForwardTargets = emptyList(),
            )
            val resolver = WattToolkitWorkshopRouteResolver(
                routeProfile = profile,
                projectGroupsUrl = server.url("/accelerator/projectgroups"),
                routeStore = FileBackedWattToolkitWorkshopRouteStore(File(tempDir, "route.json")),
            )

            val route = resolver.resolveRouteForHost("steamcommunity.com")

            assertThat(route).isNotNull()
            assertThat(route!!.forwardTargets).containsExactly("https://relay.example")
            assertThat(server.takeRequest().method).isEqualTo("POST")
            assertThat(File(tempDir, "route.json").isFile).isTrue()
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }
}
