package com.zomdroid.workshop.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zomdroid.workshop.steam.protocol.SteamAccountSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Java-friendly asynchronous facade for the account screen and download queue. */
object SteamAuthRuntime {
    interface Callback {
        fun onResult(result: Result)
    }

    data class Result(
        val kind: String,
        val message: String? = null,
        val snapshot: SteamAccountsSnapshot = SteamAccountsSnapshot(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val repositories = ConcurrentHashMap<String, SteamAuthRepository>()

    @JvmStatic
    fun currentAccountId(context: Context): String? = repository(context).activeAccountId()

    /** Returns the selected account's in-memory session for Java Steam clients. */
    @JvmStatic
    fun currentAccountSession(context: Context): SteamAccountSession? {
        val repo = repository(context)
        return repo.accountSessionFor(repo.activeAccountId())
    }

    @JvmStatic
    fun loadSnapshot(context: Context, callback: Callback) {
        dispatch(callback) { Result(kind = "snapshot", snapshot = repository(context).loadSnapshot()) }
    }

    @JvmStatic
    fun signIn(context: Context, username: String, password: String, replaceAccountId: String?, callback: Callback) {
        scope.launch {
            val result = runCatching {
                when (val step = repository(context).beginSignIn(username, password, replaceAccountId)) {
                    is SteamSignInStep.RequiresGuardCode -> Result("guard_code", step.challenge.message)
                    is SteamSignInStep.AwaitingConfirmation -> Result("device_confirmation", step.challenge.message)
                    is SteamSignInStep.Success -> Result("success", snapshot = step.snapshot)
                }
            }.getOrElse { error(it) }
            post(callback, result)
        }
    }

    @JvmStatic
    fun submitGuardCode(context: Context, code: String, callback: Callback) {
        scope.launch {
            val result = runCatching {
                when (val step = repository(context).submitPendingGuardCode(code)) {
                    is SteamSignInStep.Success -> Result("success", snapshot = step.snapshot)
                    is SteamSignInStep.RequiresGuardCode -> Result("guard_code", step.challenge.message)
                    is SteamSignInStep.AwaitingConfirmation -> Result("device_confirmation", step.challenge.message)
                }
            }.getOrElse { error(it) }
            post(callback, result)
        }
    }

    @JvmStatic
    fun waitForConfirmation(context: Context, callback: Callback) {
        scope.launch {
            val result = runCatching {
                when (val step = repository(context).waitForPendingConfirmation()) {
                    is SteamSignInStep.Success -> Result("success", snapshot = step.snapshot)
                    is SteamSignInStep.RequiresGuardCode -> Result("guard_code", step.challenge.message)
                    is SteamSignInStep.AwaitingConfirmation -> Result("device_confirmation", step.challenge.message)
                }
            }.getOrElse { error(it) }
            post(callback, result)
        }
    }

    @JvmStatic
    fun setActive(context: Context, accountId: String, callback: Callback) {
        dispatch(callback) {
            val repo = repository(context)
            repo.setActiveAccount(accountId)
            Result("snapshot", snapshot = repo.loadSnapshot())
        }
    }

    @JvmStatic
    fun remove(context: Context, accountId: String, callback: Callback) {
        scope.launch {
            val result = runCatching {
                val repo = repository(context)
                repo.removeAccount(accountId)
                Result("snapshot", snapshot = repo.loadSnapshot())
            }.getOrElse { error(it) }
            post(callback, result)
        }
    }

    @JvmStatic
    fun cancel(context: Context) = repository(context).cancelPendingSignIn()

    private fun repository(context: Context): SteamAuthRepository {
        val appContext = context.applicationContext
        return repositories.getOrPut(appContext.packageName) { SteamAuthRepository(appContext) }
    }

    private fun dispatch(callback: Callback, block: () -> Result) {
        scope.launch { post(callback, runCatching(block).getOrElse { error(it) }) }
    }

    private fun post(callback: Callback, result: Result) = mainHandler.post { callback.onResult(result) }

    private fun error(error: Throwable): Result =
        Result(kind = "error", message = error.message?.substringBefore('\n')?.take(240) ?: "Steam sign-in failed")
}
