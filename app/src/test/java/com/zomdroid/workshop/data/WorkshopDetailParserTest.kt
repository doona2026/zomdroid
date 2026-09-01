package com.zomdroid.workshop.data

import com.google.common.truth.Truth.assertThat
import com.zomdroid.workshop.SteamLanguagePreference
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

class WorkshopDetailParserTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun loadWorkshopItemDetail_parsesApiCommunityMetadataAndCommentsContext() = runBlocking {
        server.enqueue(
            MockResponse.Builder().body(
                """{"response":{"publishedfiledetails":[{"publishedfileid":"42","title":"API title","description":"[h1]API description[/h1][img]https://example.com/api.png[/img]","preview_url":"https://example.com/preview.png","file_size":"1024","time_updated":"1700000000","subscriptions":"7","views":"8","tags":[{"tag":"Maps"}]}]}}""",
            ).build(),
        )
        server.enqueue(
            MockResponse.Builder().body(
                """<div class="workshopItemTitle">Community title</div>
<div class="workshopItemDescription" id="highlightContent"><div class="bb_h1">Community description</div><br><img src="https://example.com/community.jpg">Details</div>
<script>var rgFullScreenshotURLs = [{ 'previewid' : '1', 'url': 'https://example.com/gallery-1.jpg' }, { 'previewid' : '2', 'url': 'https://example.com/gallery-2.jpg' }]; var g_sessionID = 'session-1'; InitializeCommentThread('PublishedFile_Public','PublishedFile_Public_123_42',{'feature':'42','feature2':-1,'owner':'76561198000000001','total_count':12,'start':0,'pagesize':10},'https://example.com/comment/PublishedFile_Public/',40);</script>""",
            ).build(),
        )

        val repository = WorkshopDetailRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            communityBaseUrl = server.url("/"),
            languagePreferenceProvider = { SteamLanguagePreference.SimplifiedChinese },
        )
        val result = repository.loadWorkshopItemDetail(
            WorkshopBrowseItem(
                appId = 108600u,
                publishedFileId = 42uL,
                title = "Fallback title",
                authorName = "Author",
                previewImageUrl = "https://example.com/thumb.png",
                descriptionSnippet = "Fallback description",
            ),
        )

        assertThat(result.title).isEqualTo("Community title")
        assertThat(result.description).contains("Community description")
        assertThat(result.galleryImageUrls).containsExactly(
            "https://example.com/gallery-1.jpg",
            "https://example.com/gallery-2.jpg",
        ).inOrder()
        assertThat(result.descriptionBlocks.map { it.text to it.imageUrl }).containsExactly(
            "Community description" to null,
            "" to "https://example.com/community.jpg",
            "Details" to null,
        ).inOrder()
        assertThat(result.fileSizeBytes).isEqualTo(1024)
        assertThat(result.tags).containsExactly("Maps")
        assertThat(result.commentCount).isEqualTo(12)
        assertThat(result.commentThreadContext?.sessionId).isEqualTo("session-1")
        assertThat(result.workshopUrl).contains("id=42")
        assertThat(result.workshopUrl).contains("l=schinese")
    }

    @Test
    fun loadWorkshopCommentPage_parsesPaginationAndCommentText() = runBlocking {
        val detail = WorkshopItemDetail(
            appId = 108600u,
            publishedFileId = 42uL,
            title = "Item",
            authorName = "Author",
            previewImageUrl = "",
            description = "",
            fileSizeBytes = null,
            timeUpdatedEpochSeconds = null,
            subscriptions = null,
            favorited = null,
            views = null,
            tags = emptyList(),
            workshopUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=42",
            changeNotesUrl = "",
            commentsUrl = "",
            commentThreadContext = WorkshopCommentThreadContext(
                ownerId = "76561198000000001",
                featureId = "42",
                sessionId = "session-1",
                feature2 = "-1",
                extendedData = "",
            ),
            commentCount = 11,
        )
        server.enqueue(
            MockResponse.Builder().body(
                """{"total_count":11,"start":10,"pagesize":10,"comments_html":"<div class=\"commentthread_comment\" id=\"comment_1\"><a class=\"commentthread_author_link\" href=\"/id/player\">Player</a><div class=\"commentthread_comment_text\">Hello <b>world</b></div></div>"}""",
            ).build(),
        )

        val repository = WorkshopDetailRepository(
            client = OkHttpClient(),
            baseUrl = server.url("/"),
            communityBaseUrl = server.url("/"),
        )
        val result = repository.loadWorkshopCommentPage(detail, 2)

        assertThat(result.page).isEqualTo(2)
        assertThat(result.totalPages).isEqualTo(2)
        assertThat(result.hasPreviousPage).isTrue()
        assertThat(result.hasNextPage).isFalse()
        assertThat(result.comments.single().authorName).isEqualTo("Player")
        assertThat(result.comments.single().content).contains("Hello world")
    }
}
