package com.zomdroid.workshop.steam.protocol

import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import com.zomdroid.workshop.steam.proto.CAuthentication_AccessToken_GenerateForApp_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_AllowedConfirmation
import com.zomdroid.workshop.steam.proto.CAuthentication_BeginAuthSessionViaCredentials_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_GetPasswordRSAPublicKey_Response
import com.zomdroid.workshop.steam.proto.CAuthentication_PollAuthSessionStatus_Response
import com.zomdroid.workshop.steam.proto.EAuthSessionGuardType
import java.security.KeyPairGenerator
import java.util.ArrayDeque
import java.security.interfaces.RSAPublicKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SteamAuthenticationProtocolTest {
    @Test
    fun beginAuthMapsNoneAndAllGuardChallengeTypesFromMockCm() {
        val mappings = listOf(
            EAuthSessionGuardType.k_EAuthSessionGuardType_None to SteamGuardChallengeType.None,
            EAuthSessionGuardType.k_EAuthSessionGuardType_EmailCode to SteamGuardChallengeType.EmailCode,
            EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceCode to SteamGuardChallengeType.DeviceCode,
            EAuthSessionGuardType.k_EAuthSessionGuardType_DeviceConfirmation to SteamGuardChallengeType.DeviceConfirmation,
        )

        mappings.forEach { (wireType, expectedType) ->
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
            val publicKey = keyPair.public as RSAPublicKey
            val rsa = CAuthentication_GetPasswordRSAPublicKey_Response.newBuilder()
                .setPublickeyMod(publicKey.modulus.toString(16).padStart(256, '0'))
                .setPublickeyExp(publicKey.publicExponent.toString(16).padStart(6, '0'))
                .setTimestamp(1234L)
                .build()
            val begin = CAuthentication_BeginAuthSessionViaCredentials_Response.newBuilder()
                .setClientId(7L)
                .setRequestId(ByteString.copyFrom(byteArrayOf(1, 2)))
                .setInterval(0f)
                .setSteamid(76561198000000001L)
                .addAllowedConfirmations(
                    CAuthentication_AllowedConfirmation.newBuilder()
                        .setConfirmationType(wireType)
                        .setAssociatedMessage("mock")
                        .build(),
                )
                .build()
            val mockCm = MockCmSession(
                responses = mapOf(
                    "Authentication.GetPasswordRSAPublicKey#1" to listOf(rsa),
                    "Authentication.BeginAuthSessionViaCredentials#1" to listOf(begin),
                ),
            )
            val client = testClient(mockCm)

            val auth = runBlocking {
                client.beginAuthSession(SteamAuthSessionDetails("user", "password"))
            }

            assertThat(auth.challenges.single().type).isEqualTo(expectedType)
            auth.close()
        }
    }

    @Test
    fun pollRetriesPendingMockCmSessionUntilTokensArrive() {
        val pending = CAuthentication_PollAuthSessionStatus_Response.getDefaultInstance()
        val completed = CAuthentication_PollAuthSessionStatus_Response.newBuilder()
            .setRefreshToken("refresh-token")
            .setAccessToken("access-token")
            .setAccountName("alice")
            .build()
        val mockCm = MockCmSession(
            responses = mapOf(
                "Authentication.PollAuthSessionStatus#1" to listOf(pending, completed),
            ),
        )
        val session = SteamCredentialAuthSession(
            session = mockCm,
            steamId = 76561198000000001L,
            clientId = 7L,
            requestId = byteArrayOf(1),
            pollingIntervalMillis = 1L,
            challenges = listOf(SteamGuardChallenge(SteamGuardChallengeType.DeviceConfirmation)),
        )

        val result = runBlocking { session.awaitResult() }

        assertThat(result.accountName).isEqualTo("alice")
        assertThat(result.refreshToken).isEqualTo("refresh-token")
    }

    @Test
    fun beginAuthConvertsMockCmServiceErrorToAuthenticationError() {
        val mockCm = MockCmSession(
            failures = mapOf(
                "Authentication.GetPasswordRSAPublicKey#1" to SteamServiceMethodException(
                    "Authentication.GetPasswordRSAPublicKey#1", 5, "invalid credentials",
                ),
            ),
        )

        val error = runCatching {
            runBlocking { testClient(mockCm).beginAuthSession(SteamAuthSessionDetails("user", "password")) }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(SteamAuthenticationException::class.java)
        assertThat((error as SteamAuthenticationException).resultCode).isEqualTo(5)
    }

    @Test
    fun accessTokenGenerationUsesRefreshTokenAndAllowRenewal() {
        val mockCm = MockCmSession(
            responses = mapOf(
                "Authentication.GenerateAccessTokenForApp#1" to listOf(
                    CAuthentication_AccessToken_GenerateForApp_Response.newBuilder()
                        .setAccessToken("access-token")
                        .setRefreshToken("new-refresh-token")
                        .build(),
                ),
            ),
        )
        val account = SteamAccountSession("alice", 76561198000000001L, "old-refresh-token")

        val tokens = runBlocking { testClient(mockCm).generateAccessTokenForApp(account, allowRenewal = true) }

        assertThat(tokens.accessToken).isEqualTo("access-token")
        assertThat(tokens.refreshToken).isEqualTo("new-refresh-token")
        val request = mockCm.requests.single().second as com.zomdroid.workshop.steam.proto.CAuthentication_AccessToken_GenerateForApp_Request
        assertThat(request.refreshToken).isEqualTo("old-refresh-token")
        assertThat(request.steamid).isEqualTo(account.steamId)
    }

    @Test
    fun beginAuthRequestCarriesPersistentDeviceAndGuardData() {
        val request = buildBeginAuthSessionRequest(
            details = SteamAuthSessionDetails(
                username = "test-user",
                password = "not-used-by-request-builder",
                guardData = "guard-data",
                isPersistentSession = true,
                deviceFriendlyName = "Android Workshop",
            ),
            encryptedPassword = "encrypted-password",
            encryptionTimestamp = 1234L,
        )

        assertThat(request.accountName).isEqualTo("test-user")
        assertThat(request.encryptedPassword).isEqualTo("encrypted-password")
        assertThat(request.encryptionTimestamp).isEqualTo(1234L)
        assertThat(request.guardData).isEqualTo("guard-data")
        assertThat(request.deviceDetails.deviceFriendlyName).isEqualTo("Android Workshop")
        assertThat(request.persistence).isEqualTo(
            com.zomdroid.workshop.steam.proto.ESessionPersistence.k_ESessionPersistence_Persistent,
        )
    }

    private fun testClient(session: MockCmSession) = SteamAuthenticationClient(
        directoryClient = SteamDirectoryClient(),
        sessionFactory = { session },
        serverLoader = { listOf(CmServer("mock-cm", "websockets")) },
    )

    private class MockCmSession(
        responses: Map<String, List<MessageLite>> = emptyMap(),
        private val failures: Map<String, Throwable> = emptyMap(),
    ) : SteamCmSession {
        private val responseQueues = responses.mapValues { ArrayDeque(it.value) }
        val requests = mutableListOf<Pair<String, MessageLite>>()
        override val currentSession = MutableStateFlow<SessionContext?>(null)
        override suspend fun connect(servers: List<CmServer>) = Unit
        override suspend fun connectAnonymous(servers: List<CmServer>) = error("not used")
        override suspend fun connectWithRefreshToken(servers: List<CmServer>, account: SteamAccountSession) =
            SessionContext(1, account.steamId, 0u, 30)
        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : MessageLite> callServiceMethod(
            methodName: String,
            request: MessageLite,
            parser: Parser<T>,
        ): T {
            requests += methodName to request
            failures[methodName]?.let { throw it }
            return (responseQueues[methodName]?.removeFirst() ?: error("No mock response for $methodName")) as T
        }
        override suspend fun requestDepotDecryptionKey(appId: UInt, depotId: UInt): ByteArray = error("not used")
        override fun close() = Unit
    }
}
