/* Adapted from WorkshopAndroidDownloader (Apache-2.0). */
package com.zomdroid.workshop.steam.protocol

import com.google.protobuf.ByteString
import com.zomdroid.workshop.steam.proto.CAuthentication_AccessToken_GenerateForApp_Request
import com.zomdroid.workshop.steam.proto.CAuthentication_AccessToken_GenerateForApp_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_AllowedConfirmation
import com.zomdroid.workshop.steam.proto.CAuthentication_BeginAuthSessionViaCredentials_Request
import com.zomdroid.workshop.steam.proto.CAuthentication_BeginAuthSessionViaCredentials_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_DeviceDetails
import com.zomdroid.workshop.steam.proto.CAuthentication_GetPasswordRSAPublicKey_Request
import com.zomdroid.workshop.steam.proto.CAuthentication_GetPasswordRSAPublicKey_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_PollAuthSessionStatus_Request
import com.zomdroid.workshop.steam.proto.CAuthentication_PollAuthSessionStatus_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_RefreshToken_Revoke_Request
import com.zomdroid.workshop.steam.proto.CAuthentication_RefreshToken_Revoke_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request
import com.zomdroid.workshop.steam.proto.CAuthentication_UpdateAuthSessionWithSteamGuardCode_Response
import com.zomdroid.workshop.steam.proto.EAuthSessionGuardType
import com.zomdroid.workshop.steam.proto.EAuthTokenPlatformType
import com.zomdroid.workshop.steam.proto.ESessionPersistence
import com.zomdroid.workshop.steam.proto.ETokenRenewalType
import kotlinx.coroutines.delay
import java.io.Closeable
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher

class SteamAuthenticationClient(
    private val directoryClient: SteamDirectoryClient,
    private val sessionFactory: () -> SteamCmSession,
    private val serverLoader: suspend () -> List<CmServer> = { directoryClient.loadServers() },
) {
    suspend fun beginAuthSession(
        details: SteamAuthSessionDetails,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamCredentialAuthSession {
        val servers = serverLoader()
        val session = sessionFactory()
        try {
            debugLogger.log("Protocol: loaded ${servers.size} CM server candidate(s).")
            session.connect(servers)
            val publicKey = session.callServiceMethod(
                methodName = "Authentication.GetPasswordRSAPublicKey#1",
                request = CAuthentication_GetPasswordRSAPublicKey_Request.newBuilder()
                    .setAccountName(details.username)
                    .build(),
                parser = CAuthentication_GetPasswordRSAPublicKey_Response.parser(),
            )
            val encryptedPassword = encryptPassword(details.password, publicKey)
            val response = session.callServiceMethod(
                methodName = "Authentication.BeginAuthSessionViaCredentials#1",
                request = buildBeginAuthSessionRequest(details, encryptedPassword, publicKey.timestamp),
                parser = CAuthentication_BeginAuthSessionViaCredentials_Response.parser(),
            )
            val challenges = response.allowedConfirmationsList.map(::mapChallenge).sortedBy { it.type.sortOrder() }
            debugLogger.log(
                "Protocol: auth session started steamId=${response.steamid} " +
                    "intervalSeconds=${response.interval} challenges=${challenges.summaryForLog()}.",
            )
            return SteamCredentialAuthSession(
                session = session,
                steamId = response.steamid,
                clientId = response.clientId,
                requestId = response.requestId.toByteArray(),
                pollingIntervalMillis = (response.interval * 1_000f).toLong().coerceAtLeast(1_000L),
                challenges = challenges,
                debugLogger = debugLogger,
            )
        } catch (error: Throwable) {
            session.close()
            throw error.asAuthenticationException("Steam credential sign-in failed")
        }
    }

    suspend fun generateAccessTokenForApp(
        account: SteamAccountSession,
        allowRenewal: Boolean,
        debugLogger: ((String) -> Unit)? = null,
    ): SteamWebAccessTokens {
        val servers = serverLoader()
        return sessionFactory().use { session ->
            try {
                session.connectWithRefreshToken(servers, account)
                val response = session.callServiceMethod(
                    methodName = "Authentication.GenerateAccessTokenForApp#1",
                    request = CAuthentication_AccessToken_GenerateForApp_Request.newBuilder()
                        .setRefreshToken(account.refreshToken)
                        .setSteamid(account.steamId)
                        .setRenewalType(
                            if (allowRenewal) ETokenRenewalType.k_ETokenRenewalType_Allow
                            else ETokenRenewalType.k_ETokenRenewalType_None,
                        )
                        .build(),
                    parser = CAuthentication_AccessToken_GenerateForApp_Response.parser(),
                )
                debugLogger.log("Protocol: access token generated steamId=${account.steamId}.")
                SteamWebAccessTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken.takeIf(String::isNotBlank),
                )
            } catch (error: Throwable) {
                throw error.asAuthenticationException("Steam access token generation failed")
            }
        }
    }

    suspend fun revokeRefreshToken(
        account: SteamAccountSession,
        tokenId: ULong,
        debugLogger: ((String) -> Unit)? = null,
    ) {
        val servers = serverLoader()
        sessionFactory().use { session ->
            try {
                session.connectWithRefreshToken(servers, account)
                session.callServiceMethod(
                    methodName = "Authentication.RevokeRefreshToken#1",
                    request = CAuthentication_RefreshToken_Revoke_Request.newBuilder()
                        .setTokenId(tokenId.toLong())
                        .setSteamid(account.steamId)
                        .build(),
                    parser = CAuthentication_RefreshToken_Revoke_Response.parser(),
                )
                debugLogger.log("Protocol: refresh token revoked steamId=${account.steamId}.")
            } catch (error: Throwable) {
                throw error.asAuthenticationException("Steam refresh token revoke failed")
            }
        }
    }
}

class SteamCredentialAuthSession internal constructor(
    private val session: SteamCmSession,
    val steamId: Long,
    private val clientId: Long,
    private val requestId: ByteArray,
    val pollingIntervalMillis: Long,
    val challenges: List<SteamGuardChallenge>,
    private val debugLogger: ((String) -> Unit)? = null,
) : Closeable {
    suspend fun submitGuardCode(type: SteamGuardChallengeType, code: String) {
        try {
            session.callServiceMethod(
                methodName = "Authentication.UpdateAuthSessionWithSteamGuardCode#1",
                request = CAuthentication_UpdateAuthSessionWithSteamGuardCode_Request.newBuilder()
                    .setClientId(clientId)
                    .setSteamid(steamId)
                    .setCode(code)
                    .setCodeType(type.toProto())
                    .build(),
                parser = CAuthentication_UpdateAuthSessionWithSteamGuardCode_Response.parser(),
            )
        } catch (error: Throwable) {
            throw error.asAuthenticationException("Steam Guard code submission failed")
        }
    }

    suspend fun pollStatus(): SteamAuthPollResult? {
        try {
            val response = session.callServiceMethod(
                methodName = "Authentication.PollAuthSessionStatus#1",
                request = CAuthentication_PollAuthSessionStatus_Request.newBuilder()
                    .setClientId(clientId)
                    .setRequestId(ByteString.copyFrom(requestId))
                    .build(),
                parser = CAuthentication_PollAuthSessionStatus_Response.parser(),
            )
            if (response.refreshToken.isBlank()) return null
            return SteamAuthPollResult(
                steamId = steamId,
                accountName = response.accountName,
                refreshToken = response.refreshToken,
                accessToken = response.accessToken,
                newGuardData = response.newGuardData.takeIf(String::isNotBlank),
            )
        } catch (error: Throwable) {
            throw error.asAuthenticationException("Steam auth polling failed")
        }
    }

    suspend fun awaitResult(): SteamAuthPollResult {
        while (true) {
            pollStatus()?.let { return it }
            delay(pollingIntervalMillis)
        }
    }

    override fun close() = session.close()
}

private fun mapChallenge(source: CAuthentication_AllowedConfirmation) = SteamGuardChallenge(
    type = when (source.confirmationType) {
        EAuthSessionGuardType.k_EAuthSessionGuardType_None -> SteamGuardChallengeType.None
        EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode -> SteamGuardChallengeType.EmailCode
        EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode -> SteamGuardChallengeType.DeviceCode
        EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation -> SteamGuardChallengeType.DeviceConfirmation
        EAuthSessionGuardType.k_EAuthSessionGuardType_EmailConfirmation -> SteamGuardChallengeType.EmailConfirmation
        EAuthSessionGuardType.k_EAuthSessionGuardType_MachineToken -> SteamGuardChallengeType.MachineToken
        EAuthSessionGuardType.k_EAuthSessionGuardType_LegacyMachineAuth -> SteamGuardChallengeType.LegacyMachineAuth
        else -> SteamGuardChallengeType.Unknown
    },
    message = source.associatedMessage.takeIf(String::isNotBlank),
)

private fun SteamGuardChallengeType.sortOrder() = when (this) {
    SteamGuardChallengeType.None -> 0
    SteamGuardChallengeType.DeviceConfirmation -> 1
    SteamGuardChallengeType.DeviceCode -> 2
    SteamGuardChallengeType.EmailCode -> 3
    SteamGuardChallengeType.EmailConfirmation -> 4
    SteamGuardChallengeType.MachineToken -> 5
    SteamGuardChallengeType.LegacyMachineAuth -> 6
    SteamGuardChallengeType.Unknown -> 7
}

private fun SteamGuardChallengeType.toProto() = when (this) {
    SteamGuardChallengeType.None -> EAuthSessionGuardType.k_EAuthSessionGuardType_None
    SteamGuardChallengeType.EmailCode -> EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode
    SteamGuardChallengeType.DeviceCode -> EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode
    SteamGuardChallengeType.DeviceConfirmation -> EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation
    SteamGuardChallengeType.EmailConfirmation -> EAuthSessionGuardType.k_EAuthSessionGuardType_EmailConfirmation
    SteamGuardChallengeType.MachineToken -> EAuthSessionGuardType.k_EAuthSessionGuardType_MachineToken
    SteamGuardChallengeType.LegacyMachineAuth -> EAuthSessionGuardType.k_EAuthSessionGuardType_LegacyMachineAuth
    SteamGuardChallengeType.Unknown -> EAuthSessionGuardType.k_EAuthSessionGuardType_Unknown
}

private fun encryptPassword(password: String, publicKey: CAuthentication_GetPasswordRSAPublicKey_Response): String {
    val modulus = BigInteger(1, decodeHex(publicKey.publickeyMod))
    val exponent = BigInteger(1, decodeHex(publicKey.publickeyExp))
    val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
    return Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
        init(Cipher.ENCRYPT_MODE, key)
        Base64.getEncoder().encodeToString(doFinal(password.toByteArray(Charsets.UTF_8)))
    }
}

private fun decodeHex(value: String): ByteArray {
    val normalized = if (value.length % 2 == 0) value else "0$value"
    return ByteArray(normalized.length / 2) {
        val offset = it * 2
        normalized.substring(offset, offset + 2).toInt(16).toByte()
    }
}

private fun List<SteamGuardChallenge>.summaryForLog() = joinToString(",") { it.type.name }.ifBlank { "none" }

private fun ((String) -> Unit)?.log(message: String) = this?.invoke(message)

internal fun buildBeginAuthSessionRequest(
    details: SteamAuthSessionDetails,
    encryptedPassword: String,
    encryptionTimestamp: Long,
): CAuthentication_BeginAuthSessionViaCredentials_Request {
    val builder = CAuthentication_BeginAuthSessionViaCredentials_Request.newBuilder()
        .setAccountName(details.username)
        .setEncryptedPassword(encryptedPassword)
        .setEncryptionTimestamp(encryptionTimestamp)
        .setPersistence(
            if (details.isPersistentSession) ESessionPersistence.k_ESessionPersistence_Persistent
            else ESessionPersistence.k_ESessionPersistence_Ephemeral,
        )
        .setWebsiteId(details.websiteId)
        .setDeviceDetails(
            CAuthentication_DeviceDetails.newBuilder()
                .setDeviceFriendlyName(details.deviceFriendlyName)
                .setPlatformType(EAuthTokenPlatformType.k_EAuthTokenPlatformType_SteamClient)
                .setOsType(details.clientOsType)
                .build(),
        )
    details.guardData?.takeIf(String::isNotBlank)?.let(builder::setGuardData)
    return builder.build()
}

private fun Throwable.asAuthenticationException(prefix: String): SteamAuthenticationException = when (this) {
    is SteamAuthenticationException -> this
    is SteamServiceMethodException -> SteamAuthenticationException(
        resultCode = resultCode,
        message = buildSteamAuthenticationErrorMessage(prefix, resultCode, steamMessage),
        cause = this,
    )
    else -> SteamAuthenticationException(resultCode = 2, message = "$prefix: ${message.orEmpty()}", cause = this)
}

private fun String?.maskForLog(): String = this?.trim()?.takeIf(String::isNotBlank)?.let {
    if (it.length <= 2) "*".repeat(it.length) else "${it.first()}***${it.last()}"
} ?: "-"
