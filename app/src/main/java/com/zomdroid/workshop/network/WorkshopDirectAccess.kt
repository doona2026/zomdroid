package com.zomdroid.workshop.network

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ProtocolException
import java.net.Proxy
import java.security.cert.X509Certificate
import java.util.LinkedHashSet
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Rewrites public Steam web requests through the same Watt Toolkit route used by
 * WorkshopAndroidDownloader. The original Steam URL remains the logical URL;
 * only the network URL is changed for the direct call.
 */
internal class WorkshopDirectAccessInterceptor(
    private val routeResolver: WattToolkitWorkshopRouteResolver,
    private val directCallFactory: Call.Factory,
    private val maxRedirects: Int = MAX_FOLLOW_UPS,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalHost = originalRequest.url.host
        if (!routeResolver.supports(originalHost)) {
            return chain.proceed(originalRequest)
        }

        val route = routeResolver.resolveRouteForHost(originalHost)
            ?: return chain.proceed(originalRequest)
        return try {
            executeWithRouteRefresh(originalRequest, originalHost, route)
        } catch (_: IOException) {
            // The original Steam route remains the final fallback. This also
            // keeps the feature safe when the relay configuration is stale.
            chain.proceed(originalRequest)
        }
    }

    private fun executeWithRouteRefresh(
        initialRequest: Request,
        originalHost: String,
        route: WattToolkitWorkshopRoute,
    ): Response {
        return try {
            executeDirectRequest(initialRequest, route)
        } catch (error: IOException) {
            if (!error.isRetryableDirectAccessFailure()) {
                throw error
            }
            val refreshedRoute = routeResolver.refreshRouteForHost(originalHost) ?: throw error
            try {
                executeDirectRequest(initialRequest, refreshedRoute)
            } catch (refreshedError: IOException) {
                refreshedError.addSuppressed(error)
                throw refreshedError
            }
        }
    }

    private fun executeDirectRequest(
        initialRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Response {
        var logicalRequest = route.normalizeLogicalRequest(initialRequest)
        var redirectCount = 0
        while (true) {
            val response = executeWithForwardTargetFallback(logicalRequest, route)
            val redirectTarget = response.redirectTarget(logicalRequest.url, route)
            if (redirectTarget == null) {
                return response.newBuilder()
                    .request(logicalRequest)
                    .build()
            }
            if (redirectCount >= maxRedirects) {
                response.close()
                throw ProtocolException("Too many Workshop direct-access redirects: $maxRedirects")
            }
            val nextRequest = buildRedirectRequest(
                previousRequest = logicalRequest,
                redirectUrl = redirectTarget,
                responseCode = response.code,
            )
            response.close()
            logicalRequest = nextRequest
            redirectCount++
        }
    }

    private fun executeWithForwardTargetFallback(
        logicalRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Response {
        var lastError: IOException? = null
        route.forwardTargetCandidates().forEach { candidateRoute ->
            try {
                val response = directCallFactory
                    .newCall(buildNetworkRequest(logicalRequest, candidateRoute))
                    .execute()
                if (response.isRetryableForwardedFailure(logicalRequest, candidateRoute)) {
                    response.close()
                    lastError = IOException("Forwarded Steam request failed with HTTP ${response.code}")
                    return@forEach
                }
                return response
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException("No Workshop direct-access route candidate was available")
    }

    private fun buildNetworkRequest(
        logicalRequest: Request,
        route: WattToolkitWorkshopRoute,
    ): Request {
        val logicalUrl = route.normalizeLogicalUrl(
            url = logicalRequest.url,
            fallbackLogicalHost = logicalRequest.url.host,
        )
        val shouldForward = route.matchesLogicalHost(logicalUrl.host)
        val networkUrl = if (shouldForward) route.buildForwardedUrl(logicalUrl) else logicalUrl
        if (!shouldForward) {
            return logicalRequest.newBuilder().url(networkUrl).build()
        }

        // Catalog requests are intentionally public/anonymous. Do not send a
        // Steam Cookie header to the relay; the original route still receives
        // the untouched request if the relay cannot serve it.
        return logicalRequest.newBuilder()
            .url(networkUrl)
            .removeHeader("Host")
            .removeHeader("Cookie")
            .removeHeader("Authorization")
            .header("Host", logicalUrl.host)
            .build()
    }

    private fun buildRedirectRequest(
        previousRequest: Request,
        redirectUrl: HttpUrl,
        responseCode: Int,
    ): Request {
        val preserveBody = responseCode == HTTP_TEMP_REDIRECT || responseCode == HTTP_PERM_REDIRECT
        val originalMethod = previousRequest.method
        val redirectMethod = when {
            preserveBody -> originalMethod
            originalMethod == HTTP_METHOD_GET || originalMethod == HTTP_METHOD_HEAD -> originalMethod
            else -> HTTP_METHOD_GET
        }
        val redirectBody = if (redirectMethod == originalMethod) previousRequest.body else null
        return previousRequest.newBuilder()
            .url(redirectUrl)
            .method(redirectMethod, redirectBody)
            .apply {
                if (redirectBody == null) {
                    removeHeader("Transfer-Encoding")
                    removeHeader("Content-Length")
                    removeHeader("Content-Type")
                }
            }
            .build()
    }
}

internal class WorkshopDirectHostnameVerifier(
    private val defaultVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier(),
    private val unsafeHostBypassProvider: (String) -> Boolean,
) : HostnameVerifier {
    override fun verify(hostname: String, session: SSLSession): Boolean =
        defaultVerifier.verify(hostname, session) || unsafeHostBypassProvider(hostname)
}

internal data class WattToolkitWorkshopRoute(
    val logicalHosts: Set<String>,
    val forwardTargets: List<String>,
    val ignoreSslCertVerification: Boolean = false,
    val fakeServerName: String = "",
) {
    private val normalizedLogicalHosts = logicalHosts.map(String::lowercase).toSet()
    private val normalizedFakeServerName = fakeServerName.trim()
        .takeIf { it.isNotEmpty() && it != "{origin}" && it != "@domain" }
        ?.lowercase()

    val forwardHosts: Set<String> = forwardTargets.mapNotNull { target ->
        runCatching {
            if (target.contains("://")) target.toHttpUrl().host.lowercase() else target.lowercase()
        }.getOrNull()
    }.toSet()

    val networkHosts: Set<String> = buildSet {
        addAll(forwardHosts)
        normalizedFakeServerName?.let(::add)
        if (usesOriginFakeServerName()) addAll(normalizedLogicalHosts)
    }

    fun matchesLogicalHost(host: String): Boolean = host.lowercase() in normalizedLogicalHosts

    fun buildForwardedUrl(originalUrl: HttpUrl): HttpUrl {
        val firstTarget = forwardTargets.firstOrNull()?.trim().orEmpty()
        if (firstTarget.isBlank()) return originalUrl
        return if (firstTarget.contains("://")) {
            val forwardedBase = firstTarget.toHttpUrl()
            val networkHost = networkHostFor(originalUrl.host) ?: forwardedBase.host
            forwardedBase.newBuilder()
                .encodedPath(originalUrl.encodedPath)
                .host(networkHost)
                .encodedQuery(originalUrl.encodedQuery)
                .build()
        } else {
            originalUrl.newBuilder()
                .host(networkHostFor(originalUrl.host) ?: firstTarget)
                .build()
        }
    }

    fun normalizeLogicalRequest(request: Request): Request {
        val normalizedUrl = normalizeLogicalUrl(request.url, request.url.host)
        return if (normalizedUrl == request.url) request else request.newBuilder().url(normalizedUrl).build()
    }

    fun normalizeLogicalUrl(url: HttpUrl, fallbackLogicalHost: String): HttpUrl {
        if (url.host.lowercase() !in networkHosts) return url
        return url.newBuilder().host(fallbackLogicalHost).build()
    }

    fun shouldBypassHostnameVerification(host: String): Boolean =
        ignoreSslCertVerification && host.lowercase() in networkHosts

    fun forwardTargetCandidates(): List<WattToolkitWorkshopRoute> =
        if (forwardTargets.size < 2) listOf(this)
        else forwardTargets.indices.map { index -> copy(forwardTargets = forwardTargets.drop(index)) }

    private fun networkHostFor(logicalHost: String): String? = when {
        normalizedFakeServerName != null -> normalizedFakeServerName
        usesOriginFakeServerName() -> logicalHost.lowercase()
        else -> null
    }

    private fun usesOriginFakeServerName(): Boolean = fakeServerName.trim() in setOf("{origin}", "@domain")
}

internal class WattToolkitForwardDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    private val forwardHostsByNetworkHost = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun register(route: WattToolkitWorkshopRoute) {
        val targetHost = route.forwardHosts.firstOrNull() ?: return
        val fakeHost = route.fakeServerName.trim()
            .takeIf { it.isNotEmpty() && it != "{origin}" && it != "@domain" }
            ?.lowercase()
        if (fakeHost != null) {
            forwardHostsByNetworkHost[fakeHost] = targetHost
        } else if (route.fakeServerName.trim() in setOf("{origin}", "@domain")) {
            route.logicalHosts.forEach { logicalHost ->
                forwardHostsByNetworkHost[logicalHost.lowercase()] = targetHost
            }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> =
        delegate.lookup(forwardHostsByNetworkHost[hostname.lowercase()] ?: hostname)
}

internal interface WattToolkitWorkshopRouteStore {
    fun load(): PersistedWattToolkitWorkshopRoute?
    fun save(route: PersistedWattToolkitWorkshopRoute)
    fun clear()
}

internal object NoOpWattToolkitWorkshopRouteStore : WattToolkitWorkshopRouteStore {
    override fun load(): PersistedWattToolkitWorkshopRoute? = null
    override fun save(route: PersistedWattToolkitWorkshopRoute) = Unit
    override fun clear() = Unit
}

internal data class PersistedWattToolkitWorkshopRoute(
    val route: WattToolkitWorkshopRoute,
    val cachedAtMs: Long,
)

internal class FileBackedWattToolkitWorkshopRouteStore(
    private val file: File,
    private val fallbackLogicalHosts: Set<String> = emptySet(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : WattToolkitWorkshopRouteStore {
    override fun load(): PersistedWattToolkitWorkshopRoute? = runCatching {
        val snapshot = json.decodeFromString<PersistedWattToolkitWorkshopRouteSnapshot>(file.readText())
        val logicalHosts = snapshot.logicalHosts.ifEmpty { fallbackLogicalHosts.toList() }
            .map(String::lowercase)
            .toSet()
        if (snapshot.forwardTargets.isEmpty() || logicalHosts.isEmpty()) return null
        PersistedWattToolkitWorkshopRoute(
            route = WattToolkitWorkshopRoute(
                logicalHosts = logicalHosts,
                forwardTargets = snapshot.forwardTargets,
                ignoreSslCertVerification = snapshot.ignoreSslCertVerification,
                fakeServerName = snapshot.fakeServerName,
            ),
            cachedAtMs = snapshot.cachedAtMs,
        )
    }.getOrNull()

    override fun save(route: PersistedWattToolkitWorkshopRoute) {
        runCatching {
            file.parentFile?.mkdirs()
            val snapshot = PersistedWattToolkitWorkshopRouteSnapshot(
                cachedAtMs = route.cachedAtMs,
                logicalHosts = route.route.logicalHosts.sorted(),
                forwardTargets = route.route.forwardTargets,
                ignoreSslCertVerification = route.route.ignoreSslCertVerification,
                fakeServerName = route.route.fakeServerName,
            )
            val tempFile = File.createTempFile(file.name, ".tmp", file.parentFile ?: file.absoluteFile.parentFile)
            tempFile.writeText(json.encodeToString(snapshot))
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        }
    }

    override fun clear() {
        runCatching { if (file.isFile) file.delete() }
    }
}

internal class WattToolkitWorkshopRouteResolver(
    private val routeProfile: WattToolkitRouteProfile,
    private val client: OkHttpClient = defaultWattToolkitRouteClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val projectGroupsUrl: HttpUrl = WATT_ACCELERATOR_PROJECTGROUPS_URL.toHttpUrl(),
    private val routeStore: WattToolkitWorkshopRouteStore = NoOpWattToolkitWorkshopRouteStore,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val normalizedSupportedHosts = routeProfile.supportedHosts.map(String::lowercase).toSet()
    @Volatile private var cachedRoute: WattToolkitWorkshopRoute? = null
    @Volatile private var cachedAtMs = 0L
    @Volatile private var persistedRouteLoaded = false

    fun supports(host: String): Boolean = host.lowercase() in normalizedSupportedHosts

    fun allowsUnsafeHostnameBypass(host: String): Boolean =
        cachedRoute?.shouldBypassHostnameVerification(host) == true

    fun resolveRouteForHost(host: String): WattToolkitWorkshopRoute? {
        val normalizedHost = host.lowercase()
        if (normalizedHost !in normalizedSupportedHosts) return null
        val now = nowProvider()
        synchronized(lock) {
            restorePersistedRouteLocked()
            val cached = cachedRoute
            if (cached != null && cached.matchesLogicalHost(normalizedHost) && now - cachedAtMs < ROUTE_CACHE_TTL_MS) {
                return cached
            }

            // The built-in route makes the first Workshop request fast and
            // avoids blocking the page on a route-config request.
            val bootstrap = defaultBootstrapRouteForProfile(routeProfile)
                ?.takeIf { it.matchesLogicalHost(normalizedHost) }
            if (bootstrap != null) {
                persistResolvedRouteLocked(bootstrap, now)
                return bootstrap
            }
        }

        return fetchAndPersist(normalizedHost, now)
    }

    fun refreshRouteForHost(host: String): WattToolkitWorkshopRoute? {
        val normalizedHost = host.lowercase()
        if (normalizedHost !in normalizedSupportedHosts) return null
        synchronized(lock) {
            cachedRoute = null
            cachedAtMs = 0L
            persistedRouteLoaded = true
            routeStore.clear()
        }
        return fetchAndPersist(normalizedHost, nowProvider())
            ?: defaultBootstrapRouteForProfile(routeProfile)?.takeIf { it.matchesLogicalHost(normalizedHost) }
    }

    private fun fetchAndPersist(
        normalizedHost: String,
        now: Long,
    ): WattToolkitWorkshopRoute? {
        val fetched = runCatching { fetchSupportedRoute() }.getOrNull()
            ?.takeIf { it.matchesLogicalHost(normalizedHost) }
            ?: return null
        synchronized(lock) { persistResolvedRouteLocked(fetched, now) }
        return fetched
    }

    private fun restorePersistedRouteLocked() {
        if (persistedRouteLoaded) return
        persistedRouteLoaded = true
        val persisted = routeStore.load() ?: return
        if (persisted.route.logicalHosts.intersect(normalizedSupportedHosts).isEmpty()) return
        cachedRoute = persisted.route
        cachedAtMs = persisted.cachedAtMs
    }

    private fun persistResolvedRouteLocked(route: WattToolkitWorkshopRoute, cachedAtMs: Long) {
        cachedRoute = route
        this.cachedAtMs = cachedAtMs
        routeStore.save(PersistedWattToolkitWorkshopRoute(route, cachedAtMs))
    }

    private fun fetchSupportedRoute(): WattToolkitWorkshopRoute {
        val request = Request.Builder()
            .url(projectGroupsUrl)
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", JSON_MEDIA_TYPE.toString())
            .header("User-Agent", "WorkshopOnAndroid/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Watt Toolkit route request failed: HTTP ${response.code}")
            val payload = response.body?.string().orEmpty()
            val decoded = json.decodeFromString<WattAccelerateResponse>(payload)
            val project = decoded.groups.asSequence()
                .flatMap { flattenProjects(it.items).asSequence() }
                .filter { it.checked }
                .firstOrNull { candidate ->
                    candidate.parseLogicalHosts().any { configuredHost ->
                        normalizedSupportedHosts.any { supportedHost ->
                            wattHostPatternMatches(configuredHost, supportedHost)
                        }
                    }
                }
                ?: error("Watt Toolkit route was not found for hosts=${normalizedSupportedHosts.joinToString(",")}")
            if (project.proxyType != 0) {
                error("Unsupported Watt Toolkit route type: ${project.proxyType}")
            }
            val logicalHosts = project.parseLogicalHosts()
                .filterTo(LinkedHashSet()) { normalizedSupportedHosts.contains(it) }
                .ifEmpty { normalizedSupportedHosts.toCollection(LinkedHashSet()) }
            val targets = project.forwardDomainNames.split(';')
                .map(String::trim)
                .filter(String::isNotEmpty)
            if (targets.isEmpty()) error("Watt Toolkit route has no forward target")
            return WattToolkitWorkshopRoute(
                logicalHosts = logicalHosts,
                forwardTargets = targets,
                ignoreSslCertVerification = project.ignoreSslCertVerification,
                fakeServerName = project.fakeServerName.trim(),
            )
        }
    }

    private fun flattenProjects(items: List<WattAccelerateProject>): List<WattAccelerateProject> = buildList {
        items.forEach {
            add(it)
            addAll(flattenProjects(it.items))
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ROUTE_CACHE_TTL_MS = 30L * 60L * 1_000L
    }
}

internal data class WattToolkitRouteProfile(
    val name: String,
    val cacheFileName: String,
    val supportedHosts: Set<String>,
    val bootstrapForwardTargets: List<String>,
    val bootstrapFakeServerName: String = "",
    val bootstrapIgnoreSslCertVerification: Boolean = false,
)

internal val SteamCommunityWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-community",
    cacheFileName = "watt-route-cache-v4.json",
    supportedHosts = setOf("steamcommunity.com", "www.steamcommunity.com"),
    bootstrapForwardTargets = listOf("https://www.valvesoftware.com"),
    bootstrapFakeServerName = "www.valvesoftware.com",
)

internal val SteamStoreWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-store",
    cacheFileName = "watt-store-route-cache-v4.json",
    supportedHosts = setOf(
        "api.steampowered.com",
        "store.steampowered.com",
        "help.steampowered.com",
        "login.steampowered.com",
        "checkout.steampowered.com",
    ),
    bootstrapForwardTargets = listOf("steamstore.rmbgame.net"),
    bootstrapFakeServerName = "steamstore-a.akamaihd.net",
)

internal val SteamImageWattToolkitRouteProfile = WattToolkitRouteProfile(
    name = "steam-image",
    cacheFileName = "watt-image-route-cache-v1.json",
    supportedHosts = setOf(
        "steamcdn-a.akamaihd.net",
        "steamuserimages-a.akamaihd.net",
        "cdn.akamai.steamstatic.com",
        "community.akamai.steamstatic.com",
        "avatars.akamai.steamstatic.com",
        "store.akamai.steamstatic.com",
        "avatars.fastly.steamstatic.com",
        "images.steamusercontent.com",
    ),
    bootstrapForwardTargets = listOf("https://steamimage.rmbgame.net"),
)

internal fun defaultBootstrapRouteForProfile(routeProfile: WattToolkitRouteProfile): WattToolkitWorkshopRoute? =
    routeProfile.bootstrapForwardTargets.takeIf(List<String>::isNotEmpty)?.let { forwardTargets ->
        WattToolkitWorkshopRoute(
            logicalHosts = routeProfile.supportedHosts.map(String::lowercase).toSet(),
            forwardTargets = forwardTargets,
            ignoreSslCertVerification = routeProfile.bootstrapIgnoreSslCertVerification,
            fakeServerName = routeProfile.bootstrapFakeServerName,
        )
    }

internal fun defaultWattToolkitRouteClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .proxy(Proxy.NO_PROXY)
        .protocols(listOf(Protocol.HTTP_1_1))
        .trustWattToolkitForwardCertificates()
        .hostnameVerifier(WattToolkitRouteHostnameVerifier)
        .build()

internal fun OkHttpClient.Builder.trustWattToolkitForwardCertificates(): OkHttpClient.Builder = apply {
    val trustManager = WattToolkitForwardTrustManager
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), null)
    }
    sslSocketFactory(sslContext.socketFactory, trustManager)
}

private fun Throwable.isRetryableDirectAccessFailure(): Boolean = this is IOException || cause?.isRetryableDirectAccessFailure() == true

private fun Response.isRetryableForwardedFailure(
    logicalRequest: Request,
    route: WattToolkitWorkshopRoute,
): Boolean = route.matchesLogicalHost(logicalRequest.url.host) &&
    (code == 403 || code == 404 || code == 421 || code == 502 || code == 503 || code == 504 || code in 521..525)

private fun Response.redirectTarget(
    logicalUrl: HttpUrl,
    route: WattToolkitWorkshopRoute,
): HttpUrl? {
    if (code !in REDIRECT_RESPONSE_CODES) return null
    val location = header("Location")?.trim().orEmpty()
    if (location.isBlank()) return null
    return logicalUrl.resolve(location)?.let { resolved ->
        route.normalizeLogicalUrl(resolved, logicalUrl.host)
    }
}

private fun parseHosts(vararg hostGroups: String): Set<String> = hostGroups.asSequence()
    .flatMap { it.split(';').asSequence() }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .mapNotNull { host ->
        when {
            "://" in host -> runCatching { host.toHttpUrl().host.lowercase() }.getOrNull()
            host == "*" || host.count { it == '*' } > 1 -> null
            '*' in host && !host.startsWith("*.") -> null
            else -> host.lowercase()
        }
    }
    .toSet()

private fun wattHostPatternMatches(pattern: String, host: String): Boolean {
    val normalizedPattern = pattern.lowercase()
    val normalizedHost = host.lowercase()
    if (normalizedPattern == normalizedHost) return true
    val wildcardSuffix = normalizedPattern.removePrefix("*.")
    return normalizedPattern.startsWith("*.") &&
        (normalizedHost == wildcardSuffix || normalizedHost.endsWith(".$wildcardSuffix"))
}

private fun WattAccelerateProject.parseLogicalHosts(): Set<String> =
    parseHosts(matchDomainNames, listenDomainNames)

private object WattToolkitForwardTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private object WattToolkitRouteHostnameVerifier : HostnameVerifier {
    private val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    override fun verify(hostname: String, session: SSLSession): Boolean =
        hostname.equals(WATT_ACCELERATOR_HOST, ignoreCase = true) || defaultVerifier.verify(hostname, session)
}

@Serializable
private data class WattAccelerateResponse(
    @SerialName("🦓") val groups: List<WattAccelerateGroup> = emptyList(),
)

@Serializable
private data class WattAccelerateGroup(
    @SerialName("Items") val items: List<WattAccelerateProject> = emptyList(),
)

@Serializable
private data class WattAccelerateProject(
    @SerialName("MatchDomainNames") val matchDomainNames: String = "",
    @SerialName("ListenDomainNames") val listenDomainNames: String = "",
    @SerialName("ForwardDomainNames") val forwardDomainNames: String = "",
    @SerialName("ProxyType") val proxyType: Int = -1,
    @SerialName("IgnoreSSLCertVerification") val ignoreSslCertVerification: Boolean = false,
    @SerialName("FakeServerName") val fakeServerName: String = "",
    @SerialName("Checked") val checked: Boolean = true,
    @SerialName("Items") val items: List<WattAccelerateProject> = emptyList(),
)

@Serializable
private data class PersistedWattToolkitWorkshopRouteSnapshot(
    val cachedAtMs: Long = 0L,
    val logicalHosts: List<String> = emptyList(),
    val forwardTargets: List<String> = emptyList(),
    val ignoreSslCertVerification: Boolean = false,
    val fakeServerName: String = "",
)

private const val WATT_ACCELERATOR_HOST = "api.steampp.net"
private const val WATT_ACCELERATOR_PROJECTGROUPS_URL = "https://$WATT_ACCELERATOR_HOST/accelerator/projectgroups"
private const val MAX_FOLLOW_UPS = 10
private const val HTTP_METHOD_GET = "GET"
private const val HTTP_METHOD_HEAD = "HEAD"
private const val HTTP_TEMP_REDIRECT = 307
private const val HTTP_PERM_REDIRECT = 308
private val REDIRECT_RESPONSE_CODES = setOf(300, 301, 302, 303, HTTP_TEMP_REDIRECT, HTTP_PERM_REDIRECT)
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
