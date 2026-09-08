package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Test

class InstalledModScannerTest {
    @Test
    fun scansDirectAndNestedModRootsAndCountsOnlyUnrecognizedTopLevelDirectories() {
        val instanceHome = Files.createTempDirectory("instance").toFile()
        val mods = instanceHome.resolve("Zomboid/mods")
        mods.mkdirs()
        writeMod(mods, "Direct", "name=Direct Mod\nid=direct.mod\ndescription=direct")
        writeMod(mods, "Workshop/First", "name=First Mod\nid=first.mod")
        writeMod(mods, "Workshop/Second", "name=Second Mod\nid=second.mod")
        val cache = mods.resolve("NotAMod/cache.bin")
        cache.parentFile!!.mkdirs()
        cache.writeText("cache")

        val result = InstalledModScanner().scan("Demo", instanceHome)

        assertThat(result.mods.map { it.name }).containsExactly(
            "Direct Mod", "First Mod", "Second Mod",
        ).inOrder()
        assertThat(result.mods.map { it.relativePath }).containsExactly(
            "Direct", "Workshop/First", "Workshop/Second",
        ).inOrder()
        assertThat(result.ignoredDirectoryCount).isEqualTo(1)
        assertThat(result.modsDirectoryExists).isTrue()
    }

    @Test
    fun doesNotTreatNestedVersionsOrSubmodsAsAdditionalRoots() {
        val instanceHome = Files.createTempDirectory("instance").toFile()
        val mods = instanceHome.resolve("Zomboid/mods")
        mods.mkdirs()
        writeMod(mods, "Parent", "name=Parent Mod\nid=parent.mod")
        writeMod(mods, "Parent/41", "name=Parent Legacy\nid=parent.legacy")
        writeMod(mods, "Parent/42/Child", "name=Child Mod\nid=child.mod")

        val result = InstalledModScanner().scan("Demo", instanceHome)

        assertThat(result.mods.map { it.name }).containsExactly("Parent Mod")
        assertThat(result.mods.single().relativePath).isEqualTo("Parent")
    }

    @Test
    fun groupsVersionRootsUnderOneLogicalModPackage() {
        val instanceHome = Files.createTempDirectory("instance").toFile()
        val mods = instanceHome.resolve("Zomboid/mods")
        mods.mkdirs()
        writeMod(mods, "MS/42", "name=Modern Status [B42]\nid=modern.status")
        writeMod(mods, "MS/42.13", "name=Modern Status [Legacy B42]\nid=modern.status")
        mods.resolve("MS/common").mkdirs()

        val result = InstalledModScanner().scan("Demo", instanceHome)
        val mod = result.mods.single()

        assertThat(mod.name).isEqualTo("Modern Status [Legacy B42]")
        assertThat(mod.rootPath).isEqualTo(mods.resolve("MS").canonicalPath)
        assertThat(mod.infoRootPath).isEqualTo(mods.resolve("MS/42.13").canonicalPath)
        assertThat(mod.variants.map { it.relativePath }).containsExactly(
            "MS/42", "MS/42.13",
        ).inOrder()
    }

    @Test
    fun groupsVersionAndCommonRootsWithTheSameModId() {
        val instanceHome = Files.createTempDirectory("instance").toFile()
        val mods = instanceHome.resolve("Zomboid/mods")
        mods.mkdirs()
        writeMod(mods, "RaccoonCityB42/42", "name=RaccoonCityB42\nid=RaccoonCityB42")
        writeMod(mods, "RaccoonCityB42/common", "name=RaccoonCityB42\nid=RaccoonCityB42")

        val result = InstalledModScanner().scan("Demo", instanceHome)
        val mod = result.mods.single()

        assertThat(mod.rootPath).isEqualTo(mods.resolve("RaccoonCityB42").canonicalPath)
        assertThat(mod.variants.map { it.relativePath }).containsExactly(
            "RaccoonCityB42/common", "RaccoonCityB42/42",
        ).inOrder()
    }

    @Test
    fun readsMetadataThumbnailSizeAndRootLastModified() {
        val instanceHome = Files.createTempDirectory("instance").toFile()
        val mods = instanceHome.resolve("Zomboid/mods")
        mods.mkdirs()
        val root = mods.resolve("Example")
        root.mkdirs()
        root.resolve("mod.info").writeText("name=Example\nid=example\nposter=poster.png")
        root.resolve("poster.png").writeBytes(byteArrayOf(1, 2, 3, 4))
        root.resolve("data.txt").writeText("data")
        root.setLastModified(123456789L)

        val result = InstalledModScanner().scan("Demo", instanceHome)
        val mod = result.mods.single()

        assertThat(mod.instanceName).isEqualTo("Demo")
        assertThat(mod.modId).isEqualTo("example")
        assertThat(mod.description).isEmpty()
        assertThat(mod.thumbnailPath).isEqualTo(root.resolve("poster.png").canonicalPath)
        assertThat(mod.sizeBytes).isAtLeast(4L)
        assertThat(mod.lastModifiedEpochMillis).isEqualTo(root.lastModified())
        assertThat(mod.metadataComplete).isTrue()
    }

    @Test
    fun missingModsDirectoryReturnsExplicitMissingState() {
        val instanceHome = Files.createTempDirectory("instance").toFile()

        val result = InstalledModScanner().scan("Demo", instanceHome)

        assertThat(result.mods).isEmpty()
        assertThat(result.modsDirectoryExists).isFalse()
        assertThat(result.issues).isEmpty()
    }

    @Test
    fun malformedMetadataDoesNotDiscardOtherMods() {
        val instanceHome = Files.createTempDirectory("instance").toFile()
        val mods = instanceHome.resolve("Zomboid/mods")
        mods.mkdirs()
        writeMod(mods, "Broken", "not a property\nname=Broken")
        writeMod(mods, "Valid", "name=Valid\nid=valid")

        val result = InstalledModScanner().scan("Demo", instanceHome)

        assertThat(result.mods.map { it.name }).containsExactly("Broken", "Valid")
        assertThat(result.mods.first { it.name == "Broken" }.metadataComplete).isFalse()
        assertThat(result.mods.first { it.name == "Valid" }.metadataComplete).isTrue()
    }

    private fun writeMod(mods: java.io.File, relativePath: String, info: String) {
        val root = mods.resolve(relativePath).apply { mkdirs() }
        root.resolve("mod.info").writeText(info)
    }
}
