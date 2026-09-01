/* Adapted from WorkshopAndroidDownloader (Apache-2.0); Zomdroid file access policy. */
package com.zomdroid.workshop

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zomdroid.workshop.core.DownloadedFileInfo
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.HashSet
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Converts verified private staging output into an installer-compatible local/content URI. */
object WorkshopFileAccess {
    @JvmStatic
    fun exportCompletedZip(
        outputDir: File,
        files: List<DownloadedFileInfo>,
        destination: File,
    ) {
        val root = outputDir.canonicalFile
        require(root.isDirectory) { "Workshop output directory does not exist" }
        require(files.isNotEmpty()) { "Workshop output contains no completed files" }
        destination.parentFile?.let { parent ->
            require(parent.exists() || parent.mkdirs()) { "Cannot create ZIP parent directory" }
        }

        val names = HashSet<String>()
        val temporary = File(destination.parentFile ?: root, "${destination.name}.part")
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(temporary), 64 * 1024)).use { zip ->
                for (file in files) {
                    val relativePath = normalizeRelativePath(file.relativePath)
                    require(names.add(relativePath)) { "Duplicate Workshop file: $relativePath" }
                    val source = File(root, relativePath).canonicalFile
                    require(isInside(root, source)) { "Workshop file escapes output directory" }
                    require(source.isFile) { "Completed Workshop file is missing: $relativePath" }
                    require(source.length() == file.sizeBytes) { "Completed Workshop file changed: $relativePath" }

                    zip.putNextEntry(ZipEntry(relativePath))
                    FileInputStream(source).use { input -> input.copyTo(zip, 64 * 1024) }
                    zip.closeEntry()
                }
            }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("Cannot replace completed Workshop ZIP")
            }
            require(temporary.renameTo(destination)) { "Cannot publish completed Workshop ZIP" }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    @JvmStatic
    fun contentUriForCompletedFile(context: Context, file: File): Uri {
        val root = WorkshopPaths.completedDownloadsRoot().canonicalFile
        val target = file.canonicalFile
        require(isInside(root, target)) { "File is outside the Workshop download root" }
        require(target.isFile) { "Workshop file does not exist" }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
    }

    private fun normalizeRelativePath(value: String): String {
        val normalized = value.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/')) { "Invalid Workshop file path" }
        require(normalized != "." && normalized != ".." && !normalized.split('/').contains("..")) {
            "Invalid Workshop file path"
        }
        return normalized
    }

    private fun isInside(root: File, target: File): Boolean {
        val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
        return target.path == root.path || target.path.startsWith(rootPath)
    }
}
