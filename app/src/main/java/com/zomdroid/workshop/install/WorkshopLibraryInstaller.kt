package com.zomdroid.workshop.install

import android.content.Context
import android.content.Intent
import com.zomdroid.game.GameInstance
import com.zomdroid.workshop.library.ModLibraryEntry
import com.zomdroid.workshop.library.ModLibraryRepository
import java.io.File

/** Java-friendly library install facade that records the shared entry after validation. */
object WorkshopLibraryInstaller {
    /** Returns only the target folders that actually exist in this instance. */
    @JvmStatic
    fun findExistingModNames(entry: ModLibraryEntry, instance: GameInstance): List<String> {
        val archive = File(entry.completedPath).canonicalFile
        val modsDirectory = File(instance.getHomePath(), "Zomboid/mods")
        return WorkshopModArchiveInspector.findExistingModNames(archive, modsDirectory)
    }

    @JvmStatic
    fun buildIntent(
        context: Context,
        entry: ModLibraryEntry,
        instance: GameInstance,
        keepBackup: Boolean = true,
    ): Intent {
        val repository = ModLibraryRepository(context)
        val intent = WorkshopInstallCoordinator(context).buildInstallIntent(
            entry,
            instance.getName(),
            instance.getBuildVersion(),
            keepBackup,
        )
        repository.markInstalled(entry, instance.getName())
        return intent
    }
}
