package com.zomdroid.workshop.auth

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

class SteamTokenProjectionTest {
    @Test
    fun parsesSteamJwtIdentityAndExpiry() {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("{}".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"sub\":\"76561198000000001\",\"jti\":\"42\",\"exp\":2000000000}".toByteArray(),
        )

        val parsed = parseSteamJwtInfo("$header.$payload.signature")

        assertThat(parsed.steamId).isEqualTo(76561198000000001L)
        assertThat(parsed.tokenId).isEqualTo(42uL)
        assertThat(parsed.expiresAtEpochSeconds).isEqualTo(2000000000L)
    }

    @Test
    fun buildsSteamLoginSecureCookieWithoutExposingOtherFields() {
        assertThat(buildSteamLoginSecureCookie(76561198000000001L, "access-token"))
            .isEqualTo("steamLoginSecure=76561198000000001||access-token")
    }
}
