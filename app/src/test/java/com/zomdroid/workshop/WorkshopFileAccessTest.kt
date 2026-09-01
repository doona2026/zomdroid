package com.zomdroid.workshop

import com.zomdroid.workshop.core.DownloadedFileInfo
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkshopFileAccessTest {
    @Test
    fun `exportCompletedZip includes only verified completed files`() {
        val root = Files.createTempDirectory("workshop-output").toFile()
        val content = java.io.File(root, "media/scripts/mod.info").apply {
            parentFile.mkdirs()
            writeText("name=Example")
        }
        java.io.File(root, "download.log").writeText("must not be exported")
        val zip = java.io.File(root.parentFile, "workshop.zip")

        WorkshopFileAccess.exportCompletedZip(
            outputDir = root,
            files = listOf(
                DownloadedFileInfo(
                    relativePath = "media/scripts/mod.info",
                    sizeBytes = content.length(),
                    modifiedEpochMillis = content.lastModified(),
                ),
            ),
            destination = zip,
        )

        ZipFile(zip).use { archive ->
            assertEquals("name=Example", archive.getInputStream(archive.getEntry("media/scripts/mod.info")).reader().readText())
            assertFalse(archive.entries().asSequence().any { it.name == "download.log" })
        }
    }
}
