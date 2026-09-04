package com.zomdroid.workshop.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import com.zomdroid.workshop.WorkshopBrowseSortOption
import com.zomdroid.workshop.WorkshopBrowseTimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import com.zomdroid.workshop.network.createWorkshopCatalogHttpClient

object WorkshopCatalogRuntime {
    interface BrowseCallback {
        fun onSuccess(page: WorkshopBrowsePage)
        fun onError(message: String)
    }

    interface DetailCallback {
        fun onSuccess(detail: WorkshopItemDetail)
        fun onError(message: String)
    }

    interface CommentsCallback {
        fun onSuccess(page: WorkshopCommentPage)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var client: OkHttpClient? = null
    private val browseCache = ConcurrentHashMap<BrowseCacheKey, BrowseCacheEntry>()
    private val imageCache = object : LruCache<String, Bitmap>(IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @JvmStatic
    fun browse(context: Context, search: String, sortIndex: Int, page: Int, callback: BrowseCallback) {
        val normalizedSearch = search.trim()
        val cacheKey = BrowseCacheKey(normalizedSearch, sortIndex, page)
        val cached = browseCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.createdAtMillis <= BROWSE_CACHE_TTL_MILLIS }
            ?.page
        if (cached != null) {
            mainHandler.post { callback.onSuccess(cached) }
            return
        }
        scope.launch {
            runCatching {
                // Keep cache-directory setup and Repository construction off the UI thread.
                val repository = WorkshopBrowseRepository(client(context))
                val sort = WorkshopBrowseSortOption.entries.getOrElse(sortIndex) { WorkshopBrowseSortOption.MostPopular }
                repository.browseGameWorkshop(
                    appId = PROJECT_ZOMBOID_APP_ID,
                    searchQuery = normalizedSearch,
                    sortOption = sort,
                    timeWindow = WorkshopBrowseTimeWindow.OneWeek,
                    page = page,
                )
            }.onSuccess { result ->
                browseCache[cacheKey] = BrowseCacheEntry(result, System.currentTimeMillis())
                mainHandler.post { callback.onSuccess(result) }
            }
                .onFailure { error -> mainHandler.post { callback.onError(error.message ?: "Workshop request failed") } }
        }
    }

    @JvmStatic
    fun clearBrowseCache() {
        browseCache.clear()
    }

    @JvmStatic
    fun detail(context: Context, item: WorkshopBrowseItem, includeChangeNotes: Boolean, callback: DetailCallback) {
        scope.launch {
            runCatching {
                WorkshopDetailRepository(client(context)).loadWorkshopItemDetail(item, includeChangeNotes)
            }
                .onSuccess { result -> mainHandler.post { callback.onSuccess(result) } }
                .onFailure { error -> mainHandler.post { callback.onError(error.message ?: "Workshop detail request failed") } }
        }
    }

    @JvmStatic
    fun comments(context: Context, detail: WorkshopItemDetail, page: Int, callback: CommentsCallback) {
        scope.launch {
            runCatching {
                WorkshopDetailRepository(client(context)).loadWorkshopCommentPage(detail, page)
            }
                .onSuccess { result -> mainHandler.post { callback.onSuccess(result) } }
                .onFailure { error -> mainHandler.post { callback.onError(error.message ?: "Workshop comments request failed") } }
        }
    }

    @JvmStatic
    fun item(appId: Int, publishedFileId: Long, title: String, author: String, previewUrl: String, description: String): WorkshopBrowseItem =
        WorkshopBrowseItem(appId.toUInt(), publishedFileId.toULong(), title, author, previewUrl, description)

    @JvmStatic fun appId(item: WorkshopBrowseItem): Int = item.appId.toInt()
    @JvmStatic fun publishedFileId(item: WorkshopBrowseItem): Long = item.publishedFileId.toLong()
    @JvmStatic fun detailAppId(detail: WorkshopItemDetail): Int = detail.appId.toInt()
    @JvmStatic fun detailPublishedFileId(detail: WorkshopItemDetail): Long = detail.publishedFileId.toLong()
    @JvmStatic fun requiredAppId(item: WorkshopRequiredItem): Int = item.appId.toInt()
    @JvmStatic fun requiredPublishedFileId(item: WorkshopRequiredItem): Long = item.publishedFileId.toLong()

    @JvmStatic
    fun loadImage(context: Context, url: String?, target: ImageView) {
        if (url.isNullOrBlank()) return
        target.setTag(url)
        imageCache.get(url)?.let { cached ->
            target.setImageBitmap(cached)
            return
        }
        scope.launch {
            runCatching {
                client(context).newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.byteStream()?.use(BitmapFactory::decodeStream) else null
                }
            }.getOrNull()?.let { bitmap ->
                imageCache.put(url, bitmap)
                mainHandler.post {
                    if (url == target.getTag()) {
                        target.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun client(context: Context): OkHttpClient {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val created = createWorkshopCatalogHttpClient(context)
            client = created
            return created
        }
    }

    const val PROJECT_ZOMBOID_APP_ID: UInt = 108600u

    private data class BrowseCacheKey(val search: String, val sortIndex: Int, val page: Int)
    private data class BrowseCacheEntry(val page: WorkshopBrowsePage, val createdAtMillis: Long)

    private const val BROWSE_CACHE_TTL_MILLIS = 5 * 60 * 1000L
    private const val IMAGE_CACHE_BYTES = 12 * 1024 * 1024
}
