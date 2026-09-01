package com.zomdroid.workshop.download

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkshopArchiveNamingTest {
    @Test
    fun usesReadableSanitizedTitleAndWorkshopIdentity() {
        assertThat(WorkshopArchiveNaming.forWorkshop(123L, "A/B: Mod", 456L))
            .isEqualTo("A_B_ Mod [123]_456.zip")
    }

    @Test
    fun extractsTitleFromPublishedFileMetadata() {
        val metadata = """{"response":{"publishedfiledetails":[{"title":"Readable Mod"}]}}"""
        assertThat(WorkshopArchiveNaming.titleFromMetadata(metadata, 123L))
            .isEqualTo("Readable Mod")
    }
}
