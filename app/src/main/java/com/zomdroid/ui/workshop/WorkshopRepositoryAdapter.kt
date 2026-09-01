package com.zomdroid.ui.workshop

import android.content.Context
import com.zomdroid.workshop.WorkshopBrowseSortOption
import com.zomdroid.workshop.WorkshopBrowseTimeWindow
import com.zomdroid.workshop.WorkshopRuntime
import com.zomdroid.workshop.auth.SteamAccountSummary
import com.zomdroid.workshop.auth.SteamAccountsSnapshot
import com.zomdroid.workshop.auth.SteamAuthRepository
import com.zomdroid.workshop.auth.SteamSignInStep
import com.zomdroid.workshop.data.SteamGame
import com.zomdroid.workshop.data.WorkshopBrowseItem
import com.zomdroid.workshop.data.WorkshopBrowsePage
import com.zomdroid.workshop.data.WorkshopCommentPage
import com.zomdroid.workshop.data.WorkshopDetailRepository
import com.zomdroid.workshop.data.WorkshopItemDetail
import com.zomdroid.workshop.data.WorkshopBrowseRepository
import com.zomdroid.workshop.data.SteamGameRepository
import com.zomdroid.workshop.download.DownloadCenterManager
import com.zomdroid.workshop.download.DownloadCenterManagerProvider
import com.zomdroid.workshop.library.ModLibraryEntry
import com.zomdroid.workshop.library.ModLibraryRepository
import com.zomdroid.workshop.library.ModUpdateChecker
import com.zomdroid.workshop.library.ModUpdateResult
import kotlinx.coroutines.flow.StateFlow

/** Narrow UI-facing boundary over the existing Workshop/domain services. */
interface WorkshopRepositoryAdapter {
    suspend fun featuredGames(): List<SteamGame>
    suspend fun searchGames(query: String): List<SteamGame>
    suspend fun browse(game: SteamGame, query: String, sort: WorkshopBrowseSortOption, page: Int): WorkshopBrowsePage
    suspend fun detail(item: WorkshopBrowseItem): WorkshopItemDetail
    suspend fun comments(detail: WorkshopItemDetail, page: Int): WorkshopCommentPage
    fun enqueue(detail: WorkshopItemDetail, accountId: String?): String
    fun tasks(): StateFlow<List<com.zomdroid.workshop.download.DownloadCenterTask>>
    fun downloadManager(): DownloadCenterManager
    fun accounts(): SteamAccountsSnapshot
    suspend fun signIn(username: String, password: String, replaceAccountId: String?): SteamSignInStep
    suspend fun submitGuardCode(code: String): SteamSignInStep
    suspend fun waitForConfirmation(): SteamSignInStep
    fun setActiveAccount(accountId: String)
    fun removeAccount(accountId: String)
    fun library(): ModLibrarySnapshotUi
    suspend fun checkUpdate(entry: ModLibraryEntry, force: Boolean): ModUpdateResult?
    fun removeLibraryEntry(entry: ModLibraryEntry)
    fun cleanupLibrary(): Int
}

data class ModLibrarySnapshotUi(val entries: List<ModLibraryEntry>)

class DefaultWorkshopRepositoryAdapter(context: Context) : WorkshopRepositoryAdapter {
    private val appContext = context.applicationContext
    private val client = com.zomdroid.workshop.data.WorkshopCatalogRuntime.client(appContext)
    private val games = SteamGameRepository(client)
    private val browse = WorkshopBrowseRepository(client)
    private val details = WorkshopDetailRepository(client)
    private val auth = SteamAuthRepository(appContext)
    private val manager = DownloadCenterManagerProvider.get(appContext)
    private val library = ModLibraryRepository(appContext)

    init { WorkshopRuntime.initialize(appContext) }

    override suspend fun featuredGames() = games.loadFeaturedWorkshopGames()
    override suspend fun searchGames(query: String) = games.searchWorkshopGames(query)
    override suspend fun browse(game: SteamGame, query: String, sort: WorkshopBrowseSortOption, page: Int) =
        browse.browseGameWorkshop(game.appId, query, sort, WorkshopBrowseTimeWindow.OneWeek, page)
    override suspend fun detail(item: WorkshopBrowseItem) = details.loadWorkshopItemDetail(item, includeChangeNotes = true)
    override suspend fun comments(detail: WorkshopItemDetail, page: Int) =
        if (detail.commentThreadContext == null) {
            detail.toUnavailableCommentPage()
        } else {
            details.loadWorkshopCommentPage(detail, page)
        }

    override fun enqueue(detail: WorkshopItemDetail, accountId: String?): String = manager.enqueueForInstanceWithMetadata(
        appId = detail.appId.toLong(), publishedFileId = detail.publishedFileId.toLong(),
        title = detail.title, description = detail.description, previewUrl = detail.previewImageUrl,
        updatedAtEpochSeconds = detail.timeUpdatedEpochSeconds, accountId = accountId,
        targetInstanceName = null, targetBuildVersion = null,
    ).id

    override fun tasks() = manager.tasks
    override fun downloadManager() = manager
    override fun accounts() = auth.loadSnapshot()
    override suspend fun signIn(username: String, password: String, replaceAccountId: String?) = auth.beginSignIn(username, password, replaceAccountId)
    override suspend fun submitGuardCode(code: String) = auth.submitPendingGuardCode(code)
    override suspend fun waitForConfirmation() = auth.waitForPendingConfirmation()
    override fun setActiveAccount(accountId: String) = auth.setActiveAccount(accountId)
    override fun removeAccount(accountId: String) = auth.removeAccount(accountId)
    override fun library() = ModLibrarySnapshotUi(library.snapshot().entries)

    override suspend fun checkUpdate(entry: ModLibraryEntry, force: Boolean): ModUpdateResult? {
        val checker = ModUpdateChecker(library)
        return checker.check(entry, force) {
            details.loadWorkshopItemDetail(
                WorkshopBrowseItem(entry.appId.toUInt(), entry.publishedFileId.toULong(), entry.title, "", entry.previewUrl, entry.description),
                includeChangeNotes = false,
            ).timeUpdatedEpochSeconds
        }
    }

    override fun removeLibraryEntry(entry: ModLibraryEntry) = library.remove(entry, deleteCompletedFile = true)

    override fun cleanupLibrary(): Int {
        val newest = library.snapshot().entries.groupBy { it.appId to it.publishedFileId }
            .mapNotNull { (_, versions) -> versions.maxWithOrNull(compareBy<ModLibraryEntry> { it.updatedAtEpochSeconds ?: Long.MIN_VALUE }.thenBy { it.versionKey }) }
        return newest.sumOf { library.pruneOldVersions(it.appId, it.publishedFileId, it.versionKey) }
    }
}

/** A detail page can be valid even when Steam did not embed a comment thread. */
internal fun WorkshopItemDetail.toUnavailableCommentPage(): WorkshopCommentPage = WorkshopCommentPage(
    commentsUrl = commentsUrl,
    commentCount = commentCount,
    page = 1,
    totalPages = null,
    hasPreviousPage = false,
    hasNextPage = false,
    comments = comments,
)
