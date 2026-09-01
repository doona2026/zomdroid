/* Adapted from WorkshopAndroidDownloader (Apache-2.0); Java facade contract test. */
package com.zomdroid.workshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopJavaFacadeTest {
    @Test
    fun `contract exposes project zomboid app and fallback source`() {
        assertEquals(108600L, WorkshopAppContract.PROJECT_ZOMBOID_STEAM_APP_ID)
        assertTrue(WorkshopAppContract.isProjectZomboid(108600L))
        assertEquals("ggntw.com", WorkshopAppContract.THIRD_PARTY_FALLBACK_SOURCE)
    }
}
