package com.zomdroid.workshop.library

import java.security.MessageDigest

data class ModVersionCandidate(
    val updatedAtEpochSeconds: Long?,
    val metadataJson: String = "",
    val files: List<ModLibraryFile> = emptyList(),
)

fun modVersionKey(candidate: ModVersionCandidate): String {
    val material = buildString {
        append(candidate.updatedAtEpochSeconds ?: "missing")
        append('|')
        append(candidate.metadataJson)
        candidate.files.sortedBy { it.relativePath }.forEach {
            append('|').append(it.relativePath).append(':').append(it.sizeBytes).append(':').append(it.modifiedEpochMillis)
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(material.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun compareModVersions(existing: ModLibraryEntry, candidate: ModVersionCandidate): Int {
    val existingTime = existing.updatedAtEpochSeconds
    val candidateTime = candidate.updatedAtEpochSeconds
    if (existingTime != null && candidateTime != null && existingTime != candidateTime) {
        return candidateTime.compareTo(existingTime)
    }
    val candidateKey = modVersionKey(candidate)
    return candidateKey.compareTo(existing.versionKey)
}

fun isNewerModVersion(existing: ModLibraryEntry, candidate: ModVersionCandidate): Boolean =
    compareModVersions(existing, candidate) > 0
