package com.zomdroid.workshop.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.zomdroid.workshop.WorkshopBrowseSortOption
import com.zomdroid.workshop.WorkshopBrowseTimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

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

    @JvmStatic
    fun browse(context: Context, search: String, sortIndex: Int, page: Int, callback: BrowseCallback) {
        val repository = WorkshopBrowseRepository(client(context))
        val sort = WorkshopBrowseSortOption.entries.getOrElse(sortIndex) { WorkshopBrowseSortOption.MostPopular }
        scope.launch {
            runCatching {
                repository.browseGameWorkshop(
                    appId = PROJECT_ZOMBOID_APP_ID,
                    searchQuery = search,
                    sortOption = sort,
                    timeWindow = WorkshopBrowseTimeWindow.OneWeek,
                    page = page,
                )
            }.onSuccess { result -> mainHandler.post { callback.onSuccess(result) } }
                .onFailure { error -> mainHandler.post { callback.onError(error.message ?: "Workshop request failed") } }
        }
    }

    @JvmStatic
    fun detail(context: Context, item: WorkshopBrowseItem, includeChangeNotes: Boolean, callback: DetailCallback) {
        val repository = WorkshopDetailRepository(client(context))
        scope.launch {
            runCatching { repository.loadWorkshopItemDetail(item, includeChangeNotes) }
                .onSuccess { result -> mainHandler.post { callback.onSuccess(result) } }
                .onFailure { error -> mainHandler.post { callback.onError(error.message ?: "Workshop detail request failed") } }
        }
    }

    @JvmStatic
    fun comments(context: Context, detail: WorkshopItemDetail, page: Int, callback: CommentsCallback) {
        val repository = WorkshopDetailRepository(client(context))
        scope.launch {
            runCatching { repository.loadWorkshopCommentPage(detail, page) }
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
        scope.launch {
            runCatching {
                client(context).newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.byteStream()?.use(BitmapFactory::decodeStream) else null
                }
            }.getOrNull()?.let { bitmap -> mainHandler.post { target.setImageBitmap(bitmap) } }
        }
    }

    @JvmStatic
    fun client(context: Context): OkHttpClient {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val cacheDir = File(context.applicationContext.cacheDir, "workshop-catalog-http")
            val created = OkHttpClient.Builder()
                .cache(Cache(cacheDir, 5L * 1024L * 1024L))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
            client = created
            return created
        }
    }

    const val PROJECT_ZOMBOID_APP_ID: UInt = 108600u
}
