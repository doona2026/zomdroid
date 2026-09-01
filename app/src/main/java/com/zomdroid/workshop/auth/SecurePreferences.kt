package com.zomdroid.workshop.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal fun createEncryptedPrefsOrFallback(
    context: Context,
    encryptedPrefsName: String,
    fallbackPrefsName: String,
    storageLabel: String,
): SharedPreferences = runCatching {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        context,
        encryptedPrefsName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}.getOrElse { error ->
    // Android's local JVM test stubs do not implement Log; keep the fallback path testable.
    runCatching {
        Log.w("SteamAuth", "Encrypted preferences unavailable for $storageLabel; using fallback storage", error)
    }
    context.getSharedPreferences(fallbackPrefsName, Context.MODE_PRIVATE)
}
