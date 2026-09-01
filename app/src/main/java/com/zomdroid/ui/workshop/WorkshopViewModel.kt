package com.zomdroid.ui.workshop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.workshop.WorkshopBrowseSortOption
import com.zomdroid.workshop.WorkshopAppContract
import com.zomdroid.workshop.auth.SteamAccountSummary
import com.zomdroid.workshop.auth.SteamAccountsSnapshot
import com.zomdroid.workshop.auth.SteamSignInStep
import com.zomdroid.workshop.data.SteamGame
import com.zomdroid.workshop.data.WorkshopBrowseItem
import com.zomdroid.workshop.data.WorkshopBrowsePage
import com.zomdroid.workshop.data.WorkshopCommentPage
import com.zomdroid.workshop.data.WorkshopItemDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkshopUiState(
    val games: List<SteamGame> = emptyList(),
    val selectedGame: SteamGame? = null,
    val gameQuery: String = "",
    val searchQuery: String = "",
    val sort: WorkshopBrowseSortOption = WorkshopBrowseSortOption.MostPopular,
    val page: Int = 1,
    val browseItems: List<WorkshopBrowseItem> = emptyList(),
    val hasNextPage: Boolean = false,
    val selectedItem: WorkshopBrowseItem? = null,
    val detail: WorkshopItemDetail? = null,
    val comments: WorkshopCommentPage? = null,
    val accounts: SteamAccountsSnapshot = SteamAccountsSnapshot(),
    val authMessage: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class WorkshopViewModel(private val repository: WorkshopRepositoryAdapter) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkshopUiState())
    val uiState: StateFlow<WorkshopUiState> = _uiState.asStateFlow()

    init {
        refreshGames()
        refreshAccounts()
    }

    fun refreshGames() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        launchLoad {
        val games = if (_uiState.value.gameQuery.isBlank()) repository.featuredGames() else repository.searchGames(_uiState.value.gameQuery)
        val zomboid = games.firstOrNull { it.appId == WorkshopAppContract.PROJECT_ZOMBOID_STEAM_APP_ID.toUInt() }
        _uiState.value = _uiState.value.copy(games = games, selectedGame = zomboid ?: games.firstOrNull(), isLoading = false)
        (zomboid ?: games.firstOrNull())?.let { loadBrowse(it, 1) }
        }
    }

    fun setGameQuery(value: String) { _uiState.value = _uiState.value.copy(gameQuery = value) }
    fun setSearchQuery(value: String) { _uiState.value = _uiState.value.copy(searchQuery = value) }
    fun selectGame(game: SteamGame) { _uiState.value = _uiState.value.copy(selectedGame = game); loadBrowse(game, 1) }
    fun setSort(sort: WorkshopBrowseSortOption) { _uiState.value = _uiState.value.copy(sort = sort); _uiState.value.selectedGame?.let { loadBrowse(it, 1) } }
    fun loadBrowse(game: SteamGame? = null, page: Int? = null) = launchLoad {
        val actualGame = game ?: _uiState.value.selectedGame ?: return@launchLoad
        val actualPage = page ?: _uiState.value.page
        _uiState.value = _uiState.value.copy(selectedGame = actualGame, page = actualPage, isLoading = true, error = null)
        val result = repository.browse(actualGame, _uiState.value.searchQuery, _uiState.value.sort, actualPage)
        _uiState.value = _uiState.value.copy(browseItems = result.items, page = result.page, hasNextPage = result.hasNextPage, isLoading = false)
    }

    fun openDetail(item: WorkshopBrowseItem) {
        if (_uiState.value.detail?.publishedFileId == item.publishedFileId && !_uiState.value.isLoading) return
        if (_uiState.value.selectedItem?.publishedFileId == item.publishedFileId && _uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(selectedItem = item, isLoading = true, error = null, detail = null, comments = null)
        launchLoad {
        val detail = repository.detail(item)
        _uiState.value = _uiState.value.copy(detail = detail, comments = null, isLoading = false)
        loadComments(detail, 1)
        }
    }

    fun openDetail(id: Long) {
        (_uiState.value.browseItems.firstOrNull { it.publishedFileId.toLong() == id }
            ?: _uiState.value.selectedItem?.takeIf { it.publishedFileId.toLong() == id })?.let(::openDetail)
    }

    fun loadComments(detail: WorkshopItemDetail? = null, page: Int = 1) = launchLoad {
        val actualDetail = detail ?: _uiState.value.detail ?: return@launchLoad
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val comments = runCatching { repository.comments(actualDetail, page) }
            .getOrElse { actualDetail.toUnavailableCommentPage() }
        _uiState.value = _uiState.value.copy(comments = comments, isLoading = false)
    }

    fun enqueueCurrent() {
        val detail = _uiState.value.detail ?: return
        repository.enqueue(detail, _uiState.value.accounts.activeAccountId)
    }

    fun refreshAccounts() { _uiState.value = _uiState.value.copy(accounts = repository.accounts()) }
    fun signIn(username: String, password: String) = launchLoad {
        if (username.isBlank() || password.isBlank()) { _uiState.value = _uiState.value.copy(authMessage = "credentials_required", isLoading = false); return@launchLoad }
        _uiState.value = _uiState.value.copy(isLoading = true, authMessage = null)
        applyAuthStep(repository.signIn(username.trim(), password, null))
    }
    fun submitGuardCode(code: String) = launchLoad {
        if (code.isBlank()) return@launchLoad
        _uiState.value = _uiState.value.copy(isLoading = true)
        applyAuthStep(repository.submitGuardCode(code.trim()))
    }
    fun waitForConfirmation() = launchLoad { _uiState.value = _uiState.value.copy(isLoading = true); applyAuthStep(repository.waitForConfirmation()) }
    fun setActiveAccount(account: SteamAccountSummary) { repository.setActiveAccount(account.accountId); refreshAccounts() }
    fun removeAccount(account: SteamAccountSummary) { repository.removeAccount(account.accountId); refreshAccounts() }

    private fun applyAuthStep(step: SteamSignInStep) {
        _uiState.value = when (step) {
            is SteamSignInStep.Success -> _uiState.value.copy(accounts = step.snapshot, authMessage = "success", isLoading = false)
            is SteamSignInStep.RequiresGuardCode -> _uiState.value.copy(authMessage = "guard_code", isLoading = false)
            is SteamSignInStep.AwaitingConfirmation -> _uiState.value.copy(authMessage = "device_confirmation", isLoading = false)
        }
    }

    private fun launchLoad(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { block() }.onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message ?: it.javaClass.simpleName) } }
    }

    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkshopViewModel(DefaultWorkshopRepositoryAdapter(context)) as T
        }
    }
}
