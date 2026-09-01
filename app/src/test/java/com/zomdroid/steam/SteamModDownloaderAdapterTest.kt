package com.zomdroid.steam

import org.junit.Assert.assertEquals
import org.junit.Test

class SteamModDownloaderAdapterTest {
    @Test
    fun `parseWorkshopIds accepts whitespace and comma separated positive ids`() {
        assertEquals(
            listOf(123L, 456L, 789L),
            SteamModDownloader.parseWorkshopIds("123, 456\nnot-an-id 0 -1 789"),
        )
    }

    @Test
    fun `parseWorkshopIds returns empty list for blank or invalid input`() {
        assertEquals(emptyList<Long>(), SteamModDownloader.parseWorkshopIds("  , bad 0 -5 "))
    }
}
