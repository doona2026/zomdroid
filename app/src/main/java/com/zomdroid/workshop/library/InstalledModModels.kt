package com.zomdroid.workshop.library

data class InstalledModVariant(
    val rootPath: String,
    val relativePath: String,
    val name: String,
    val modId: String? = null,
)

data class InstalledMod(
    val instanceName: String,
    val rootPath: String,
    val relativePath: String,
    val name: String,
    val modId: String? = null,
    val description: String = "",
    val thumbnailPath: String? = null,
    val sizeBytes: Long = 0L,
    val lastModifiedEpochMillis: Long = 0L,
    val metadataComplete: Boolean = !modId.isNullOrBlank(),
    val duplicateModId: Boolean = false,
    val matchedWorkshopId: Long? = null,
    val matchedWorkshopTitle: String? = null,
    val infoFields: Map<String, String> = emptyMap(),
    /** The physical Mod root whose mod.info supplied the displayed metadata. */
    val infoRootPath: String = rootPath,
    /** Nested version roots grouped under this logical package, if any. */
    val variants: List<InstalledModVariant> = emptyList(),
)

enum class InstalledModSortOrder {
    NAME_ASC,
    LAST_MODIFIED_DESC,
    LAST_MODIFIED_ASC,
}

data class InstalledModScanResult(
    val mods: List<InstalledMod> = emptyList(),
    val ignoredDirectoryCount: Int = 0,
    val issues: List<String> = emptyList(),
    val modsDirectoryExists: Boolean = true,
)
