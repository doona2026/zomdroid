/* Adapted from WorkshopAndroidDownloader (Apache-2.0); Zomdroid application contract. */
package com.zomdroid.workshop

/** Stable values shared by the Java UI/services and the Kotlin Workshop implementation. */
object WorkshopAppContract {
    const val PROJECT_ZOMBOID_STEAM_APP_ID: Long = 108600L
    const val EXTRA_WORKSHOP_ID: String = "com.zomdroid.workshop.EXTRA_WORKSHOP_ID"
    const val EXTRA_INSTANCE_NAME: String = "com.zomdroid.workshop.EXTRA_INSTANCE_NAME"
    const val THIRD_PARTY_FALLBACK_SOURCE: String = "ggntw.com"

    @JvmStatic
    fun isProjectZomboid(appId: Long): Boolean = appId == PROJECT_ZOMBOID_STEAM_APP_ID
}
