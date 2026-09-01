/* Adapted from WorkshopAndroidDownloader (Apache-2.0). */
package com.zomdroid.workshop.auth

import android.content.Context
import android.util.Log
import com.zomdroid.workshop.SteamLanguagePreference
import com.zomdroid.workshop.steam.protocol.SteamAccountSession
import com.zomdroid.workshop.steam.protocol.SteamAuthPollResult
import com.zomdroid.workshop.steam.protocol.SteamAuthenticationClient
import com.zomdroid.workshop.steam.protocol.SteamAuthenticationException
import com.zomdroid.workshop.steam.protocol.SteamAuthSessionDetails
import com.zomdroid.workshop.steam.protocol.SteamCredentialAuthSession
import com.zomdroid.workshop.steam.protocol.SteamDirectoryClient
import com.zomdroid.workshop.steam.protocol.SteamGuardChallenge
import com.zomdroid.workshop.steam.protocol.SteamGuardChallengeType
import com.zomdroid.workshop.steam.protocol.SteamPublishedFileClient
import com.zomdroid.workshop.steam.protocol.SteamPublishedFileQuery
import com.zomdroid.workshop.steam.protocol.SteamPublishedFileQueryResult
import com.zomdroid.workshop.steam.protocol.SteamWebAccessTokens
import com.zomdroid.workshop.steam.protocol.applyDefaultHttpTimeouts
import com.zomdroid.workshop.steam.protocol.applySteamHttpCompatibility
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class SteamAuthRepository(context: Context) {
    private val appContext = context.applicationContext
    private val steamClientIdentity = SteamClientIdentity(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val authMutex = Mutex()
    private val tokenMutex = Mutex()
    private val prefs by lazy {
        createEncryptedPrefsOrFallback(
            context = appContext,
            encryptedPrefsName = PREFS_NAME,
            fallbackPrefsName = FALLBACK_PREFS_NAME,
            storageLabel = "Steam authentication state",
        )
    }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .applyDefaultHttpTimeouts()
            .applySteamHttpCompatibility()
            .build()
    }
    private val directoryClient by lazy { SteamDirectoryClient(httpClient) }
    private val authenticationClient by lazy {
        SteamAuthenticationClient(
            directoryClient = directoryClient,
            sessionFactory = { steamClientIdentity.createSession(httpClient) },
        )
    }
    private val publishedFileClient by lazy {
        SteamPublishedFileClient(
            directoryClient = directoryClient,
            sessionFactory = { steamClientIdentity.createSession(httpClient) },
        )
    }

    @Volatile private var pendingAuthSession: SteamCredentialAuthSession? = null
    @Volatile private var pendingReplaceAccountId: String? = null

    init {
        clearLegacyCookieOnlyState()
    }

    fun activeAccountId(): String? = loadState().activeAccountId

    fun loadSnapshot(): SteamAccountsSnapshot {
        val state = loadState()
        return SteamAccountsSnapshot(
            accounts = state.accounts.sortedBy { it.accountName.lowercase() }.map { account ->
                SteamAccountSummary(
                    accountId = account.accountId,
                    accountName = account.accountName,
                    steamId = account.steamId,
                    isActive = account.accountId == state.activeAccountId,
                    requiresReauthentication = account.requiresReauthentication,
                )
            },
            activeAccountId = state.activeAccountId,
        )
    }

    fun currentDownloadBinding(): SteamDownloadBinding =
        loadSnapshot().activeAccount?.let { SteamDownloadBinding(it.accountId, it.accountName) }
            ?: SteamDownloadBinding()

    fun accountSessionFor(accountId: String?): SteamAccountSession? = accountId
        ?.let { id -> loadState().accounts.firstOrNull { it.accountId == id } }
        ?.takeUnless { it.requiresReauthentication }
        ?.toProtocolSession(steamClientIdentity.machineName)

    fun activeAccountRequiresReauthentication(): Boolean =
        loadSnapshot().activeAccount?.requiresReauthentication == true

    suspend fun beginSignIn(
        username: String,
        password: String,
        replaceAccountId: String? = null,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        debugLogger.log("Repository: beginSignIn username=${username.maskForLog()} passwordLength=${password.length}.")
        pendingAuthSession?.close()
        pendingAuthSession = null
        pendingReplaceAccountId = replaceAccountId
        val authSession = authenticationClient.beginAuthSession(
            details = SteamAuthSessionDetails(
                username = username,
                password = password,
                guardData = storedGuardDataFor(username, replaceAccountId),
                isPersistentSession = true,
                deviceFriendlyName = steamClientIdentity.machineName,
            ),
            debugLogger = debugLogger,
        )
        pendingAuthSession = authSession
        val challenge = authSession.challenges.firstOrNull()
        when {
            challenge == null || challenge.type == SteamGuardChallengeType.None -> finalizePendingAuth(debugLogger)
            challenge.type == SteamGuardChallengeType.DeviceConfirmation ||
                challenge.type == SteamGuardChallengeType.EmailConfirmation ->
                SteamSignInStep.AwaitingConfirmation(challenge)
            challenge.type == SteamGuardChallengeType.EmailCode ||
                challenge.type == SteamGuardChallengeType.DeviceCode ->
                SteamSignInStep.RequiresGuardCode(challenge)
            else -> throw IOException("Unsupported Steam Guard challenge: ${challenge.type}")
        }
    }

    suspend fun signInWithRefreshToken(
        refreshToken: String,
        accountNameHint: String? = null,
        replaceAccountId: String? = null,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        pendingAuthSession?.close()
        pendingAuthSession = null
        pendingReplaceAccountId = null
        val steamId = parseSteamJwtInfo(refreshToken).steamId
            ?: throw IOException("Unable to parse Steam refresh token")
        val state = loadState()
        val existing = state.accounts.firstOrNull { it.accountId == replaceAccountId || it.steamId == steamId }
        val accountName = accountNameHint?.trim()?.takeIf(String::isNotBlank)
            ?: existing?.accountName ?: "Steam $steamId"
        val tokens = authenticationClient.generateAccessTokenForApp(
            account = SteamAccountSession(accountName, steamId, refreshToken, machineName = steamClientIdentity.machineName),
            allowRenewal = true,
            debugLogger = debugLogger,
        )
        val account = persistAccount(
            state = state,
            accountName = accountName,
            steamId = steamId,
            refreshToken = tokens.refreshToken ?: refreshToken,
            accessToken = tokens.accessToken,
            replaceAccountId = replaceAccountId,
        )
        SteamSignInStep.Success(account, loadSnapshot())
    }

    suspend fun submitPendingGuardCode(
        code: String,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock {
        val session = pendingAuthSession ?: throw IOException("No pending Steam sign-in session")
        val challenge = session.challenges.firstOrNull()
            ?: throw IOException("Steam did not provide a guard challenge")
        session.submitGuardCode(challenge.type, code)
        finalizePendingAuth(debugLogger)
    }

    suspend fun waitForPendingConfirmation(
        debugLogger: ((String) -> Unit)? = null,
    ): SteamSignInStep = authMutex.withLock { finalizePendingAuth(debugLogger) }

    fun cancelPendingSignIn() {
        pendingReplaceAccountId = null
        pendingAuthSession?.close()
        pendingAuthSession = null
    }

    fun setActiveAccount(accountId: String?) {
        val state = loadState()
        saveState(state.copy(activeAccountId = accountId?.takeIf { id -> state.accounts.any { it.accountId == id } }))
    }

    fun removeAccount(accountId: String) {
        val state = loadState()
        val account = state.accounts.firstOrNull { it.accountId == accountId } ?: return
        runCatching {
            parseSteamJwtInfo(account.refreshToken).tokenId?.let { tokenId ->
                runBlocking {
                    authenticationClient.revokeRefreshToken(
                        account.toProtocolSession(steamClientIdentity.machineName),
                        tokenId,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "Steam refresh token revoke failed during account removal", it) }
        saveState(
            state.copy(
                accounts = state.accounts.filterNot { it.accountId == accountId },
                activeAccountId = state.activeAccountId.takeUnless { it == accountId },
            ),
        )
    }

    fun markAccountRequiresReauthentication(accountId: String) = updateAccount(accountId) {
        it.copy(requiresReauthentication = true, webAccessToken = null, webAccessTokenExpEpochSeconds = null)
    }

    suspend fun cookieHeaderForAccount(url: HttpUrl, accountId: String?): String? =
        projectedCookiesForAccount(url, accountId).takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.name}=${it.value}" }

    suspend fun projectedCookiesForAccount(url: HttpUrl, accountId: String?): List<Cookie> {
        if (!url.host.isSteamDomain()) return emptyList()
        val context = webLoginContextForAccount(accountId) ?: return emptyList()
        return listOf(
            Cookie.Builder().name("steamLoginSecure")
                .value(buildSteamLoginSecureCookieValue(context.steamId, context.accessToken))
                .domain(url.host).path("/").build(),
            Cookie.Builder().name("sessionid").value(context.sessionId)
                .domain(url.host).path("/").build(),
        )
    }

    suspend fun webLoginContextForAccount(accountId: String?): SteamWebLoginContext? {
        val resolvedId = accountId ?: return null
        val account = ensureProjectedWebSessionState(resolvedId) ?: return null
        if (account.requiresReauthentication) return null
        val tokens = ensureFreshAccessToken(account.accountId) ?: return null
        return SteamWebLoginContext(
            steamId = account.steamId,
            accessToken = tokens.accessToken,
            sessionId = account.webSessionId ?: generateSteamWebSessionId(),
        )
    }

    fun blockingCookieHeaderFor(url: HttpUrl, accountId: String?): String? = runBlocking {
        cookieHeaderForAccount(url, accountId)
    }

    fun blockingProjectedCookiesFor(url: HttpUrl, accountId: String?): List<Cookie> = runBlocking {
        projectedCookiesForAccount(url, accountId)
    }

    suspend fun queryPublishedFiles(
        accountId: String?,
        query: SteamPublishedFileQuery,
    ): SteamPublishedFileQueryResult? = accountSessionFor(accountId)?.let {
        publishedFileClient.queryFiles(it, query)
    }

    private suspend fun finalizePendingAuth(debugLogger: ((String) -> Unit)?): SteamSignInStep {
        val session = pendingAuthSession ?: throw IOException("No pending Steam sign-in session")
        val replaceAccountId = pendingReplaceAccountId
        return try {
            val result = session.awaitResult()
            val account = persistAccount(result, replaceAccountId)
            SteamSignInStep.Success(account, loadSnapshot())
        } finally {
            session.close()
            pendingAuthSession = null
            pendingReplaceAccountId = null
        }
    }

    private suspend fun ensureFreshAccessToken(accountId: String): SteamWebAccessTokens? = tokenMutex.withLock {
        val account = loadState().accounts.firstOrNull { it.accountId == accountId } ?: return null
        if (account.requiresReauthentication) return null
        val now = System.currentTimeMillis() / 1000L
        if (!account.webAccessToken.isNullOrBlank() &&
            account.webAccessTokenExpEpochSeconds != null &&
            account.webAccessTokenExpEpochSeconds - TOKEN_REFRESH_WINDOW_SECONDS > now
        ) return SteamWebAccessTokens(account.webAccessToken)

        runCatching {
            authenticationClient.generateAccessTokenForApp(
                account.toProtocolSession(steamClientIdentity.machineName),
                allowRenewal = true,
            )
        }.onSuccess { tokens ->
            val accessToken = tokens.accessToken
            val info = parseSteamJwtInfo(accessToken)
            updateAccount(account.accountId) {
                it.copy(
                    refreshToken = tokens.refreshToken ?: it.refreshToken,
                    webAccessToken = accessToken,
                    webAccessTokenExpEpochSeconds = info.expiresAtEpochSeconds,
                    requiresReauthentication = false,
                )
            }
        }.onFailure { error ->
            val definitive = error.steamAuthenticationResultCodeOrNull() in DEFINITIVE_REAUTHENTICATION_RESULT_CODES
            Log.w(TAG, "Steam web token refresh failed accountId=${account.accountId.maskForLog()} definitive=$definitive")
            updateAccount(account.accountId) {
                it.copy(
                    requiresReauthentication = definitive || it.requiresReauthentication,
                    webAccessToken = null,
                    webAccessTokenExpEpochSeconds = null,
                )
            }
        }.getOrNull()
    }

    private fun persistAccount(result: SteamAuthPollResult, replaceAccountId: String?): SteamAccountSummary =
        persistAccount(
            state = loadState(),
            accountName = result.accountName,
            steamId = result.steamId,
            refreshToken = result.refreshToken,
            accessToken = result.accessToken,
            replaceAccountId = replaceAccountId,
            guardDataOverride = result.newGuardData,
        )

    private fun persistAccount(
        state: StoredSteamState,
        accountName: String,
        steamId: Long,
        refreshToken: String,
        accessToken: String,
        replaceAccountId: String?,
        guardDataOverride: String? = null,
    ): SteamAccountSummary {
        val existing = state.accounts.firstOrNull { it.accountId == replaceAccountId || it.steamId == steamId }
        val accountId = existing?.accountId ?: UUID.randomUUID().toString()
        val info = parseSteamJwtInfo(accessToken)
        val next = StoredSteamAccount(
            accountId = accountId,
            accountName = accountName,
            steamId = steamId,
            refreshToken = refreshToken,
            guardData = guardDataOverride ?: existing?.guardData,
            webAccessToken = accessToken,
            webAccessTokenExpEpochSeconds = info.expiresAtEpochSeconds,
            webSessionId = existing?.webSessionId ?: generateSteamWebSessionId(),
        )
        saveState(
            state.copy(
                accounts = state.accounts.filterNot { it.accountId == accountId || it.steamId == steamId } + next,
                activeAccountId = accountId,
            ),
        )
        return loadSnapshot().accounts.first { it.accountId == accountId }
    }

    private fun updateAccount(accountId: String, transform: (StoredSteamAccount) -> StoredSteamAccount) {
        val state = loadState()
        saveState(state.copy(accounts = state.accounts.map { if (it.accountId == accountId) transform(it) else it }))
    }

    private fun storedGuardDataFor(username: String, replaceAccountId: String?): String? = loadState().accounts
        .firstOrNull { it.accountId == replaceAccountId || it.accountName.equals(username, ignoreCase = true) }
        ?.guardData

    private fun ensureProjectedWebSessionState(accountId: String): StoredSteamAccount? {
        val state = loadState()
        val account = state.accounts.firstOrNull { it.accountId == accountId } ?: return null
        if (!account.webSessionId.isNullOrBlank()) return account
        val sessionId = generateSteamWebSessionId()
        saveState(state.copy(accounts = state.accounts.map {
            if (it.accountId == accountId) it.copy(webSessionId = sessionId) else it
        }))
        return account.copy(webSessionId = sessionId)
    }

    private fun clearLegacyCookieOnlyState() {
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_COMMUNITY_COOKIE_HEADER)
            .remove(KEY_STORE_COOKIE_HEADER)
            .remove(KEY_API_COOKIE_HEADER)
            .apply()
    }

    private fun loadState(): StoredSteamState {
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyStoredState()
        return runCatching { json.decodeFromString<StoredSteamState>(raw) }.getOrElse {
            Log.w(TAG, "Failed to decode stored Steam authentication state", it)
            emptyStoredState()
        }
    }

    private fun emptyStoredState() = StoredSteamState()

    private fun saveState(state: StoredSteamState) {
        prefs.edit().putString(KEY_ACCOUNTS_JSON, json.encodeToString(state)).apply()
    }

    private companion object {
        private const val TAG = "SteamAuth"
        private const val PREFS_NAME = "steam_accounts_secure"
        private const val FALLBACK_PREFS_NAME = "steam_accounts_secure_fallback"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val TOKEN_REFRESH_WINDOW_SECONDS = 15 * 60L
        private const val LEGACY_PREFS_NAME = "steam_auth"
        private const val KEY_COMMUNITY_COOKIE_HEADER = "community_cookie_header"
        private const val KEY_STORE_COOKIE_HEADER = "store_cookie_header"
        private const val KEY_API_COOKIE_HEADER = "api_cookie_header"
    }
}

class SteamCookieInterceptor(
    private val authRepository: SteamAuthRepository,
    private val accountIdProvider: (() -> String?)? = null,
    private val fallbackToActiveAccount: Boolean = true,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val accountId = accountIdProvider?.invoke()
            ?: authRepository.activeAccountId().takeIf { fallbackToActiveAccount }
        val cookieHeader = authRepository.blockingCookieHeaderFor(chain.request().url, accountId)
        val request = if (cookieHeader.isNullOrBlank()) chain.request() else chain.request().newBuilder()
            .header("Cookie", cookieHeader).build()
        return chain.proceed(request)
    }
}

class SteamLanguageInterceptor(
    private val languagePreferenceProvider: () -> SteamLanguagePreference,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.isSteamDomain()) return chain.proceed(request)
        return chain.proceed(request.newBuilder()
            .header("Accept-Language", languagePreferenceProvider().acceptLanguageValue)
            .build())
    }
}

@Serializable
private data class StoredSteamState(
    @SerialName("accounts") val accounts: List<StoredSteamAccount> = emptyList(),
    @SerialName("activeAccountId") val activeAccountId: String? = null,
)

@Serializable
private data class StoredSteamAccount(
    @SerialName("accountId") val accountId: String,
    @SerialName("accountName") val accountName: String,
    @SerialName("steamId") val steamId: Long,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("guardData") val guardData: String? = null,
    @SerialName("webAccessToken") val webAccessToken: String? = null,
    @SerialName("webAccessTokenExpEpochSeconds") val webAccessTokenExpEpochSeconds: Long? = null,
    @SerialName("webSessionId") val webSessionId: String? = null,
    @SerialName("requiresReauthentication") val requiresReauthentication: Boolean = false,
)

private fun StoredSteamAccount.toProtocolSession(machineName: String) = SteamAccountSession(
    accountName = accountName,
    steamId = steamId,
    refreshToken = refreshToken,
    machineName = machineName,
)

private fun Throwable.steamAuthenticationResultCodeOrNull(): Int? {
    var current: Throwable? = this
    while (current != null) {
        if (current is SteamAuthenticationException) return current.resultCode
        current = current.cause
    }
    return null
}

private val DEFINITIVE_REAUTHENTICATION_RESULT_CODES = setOf(5, 8, 15, 63, 65, 66, 74, 85, 88)

internal fun String.isSteamDomain(): Boolean {
    val host = lowercase()
    return host == "steamcommunity.com" || host.endsWith(".steamcommunity.com") ||
        host == "steampowered.com" || host.endsWith(".steampowered.com")
}

private val steamWebSessionRandom = SecureRandom()

private fun generateSteamWebSessionId(): String {
    val bytes = ByteArray(12)
    steamWebSessionRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun String?.maskForLog(): String = this?.trim()?.takeIf(String::isNotBlank)?.let {
    if (it.length <= 2) "*".repeat(it.length) else "${it.first()}***${it.last()}"
} ?: "-"

private fun ((String) -> Unit)?.log(message: String) = this?.invoke(message)
