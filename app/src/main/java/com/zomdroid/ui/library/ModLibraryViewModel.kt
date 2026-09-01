package com.zomdroid.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.ui.workshop.DefaultWorkshopRepositoryAdapter
import com.zomdroid.workshop.library.ModLibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModLibraryUiState(val entries: List<ModLibraryEntry> = emptyList(), val query: String = "", val selected: ModLibraryEntry? = null, val isLoading: Boolean = false, val message: String? = null)

class ModLibraryViewModel(context: Context) : ViewModel() {
    private val repository = DefaultWorkshopRepositoryAdapter(context)
    private val _uiState = MutableStateFlow(ModLibraryUiState())
    val uiState: StateFlow<ModLibraryUiState> = _uiState.asStateFlow()
    init { refresh() }
    fun refresh() { _uiState.value = _uiState.value.copy(entries = repository.library().entries, selected = _uiState.value.selected?.let { old -> repository.library().entries.firstOrNull { it.publishedFileId == old.publishedFileId } }) }
    fun setQuery(query: String) { _uiState.value = _uiState.value.copy(query = query) }
    fun select(entry: ModLibraryEntry?) { _uiState.value = _uiState.value.copy(selected = entry) }
    fun checkUpdate(entry: ModLibraryEntry) = viewModelScope.launch(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val result = runCatching { repository.checkUpdate(entry, force = true) }.getOrNull()
        refresh()
        _uiState.value = _uiState.value.copy(isLoading = false, message = when { result == null -> "check_failed"; result.updateAvailable -> "update_available"; else -> "up_to_date" })
    }
    fun remove(entry: ModLibraryEntry) { repository.removeLibraryEntry(entry); refresh() }
    fun cleanup() { val count = repository.cleanupLibrary(); refresh(); _uiState.value = _uiState.value.copy(message = "cleanup:$count") }
    fun visibleEntries(): List<ModLibraryEntry> = _uiState.value.entries.filter { entry -> _uiState.value.query.isBlank() || entry.title.contains(_uiState.value.query, true) || entry.publishedFileId.toString().contains(_uiState.value.query) }
    companion object {
        fun factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ModLibraryViewModel(context) as T
        }
    }
}
