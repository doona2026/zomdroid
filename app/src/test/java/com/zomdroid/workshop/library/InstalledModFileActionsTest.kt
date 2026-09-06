package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.LinkOption
import org.junit.Test

class InstalledModFileActionsTest {
    @Test
    fun deletesOnlyTheScannedModRootAndPreservesItsSibling() {
        val mods = Files.createTempDirectory("mods").toFile()
        val target = mods.resolve("Target").apply { mkdirs() }
        target.resolve("mod.info").writeText("id=target")
        target.resolve("data.bin").writeText("data")
        val sibling = mods.resolve("Sibling").apply { mkdirs() }
        sibling.resolve("mod.info").writeText("id=sibling")
        val mod = mod(target)

        val result = InstalledModFileActions.delete(mods, mod, listOf(mod))

        assertThat(result.success).isTrue()
        assertThat(target.exists()).isFalse()
        assertThat(sibling.exists()).isTrue()
    }

    @Test
    fun deletesTheWholeLogicalPackageIncludingNestedVersions() {
        val mods = Files.createTempDirectory("mods").toFile()
        val packageRoot = mods.resolve("MS").apply { mkdirs() }
        val version42 = packageRoot.resolve("42").apply { mkdirs() }
        val version4213 = packageRoot.resolve("42.13").apply { mkdirs() }
        packageRoot.resolve("common").mkdirs()
        version42.resolve("mod.info").writeText("id=modern.status")
        version4213.resolve("mod.info").writeText("id=modern.status")
        val logicalMod = mod(packageRoot).copy(
            variants = listOf(
                InstalledModVariant(version42.absolutePath, "MS/42", "Modern Status [B42]", "modern.status"),
                InstalledModVariant(version4213.absolutePath, "MS/42.13", "Modern Status [B42.13]", "modern.status"),
            ),
        )

        val result = InstalledModFileActions.delete(mods, logicalMod, listOf(logicalMod))

        assertThat(result.success).isTrue()
        assertThat(packageRoot.exists()).isFalse()
    }

    @Test
    fun rejectsModsRootAndPathsOutsideTheCurrentInstance() {
        val mods = Files.createTempDirectory("mods").toFile()
        val outside = Files.createTempDirectory("outside").toFile().apply { mkdirs() }
        val rootMod = mod(mods)
        val outsideMod = mod(outside)

        assertThat(InstalledModFileActions.delete(mods, rootMod, listOf(rootMod)).success).isFalse()
        assertThat(InstalledModFileActions.delete(mods, outsideMod, listOf(outsideMod)).success).isFalse()
        assertThat(mods.exists()).isTrue()
        assertThat(outside.exists()).isTrue()
    }

    @Test
    fun rejectsAPathThatWasNotTheScannedRoot() {
        val mods = Files.createTempDirectory("mods").toFile()
        val scanned = mods.resolve("Scanned").apply { mkdirs() }
        val other = mods.resolve("Other").apply { mkdirs() }
        val invalidMod = mod(scanned).copy(rootPath = other.absolutePath)

        val result = InstalledModFileActions.delete(mods, invalidMod, listOf(mod(scanned)))

        assertThat(result.success).isFalse()
        assertThat(scanned.exists()).isTrue()
        assertThat(other.exists()).isTrue()
        assertThat(invalidMod.rootPath).isNotEqualTo(scanned.absolutePath)
    }

    @Test
    fun rejectsASymbolicLinkModRootWhenThePlatformAllowsCreatingOne() {
        val mods = Files.createTempDirectory("mods").toFile()
        val target = Files.createTempDirectory("target").toFile().apply {
            resolve("mod.info").writeText("id=target")
        }
        val link = mods.resolve("Link")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: SecurityException) {
            return
        } catch (_: java.io.IOException) {
            return
        }

        val linkedMod = mod(link)
        val result = InstalledModFileActions.delete(mods, linkedMod, listOf(linkedMod))

        assertThat(result.success).isFalse()
        assertThat(Files.exists(link.toPath(), LinkOption.NOFOLLOW_LINKS)).isTrue()
        assertThat(target.exists()).isTrue()
    }

    private fun mod(root: java.io.File) = InstalledMod(
        instanceName = "Demo",
        rootPath = root.absolutePath,
        relativePath = root.name,
        name = root.name,
        modId = root.name,
    )
}
