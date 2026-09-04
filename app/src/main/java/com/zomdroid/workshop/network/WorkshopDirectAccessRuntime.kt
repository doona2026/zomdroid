package com.zomdroid.workshop.network

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Protocol

internal data class WorkshopDirectAccessRuntime(
    val resolvers: List<WattToolkitWorkshopRouteResolver>,
    val hostnameVerifier: WorkshopDirectHostnameVerifier,
    val directHttpClient: OkHttpClient,
    val forwardDns: WattToolkitForwardDns,
)

private val defaultWorkshopRouteProfiles = listOf(
    SteamCommunityWattToolkitRouteProfile,
    SteamStoreWattToolkitRouteProfile,
    SteamImageWattToolkitRouteProfile,
)

internal fun createWorkshopDirectAccessRuntime(
    filesDir: File,
    routeProfiles: List<WattToolkitRouteProfile> = defaultWorkshopRouteProfiles,
): WorkshopDirectAccessRuntime {
    val forwardDns = WattToolkitForwardDns()
    val resolvers = routeProfiles.map { profile ->
        WattToolkitWorkshopRouteResolver(
            routeProfile = profile,
            routeStore = FileBackedWattToolkitWorkshopRouteStore(
                file = File(filesDir, "workshop/network/${profile.cacheFileName}"),
                fallbackLogicalHosts = profile.supportedHosts,
            ),
        )
    }
    val hostnameVerifier = WorkshopDirectHostnameVerifier { host ->
        resolvers.any { it.allowsUnsafeHostnameBypass(host) }
    }
    val directHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .hostnameVerifier(hostnameVerifier)
        .dns(forwardDns)
        .trustWattToolkitForwardCertificates()
        .followRedirects(false)
        .followSslRedirects(false)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()
    return WorkshopDirectAccessRuntime(
        resolvers = resolvers,
        hostnameVerifier = hostnameVerifier,
        directHttpClient = directHttpClient,
        forwardDns = forwardDns,
    )
}

internal fun createWorkshopCatalogHttpClient(context: Context): OkHttpClient {
    val runtime = createWorkshopDirectAccessRuntime(context.applicationContext.filesDir)
    val cacheDir = File(context.applicationContext.cacheDir, "workshop-catalog-http")
    return OkHttpClient.Builder()
        .cache(Cache(cacheDir, 5L * 1024L * 1024L))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addWorkshopDirectAccess(runtime)
        .build()
}

/**
 * Adds the same WorkshopAndroidDownloader-compatible direct route to any
 * Steam HTTP client, including the client used by the download engine.
 *
 * Keeping this as a builder extension is intentional: authenticated clients
 * can add their cookie interceptor before this extension, while the relay
 * request still strips Steam credentials in WorkshopDirectAccessInterceptor.
 */
internal fun OkHttpClient.Builder.addWorkshopDirectAccess(
    context: Context,
): OkHttpClient.Builder = addWorkshopDirectAccess(
    createWorkshopDirectAccessRuntime(context.applicationContext.filesDir),
)

internal fun OkHttpClient.Builder.addWorkshopDirectAccess(
    runtime: WorkshopDirectAccessRuntime,
): OkHttpClient.Builder = apply {
    hostnameVerifier(runtime.hostnameVerifier)
    runtime.resolvers.forEach { resolver ->
        addInterceptor(
            WorkshopDirectAccessInterceptor(
                routeResolver = resolver,
                directCallFactory = runtime.directHttpClient,
                forwardDns = runtime.forwardDns,
            ),
        )
    }
}
