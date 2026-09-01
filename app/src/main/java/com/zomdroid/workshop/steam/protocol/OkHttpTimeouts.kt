/* Adapted from WorkshopAndroidDownloader (Apache-2.0); package/imports changed for Zomdroid. */
package com.zomdroid.workshop.steam.protocol

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol

const val DEFAULT_HTTP_TIMEOUT_SECONDS = 30L

fun OkHttpClient.Builder.applyDefaultHttpTimeouts(
    timeoutSeconds: Long = DEFAULT_HTTP_TIMEOUT_SECONDS,
): OkHttpClient.Builder =
    connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)

fun OkHttpClient.Builder.applySteamHttpCompatibility(): OkHttpClient.Builder =
    protocols(listOf(Protocol.HTTP_1_1))

fun newDefaultOkHttpClient(
    timeoutSeconds: Long = DEFAULT_HTTP_TIMEOUT_SECONDS,
): OkHttpClient =
    OkHttpClient.Builder()
        .applyDefaultHttpTimeouts(timeoutSeconds)
        .applySteamHttpCompatibility()
        .build()
