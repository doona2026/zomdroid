/* Adapted from WorkshopAndroidDownloader (Apache-2.0); Zomdroid runtime ownership. */
package com.zomdroid.workshop

import android.content.Context
import com.zomdroid.workshop.auth.SteamClientIdentity
import com.zomdroid.workshop.auth.SteamAuthRepository
import com.zomdroid.workshop.core.WorkshopDownloadEngine
import com.zomdroid.workshop.steam.protocol.applyDefaultHttpTimeouts
import com.zomdroid.workshop.steam.protocol.applySteamHttpCompatibility
import com.zomdroid.workshop.network.addWorkshopDirectAccess
import okhttp3.OkHttpClient

/** Process-scoped owner for the shared Workshop HTTP client and official Steam engine. */
object WorkshopRuntime {
    @Volatile
    private var engine: WorkshopDownloadEngine? = null

    @JvmStatic
    @Synchronized
    fun initialize(context: Context) {
        if (engine == null) {
            val client = OkHttpClient.Builder()
                .applyDefaultHttpTimeouts()
                .applySteamHttpCompatibility()
                .addWorkshopDirectAccess(context)
                .build()
            engine = WorkshopDownloadEngine.createDefault(client = client)
        }
    }

    @JvmStatic
    fun requireEngine(): WorkshopDownloadEngine =
        engine ?: error("WorkshopRuntime.initialize(context) must be called first")

    /** Returns an engine whose web and CM requests are bound to the requested account. */
    @JvmStatic
    fun requireEngine(context: Context, accountId: String?): WorkshopDownloadEngine {
        if (accountId == null) {
            initialize(context)
            return requireEngine()
        }
        val appContext = context.applicationContext
        val authRepository = SteamAuthRepository(appContext)
        // Keep the CM identity used for an authenticated download identical to the
        // identity used while creating/refreshing the account session. Using the
        // protocol's demo identity here makes Steam see the same account from a
        // different client installation and can result in EResult=84/AccessDenied.
        val steamClientIdentity = SteamClientIdentity(appContext)
        val account = authRepository.accountSessionFor(accountId)
            ?: error("Steam account is unavailable or requires reauthentication")
        val client = OkHttpClient.Builder()
            .applyDefaultHttpTimeouts()
            .applySteamHttpCompatibility()
            .addInterceptor(
                com.zomdroid.workshop.auth.SteamCookieInterceptor(
                    authRepository = authRepository,
                    accountIdProvider = { accountId },
                    fallbackToActiveAccount = false,
                ),
            )
            .addWorkshopDirectAccess(appContext)
            .build()
        return WorkshopDownloadEngine.createDefault(
            client = client,
            sessionFactory = { steamClientIdentity.createSession(client) },
            sessionConnector = { session, servers -> session.connectWithRefreshToken(servers, account) },
        )
    }

    @JvmStatic
    fun createJavaFacade(context: Context): WorkshopJavaFacade {
        initialize(context)
        return WorkshopJavaFacade(requireEngine(), WorkshopPaths.privateStagingRoot(context))
    }
}
