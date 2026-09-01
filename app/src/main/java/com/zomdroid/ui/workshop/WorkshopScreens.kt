package com.zomdroid.ui.workshop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import com.zomdroid.ui.component.ZomdroidLiquidButton as Button
import com.zomdroid.ui.component.ZomdroidLiquidChip as FilterChip
import androidx.compose.material3.Icon
import com.zomdroid.ui.component.ZomdroidLiquidIconButton as IconButton
import com.zomdroid.ui.component.ZomdroidLinearProgressIndicator as LinearProgressIndicator
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidSectionLabel
import com.zomdroid.ui.component.ZomdroidAsyncContent
import com.zomdroid.ui.component.ZomdroidEmptyState
import com.zomdroid.ui.component.ZomdroidErrorState
import com.zomdroid.workshop.WorkshopBrowseSortOption
import com.zomdroid.workshop.auth.SteamAccountSummary
import com.zomdroid.workshop.data.WorkshopBrowseItem
import com.zomdroid.workshop.data.WorkshopItemDetail

@Composable
fun WorkshopScreen(
    viewModel: WorkshopViewModel,
    onOpenDetail: (Long) -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ZomdroidGlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    AsyncImage(state.selectedGame?.headerImageUrl, stringResource(R.string.workshop_cover), Modifier.size(104.dp, 58.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(state.selectedGame?.name ?: stringResource(R.string.workshop_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.workshop_title), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onOpenAccount) { Icon(Icons.Default.AccountCircle, stringResource(R.string.workshop_account_menu)) }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.workshop_search_hint)) },
                singleLine = true,
                trailingIcon = { IconButton(onClick = { viewModel.loadBrowse(page = 1) }) { Icon(Icons.Default.Search, stringResource(R.string.workshop_search)) } },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stringArrayResource(R.array.workshop_sort_options).forEachIndexed { index, label ->
                    FilterChip(
                        selected = state.sort.ordinal == index,
                        onClick = { viewModel.setSort(WorkshopBrowseSortOption.entries[index]) },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { error -> item { ZomdroidErrorState(error, onRetry = { viewModel.loadBrowse(page = state.page) }) } }
        item { Text(stringResource(R.string.workshop_page_format, state.page), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!state.isLoading && state.error == null && state.browseItems.isEmpty()) item { ZomdroidEmptyState(stringResource(R.string.workshop_empty)) }
        items(state.browseItems, key = { it.publishedFileId.toString() }) { item -> WorkshopCard(item) { onOpenDetail(item.publishedFileId.toLong()); viewModel.openDetail(item) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(enabled = state.page > 1 && !state.isLoading, onClick = { viewModel.loadBrowse(page = state.page - 1) }) { Text(stringResource(R.string.workshop_previous)) }
                Text(stringResource(R.string.workshop_page_format, state.page), Modifier.padding(top = 12.dp))
                OutlinedButton(enabled = state.hasNextPage && !state.isLoading, onClick = { viewModel.loadBrowse(page = state.page + 1) }) { Text(stringResource(R.string.workshop_next)) }
            }
        }
    }
}

@Composable fun WorkshopCard(item: WorkshopBrowseItem, onClick: () -> Unit) {
    ZomdroidGlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AsyncImage(item.previewImageUrl, stringResource(R.string.workshop_cover), Modifier.size(132.dp, 84.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.workshop_author_format, item.authorName), style = MaterialTheme.typography.bodySmall)
                if (item.fileSizeBytes != null) Text(formatBytes(item.fileSizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(item.descriptionSnippet, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun WorkshopDetailScreen(viewModel: WorkshopViewModel, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    val detail = state.detail
    if (detail == null) {
        ZomdroidAsyncContent(
            value = detail,
            loading = state.isLoading,
            errorMessage = state.error,
            isEmpty = true,
            onRetry = { state.selectedItem?.let(viewModel::openDetail) },
            empty = { ZomdroidEmptyState(stringResource(R.string.workshop_detail_missing)) },
            content = {},
        )
        return
    }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }; Text(detail.title, style = MaterialTheme.typography.headlineSmall) } }
        val gallery = detail.galleryImageUrls.ifEmpty { listOf(detail.previewImageUrl) }.filter(String::isNotBlank).distinct()
        if (gallery.isNotEmpty()) item {
            val galleryState = rememberLazyListState()
            androidx.compose.foundation.lazy.LazyRow(
                state = galleryState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(gallery, key = { it }) { url ->
                    AsyncImage(url, stringResource(R.string.workshop_cover), Modifier.fillMaxWidth().height(300.dp), contentScale = ContentScale.Fit)
                }
            }
            if (gallery.size > 1) {
                Text(
                    stringResource(R.string.workshop_image_page_format, galleryState.firstVisibleItemIndex + 1, gallery.size),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.workshop_author_format, detail.authorName)); Text(stringResource(R.string.workshop_meta_format, detail.fileSizeBytes?.let(::formatBytes) ?: "?", detail.subscriptions ?: "?", detail.views ?: "?")); Text(detail.tags.joinToString(" · ")); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { viewModel.enqueueCurrent() }) { Icon(Icons.Default.Download, null); Text(stringResource(R.string.workshop_download)) }; OutlinedButton(onClick = { onOpenUrl(detail.workshopUrl) }) { Icon(Icons.Default.OpenInNew, null); Text(stringResource(R.string.workshop_open_steam)) } } } } }
        item { ZomdroidSectionLabel(stringResource(R.string.workshop_details)) }
        if (detail.descriptionBlocks.isEmpty()) {
            item { Text(detail.description) }
        } else {
            detail.descriptionBlocks.forEach { block ->
                if (block.text.isNotBlank()) item { Text(block.text) }
                if (!block.imageUrl.isNullOrBlank()) item { AsyncImage(block.imageUrl, stringResource(R.string.workshop_description_image), Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit) }
            }
        }
        if (detail.changeNotes.isNotBlank()) item { ZomdroidSectionLabel(stringResource(R.string.workshop_change_notes)); Text(detail.changeNotes) }
        if (detail.requiredItems.isNotEmpty()) item { ZomdroidSectionLabel(stringResource(R.string.workshop_dependencies)); detail.requiredItems.forEach { dependency -> WorkshopCard(dependency.toBrowseItem()) { viewModel.openDetail(dependency.toBrowseItem()) } } }
        item {
            ZomdroidSectionLabel(stringResource(R.string.workshop_comments))
            if (state.comments?.comments.isNullOrEmpty()) Text(stringResource(R.string.workshop_comments_unavailable))
            else state.comments?.comments?.forEach { comment -> ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(comment.authorName, style = MaterialTheme.typography.labelLarge); Text(comment.content) } } }
        }
        state.comments?.let { comments -> item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(enabled = comments.hasPreviousPage, onClick = { viewModel.loadComments(page = comments.page - 1) }) { Text(stringResource(R.string.workshop_previous)) }; OutlinedButton(enabled = comments.hasNextPage, onClick = { viewModel.loadComments(page = comments.page + 1) }) { Text(stringResource(R.string.workshop_next)) } } } }
    }
}

@Composable
fun WorkshopAccountScreen(viewModel: WorkshopViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }; Text(stringResource(R.string.workshop_account_title), style = MaterialTheme.typography.headlineSmall) } }
        item { Text(stringResource(R.string.workshop_account_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.accounts.accounts.forEach { account -> item { AccountRow(account, state.accounts.activeAccountId == account.accountId, viewModel) } }
        item { OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workshop_account_username)) }) }
        item { OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workshop_account_password)) }) }
        item { Button(enabled = !state.isLoading, onClick = { viewModel.signIn(username, password) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.workshop_account_sign_in)) } }
        when (state.authMessage) {
            "guard_code" -> { item { Text(stringResource(R.string.workshop_account_guard_message)) }; item { OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workshop_account_guard_code)) }) }; item { Button(onClick = { viewModel.submitGuardCode(code) }) { Text(stringResource(R.string.workshop_account_submit_code)) } } }
            "device_confirmation" -> { item { Text(stringResource(R.string.workshop_account_confirmation_message)) }; item { Button(onClick = viewModel::waitForConfirmation) { Text(stringResource(R.string.workshop_account_wait)) } } }
            "success" -> item { Text(stringResource(R.string.workshop_account_ready)) }
            "credentials_required" -> item { Text(stringResource(R.string.workshop_account_missing_credentials), color = MaterialTheme.colorScheme.error) }
        }
        if (state.accounts.accounts.isEmpty()) item { Text(stringResource(R.string.workshop_account_anonymous)) }
    }
}

@Composable private fun AccountRow(account: SteamAccountSummary, active: Boolean, viewModel: WorkshopViewModel) {
    ZomdroidGlassCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(Modifier.weight(1f)) { Text(account.accountName); Text(if (active) stringResource(R.string.workshop_account_ready) else account.accountId, style = MaterialTheme.typography.bodySmall) }; if (!active) OutlinedButton(onClick = { viewModel.setActiveAccount(account) }) { Text(stringResource(R.string.workshop_account_menu)) }; OutlinedButton(onClick = { viewModel.removeAccount(account) }) { Text(stringResource(R.string.workshop_account_remove)) } } }
}

private fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f); bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f)); else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f)) }

@Composable private fun <T> StateFlowCompat(state: kotlinx.coroutines.flow.StateFlow<T>): T = state.collectAsStateWithLifecycleCompat().value
@Composable private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> = this.collectAsState()
