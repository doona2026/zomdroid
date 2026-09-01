package com.zomdroid.workshop.install

import android.content.Context
import android.content.Intent
import com.zomdroid.game.GameInstance
import com.zomdroid.workshop.library.ModLibraryEntry
import com.zomdroid.workshop.library.ModLibraryRepository

/** Java-friendly library install facade that records the shared entry after validation. */
object WorkshopLibraryInstaller {
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
