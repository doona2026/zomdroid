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

    @Test
    fun workshopImageUrlsAreExtractedFromBbCodeAndHtml() {
        val urls = SteamHtmlDecoder.extractWorkshopImageUrls(
            "[img]https://example.com/one.png[/img] " +
                "<img src=\"https://example.com/two.jpg\"> " +
                "[img width=640]https://example.com/one.png[/img]",
        )

        assertThat(urls).containsExactly(
            "https://example.com/one.png",
            "https://example.com/two.jpg",
        ).inOrder()
    }

    @Test
    fun workshopGalleryUrlsAreExtractedFromSteamScreenshotArray() {
        val urls = SteamHtmlDecoder.extractWorkshopGalleryImageUrls(
            "var rgFullScreenshotURLs = [" +
                "{ 'previewid' : '1', 'url': 'https://example.com/full-1.jpg' }," +
                "{ 'previewid' : '2', 'url': 'https://example.com/full-2.jpg' }];",
        )

        assertThat(urls).containsExactly(
            "https://example.com/full-1.jpg",
            "https://example.com/full-2.jpg",
        ).inOrder()
    }

    @Test
    fun workshopDescriptionPartsKeepImagesInPlace() {
        val parts = SteamHtmlDecoder.decodeWorkshopDescriptionParts(
            "开头[img]https://example.com/inline.png[/img]结尾",
            isHtml = false,
        )

        assertThat(parts.map { it.text to it.imageUrl }).containsExactly(
            "开头" to null,
            "" to "https://example.com/inline.png",
            "结尾" to null,
        ).inOrder()
    }
}
