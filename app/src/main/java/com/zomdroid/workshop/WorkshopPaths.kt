/* Adapted from WorkshopAndroidDownloader (Apache-2.0); Zomdroid path policy. */
package com.zomdroid.workshop

import android.content.Context
import android.os.Environment
import java.io.File

object WorkshopPaths {
    @JvmStatic
    fun privateStagingRoot(context: Context): File =
        File(context.applicationContext.filesDir, "workshop/staging")

    @JvmStatic
    fun privateItemStagingRoot(context: Context, publishedFileId: Long): File {
        require(publishedFileId > 0) { "publishedFileId must be positive" }
        return File(privateStagingRoot(context), publishedFileId.toString())
    }

    @JvmStatic
    fun completedDownloadsRoot(): File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "zomdroid/workshop",
        )
}
