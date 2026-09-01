package com.zomdroid.ui.download

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.steam.SteamDownloadState
import com.zomdroid.steam.SteamGameDownloader
import com.zomdroid.steam.SteamModDownloader
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class SteamDownloadUiState(val log: String = "", val percent: Int = -1, val indeterminate: Boolean = true, val downloading: Boolean = false, val storageAccessRequired: Boolean = false, val guardRequest: GuardRequest? = null, val finishedMessage: String? = null)
data class GuardRequest(val previousWrong: Boolean, val email: String?)

class SteamDownloadViewModel(private val context: Context) : ViewModel(), SteamDownloadState.View {
    private val appContext = context.applicationContext
    private val steamState = SteamDownloadState.get()
    private val _uiState = MutableStateFlow(SteamDownloadUiState(log = steamState.log.toString(), percent = steamState.percent, indeterminate = steamState.isIndeterminate, downloading = steamState.isDownloading))
    val uiState: StateFlow<SteamDownloadUiState> = _uiState.asStateFlow()
    private val _guardRequests = MutableSharedFlow<GuardRequest>(extraBufferCapacity = 1)
    val guardRequests: SharedFlow<GuardRequest> = _guardRequests.asSharedFlow()
    private var pendingGuard: CompletableFuture<String>? = null

    init { steamState.setView(this) }
    fun startGame(username: String, password: String, manifest: String, build42: Boolean): Boolean {
        if (steamState.isDownloading) return false
        if (!ensureStorage()) return false
        val parsed = parseManifest(manifest)
        val branch = parsed.branch ?: if (build42) "public" else "legacy41"
        val label = parsed.label ?: if (build42) "42" else "41"
        val downloader = SteamGameDownloader(username.trim(), password, parsed.id, branch, label, steamState)
        start(downloader, "zd-download"); return true
    }
    fun startMods(rawIds: String): Boolean {
        if (steamState.isDownloading) return false
        if (!ensureStorage()) return false
        val ids = SteamModDownloader.parseWorkshopIds(rawIds)
        if (ids.isEmpty()) return false
        start(SteamModDownloader(appContext, ids, steamState), "zd-anon-mod"); return true
    }
    fun cancel() = steamState.cancel()
    fun clearFinishedMessage() { _uiState.value = _uiState.value.copy(finishedMessage = null) }
    fun submitGuardCode(code: String) { pendingGuard?.complete(code.trim()); pendingGuard = null; _uiState.value = _uiState.value.copy(guardRequest = null) }
    private fun ensureStorage(): Boolean { if (Environment.isExternalStorageManager()) return true; _uiState.value = _uiState.value.copy(storageAccessRequired = true); return false }
    fun storageRequestHandled() { _uiState.value = _uiState.value.copy(storageAccessRequired = false) }
    private fun start(runnable: Runnable, threadName: String) { steamState.begin(appContext); val thread = Thread(runnable, threadName); steamState.setActive(runnable as com.zomdroid.steam.Cancellable, thread); _uiState.value = _uiState.value.copy(downloading = true, log = steamState.log.toString(), indeterminate = true, percent = -1); thread.start() }
    override fun onLog(fullLog: CharSequence) { _uiState.value = _uiState.value.copy(log = fullLog.toString()) }
    override fun onPercent(percent: Int, indeterminate: Boolean) { _uiState.value = _uiState.value.copy(percent = percent, indeterminate = indeterminate, downloading = true) }
    override fun onFinished(message: String) { _uiState.value = _uiState.value.copy(log = steamState.log.toString(), downloading = false, percent = -1, indeterminate = true, finishedMessage = message) }
    override fun requestSteamGuardCode(previousWrong: Boolean, email: String?): CompletableFuture<String> { val future = CompletableFuture<String>(); pendingGuard = future; val request = GuardRequest(previousWrong, email); _uiState.value = _uiState.value.copy(guardRequest = request); _guardRequests.tryEmit(request); return future }
    override fun onCleared() { steamState.clearView(this); super.onCleared() }

    private data class Manifest(val id: Long, val branch: String?, val label: String?)
    private fun parseManifest(raw: String): Manifest { val text = raw.trim(); val id = if (text.matches(Regex("\\d+"))) text.toLongOrNull() ?: 0 else MANIFEST.matcher(text).run { if (find()) group(1)?.toLongOrNull() ?: 0 else 0 }; val branch = BETA.matcher(text).run { if (find()) group(1) else null }; return Manifest(id, branch, branch ?: if (id > 0) "manifest" else null) }
    companion object {
        private val MANIFEST = Pattern.compile("-manifest\\s+(\\d+)")
        private val BETA = Pattern.compile("-beta\\s+(\\S+)")
        fun factory(context: Context) = object : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = SteamDownloadViewModel(context) as T }
    }
}
