package com.zomdroid.workshop.install

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkshopModArchiveInspectorTest {
    @Test
    fun findsWrappedAndBuildVersionModRoots() {
        val archive = zipOf(
            "Workshop/NeatUI/42.20/media/ui.txt",
            "Workshop/NeatUI/42.20/mod.info",
        )

        assertEquals(listOf("NeatUI"), WorkshopModArchiveInspector.findModRootNames(archive))
    }

    @Test
    fun usesArchiveNameWhenTheModRootIsTheArchiveRoot() {
        val archive = zipOf("media/ui.txt", "mod.info")

        assertEquals(listOf("root-mod"), WorkshopModArchiveInspector.findModRootNames(archive))
    }

    @Test
    fun findsMultipleIndependentModRoots() {
        val archive = zipOf("First/mod.info", "Second/media/ui.txt")

        assertEquals(
            listOf("First", "Second"),
            WorkshopModArchiveInspector.findModRootNames(archive),
        )
    }

    @Test
    fun reportsOnlyFoldersThatExistInTheTargetDirectory() {
        val archive = zipOf("First/mod.info", "Second/mod.info")
        val modsDirectory = Files.createTempDirectory("zomboid-mods").toFile().apply {
            resolve("Second").mkdirs()
        }

        assertEquals(
            listOf("Second"),
            WorkshopModArchiveInspector.findExistingModNames(archive, modsDirectory),
        )
    }

    private fun zipOf(vararg entries: String): File {
        val archive = Files.createTempDirectory("workshop-inspector").toFile()
            .resolve("root-mod.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            entries.forEach { name ->
                output.putNextEntry(ZipEntry(name))
                output.write("test".toByteArray())
                output.closeEntry()
            }
        }
        archive.deleteOnExit()
        return archive
    }
}
