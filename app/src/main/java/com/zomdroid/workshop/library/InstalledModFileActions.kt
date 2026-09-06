package com.zomdroid.workshop.library

import java.io.File
import java.nio.file.Files

data class InstalledModFileActionResult(
    val success: Boolean,
    val message: String? = null,
)

object InstalledModFileActions {
    @JvmStatic
    fun delete(
        modsDirectory: File,
        mod: InstalledMod,
        scannedMods: Collection<InstalledMod>,
    ): InstalledModFileActionResult {
        return try {
            val modsRoot = modsDirectory.canonicalFile
            val target = File(mod.rootPath).canonicalFile
            val scannedPaths = scannedMods.mapNotNull {
                runCatching { File(it.rootPath).canonicalFile.path }.getOrNull()
            }
            if (!modsRoot.isDirectory || target == modsRoot || !isWithin(modsRoot, target) || target.path !in scannedPaths) {
                return failure("The Mod is outside the selected instance")
            }
            if (Files.isSymbolicLink(File(mod.rootPath).toPath()) || !target.isDirectory) {
                return failure("The Mod folder is no longer available")
            }
            if (!deleteTree(target)) failure("The Mod folder could not be deleted")
            else InstalledModFileActionResult(success = true)
        } catch (error: Exception) {
            failure(error.message ?: "The Mod folder could not be deleted")
        }
    }

    private fun deleteTree(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath())) return file.delete()
        if (file.isDirectory) {
            val children = file.listFiles() ?: return false
            if (!children.all(::deleteTree)) return false
        }
        return file.delete()
    }

    private fun failure(message: String) = InstalledModFileActionResult(false, message)

    private fun isWithin(boundary: File, candidate: File): Boolean =
        candidate.path.startsWith(boundary.path.trimEnd(File.separatorChar) + File.separator)
}
