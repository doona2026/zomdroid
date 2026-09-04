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
    return WorkshopDirectAccessRuntime(resolvers, hostnameVerifier, directHttpClient)
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
        .hostnameVerifier(runtime.hostnameVerifier)
        .apply {
            runtime.resolvers.forEach { resolver ->
                addInterceptor(
                    WorkshopDirectAccessInterceptor(
                        routeResolver = resolver,
                        directCallFactory = runtime.directHttpClient,
                    ),
                )
            }
        }
        .build()
}
