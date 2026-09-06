package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class InstalledModWorkshopMatcherTest {
    @Test
    fun matchesAnInstalledArchiveByItsModRootName() {
        val archive = zipOf("First/mod.info" to "name=First\nid=first")
        val entry = entry(123L, archive, installed = listOf("Demo"))
        val mod = mod("First", "different.id")

        val match = InstalledModWorkshopMatcher.match(mod, listOf(entry))

        assertThat(match?.publishedFileId).isEqualTo(123L)
    }

    @Test
    fun matchesAnExternalCopyByAUniqueArchivedModId() {
        val archive = zipOf("First/mod.info" to "name=First\nid=first")
        val entry = entry(123L, archive, installed = listOf("Other"))
        val mod = mod("CopiedFolder", "first")

        val match = InstalledModWorkshopMatcher.match(mod, listOf(entry))

        assertThat(match?.publishedFileId).isEqualTo(123L)
    }

    @Test
    fun ignoresArchivesNotRecordedForThisInstanceAndAmbiguousMatches() {
        val first = entry(123L, zipOf("First/mod.info" to "id=first"), installed = listOf("Other"))
        val second = entry(456L, zipOf("Copied/mod.info" to "id=first"), installed = listOf("Demo"))
        val third = entry(789L, zipOf("Another/mod.info" to "id=first"), installed = listOf("Demo"))
        val mod = mod("CopiedFolder", "first")

        val match = InstalledModWorkshopMatcher.match(mod, listOf(first, second, third))

        assertThat(match).isNull()
    }

    @Test
    fun brokenArchivesDoNotBreakUnmatchedResult() {
        val broken = Files.createTempFile("broken", ".zip").toFile().apply { writeText("not a zip") }
        val entry = entry(123L, broken, installed = listOf("Demo"))

        assertThat(InstalledModWorkshopMatcher.match(mod("Any", "any"), listOf(entry))).isNull()
    }

    private fun mod(folder: String, id: String) = InstalledMod(
        instanceName = "Demo",
        rootPath = "/mods/$folder",
        relativePath = folder,
        name = folder,
        modId = id,
    )

    private fun entry(id: Long, archive: File, installed: List<String>) = ModLibraryEntry(
        appId = 108600L,
        publishedFileId = id,
        title = "Workshop $id",
        versionKey = "v$id",
        completedPath = archive.absolutePath,
        installedInstances = installed,
    )

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = Files.createTempFile("workshop", ".zip").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
