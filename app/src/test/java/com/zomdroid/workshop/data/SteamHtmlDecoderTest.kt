package com.zomdroid.workshop.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SteamHtmlDecoderTest {
    @Test
    fun apiListMarkersAreReadable() {
        val decoded = SteamHtmlDecoder.decodeWorkshopApiDescription(
            "[list][*]第一项[*]第二项[/list]",
        )

        assertThat(decoded).contains("• 第一项")
        assertThat(decoded).contains("• 第二项")
        assertThat(decoded).doesNotContain("闁")
        assertThat(decoded).doesNotContain("閳")
    }

    @Test
    fun htmlListMarkersAreReadable() {
        val decoded = SteamHtmlDecoder.decodeWorkshopHtmlDescription(
            "<ul><li>第一项</li><li>第二项</li></ul>",
        )

        assertThat(decoded).contains("• 第一项")
        assertThat(decoded).contains("• 第二项")
        assertThat(decoded).doesNotContain("闁")
        assertThat(decoded).doesNotContain("閳")
    }
}
