package com.zomdroid.workshop.auth

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.zomdroid.workshop.steam.protocol.SteamGuardChallenge

data class SteamAccountSummary(
    val accountId: String,
    val accountName: String,
    val steamId: Long,
    val isActive: Boolean,
    val requiresReauthentication: Boolean,
)

data class SteamAccountsSnapshot(
    val accounts: List<SteamAccountSummary> = emptyList(),
    val activeAccountId: String? = null,
) {
    val activeAccount: SteamAccountSummary?
        get() = accounts.firstOrNull { it.isActive }
}

data class SteamDownloadBinding(
    val accountId: String? = null,
    val accountName: String = "Anonymous",
)

sealed interface SteamSignInStep {
    data class RequiresGuardCode(val challenge: SteamGuardChallenge) : SteamSignInStep
    data class AwaitingConfirmation(val challenge: SteamGuardChallenge) : SteamSignInStep
    data class Success(val account: SteamAccountSummary, val snapshot: SteamAccountsSnapshot) : SteamSignInStep
}

data class SteamWebLoginContext(
    val steamId: Long,
    val accessToken: String,
    val sessionId: String,
)

data class ParsedJwtInfo(
    val steamId: Long? = null,
    val tokenId: ULong? = null,
    val expiresAtEpochSeconds: Long? = null,
)

fun buildSteamLoginSecureCookieValue(steamId: Long, accessToken: String): String = "$steamId||$accessToken"

fun buildSteamLoginSecureCookie(steamId: Long, accessToken: String): String =
    "steamLoginSecure=${buildSteamLoginSecureCookieValue(steamId, accessToken)}"

fun parseSteamJwtInfo(token: String): ParsedJwtInfo {
    val parts = token.split('.')
    if (parts.size < 2) return ParsedJwtInfo()
    val payload = runCatching {
        val normalized = parts[1].replace('-', '+').replace('_', '/')
            .padEnd((parts[1].length + 3) / 4 * 4, '=')
        Base64.getDecoder().decode(normalized).decodeToString()
    }.getOrNull() ?: return ParsedJwtInfo()
    val json = runCatching { Json.Default.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return ParsedJwtInfo()
    return ParsedJwtInfo(
        steamId = json["sub"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        tokenId = json["jti"]?.jsonPrimitive?.contentOrNull?.toULongOrNull(),
        expiresAtEpochSeconds = json["exp"]?.jsonPrimitive?.longOrNull,
    )
}
