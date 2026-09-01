package com.zomdroid.workshop.data

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class WorkshopBrowseParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parse_extractsLegacyWorkshopItemAndPagination() {
        val payload = """
            <div class="workshopItem">
              <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=42" class="ugc" data-appid="108600" data-publishedfileid="42">
                <div id="sharedfile_42" class="workshopItemPreviewHolder"><img class="workshopItemPreviewImage" src="https://example.com/preview.png"></div>
              </a>
              <a href="https://steamcommunity.com/sharedfiles/filedetails/?id=42" class="item_link"><div class="workshopItemTitle ellipsis">Test map</div></a>
              <div class="workshopItemAuthorName ellipsis">by <a class="workshop_author_link" href="/id/author">Author</a></div>
            </div>
            <script>SharedFileBindMouseHover("sharedfile_42", false, {"id":"42","title":"Test map","description":"A fun mod"});</script>
            <a class="pagebtn" href="https://steamcommunity.com/workshop/browse/?appid=108600&p=2">Next</a>
        """.trimIndent()

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.items).containsExactly(
            WorkshopBrowseItem(
                appId = 108600u,
                publishedFileId = 42uL,
                title = "Test map",
                authorName = "Author",
                previewImageUrl = "https://example.com/preview.png",
                descriptionSnippet = "A fun mod",
            ),
        )
    }

    @Test
    fun parse_extractsSsrRenderContextAndFileSize() {
        val queryData = """
            {"queries":[{"state":{"data":{"current_page":2,"total_pages":3,"results":[
              {"publishedfileid":"43","creator":"7656119","consumer_appid":108600,
               "preview_url":"https://example.com/ssr.png","title":"SSR mod",
               "short_description":"SSR description","file_size":"123456"}
            ]}},"queryKey":["workshop_browse",108600,"trend"]}]}
        """.trimIndent()
        val renderContext = """{"queryData":${Json.encodeToString(queryData)}}"""
        val payload = """<script>window.SSR.renderContext=JSON.parse(${Json.encodeToString(renderContext)});</script>"""

        val page = WorkshopBrowseParser.parse(payload, page = 1, json = json)

        assertThat(page.page).isEqualTo(2)
        assertThat(page.hasNextPage).isTrue()
        assertThat(page.items.single().publishedFileId).isEqualTo(43uL)
        assertThat(page.items.single().fileSizeBytes).isEqualTo(123456L)
        assertThat(page.items.single().descriptionSnippet).isEqualTo("SSR description")
    }

    @Test
    fun parse_invalidOrEmptyPageReturnsEmptyItems() {
        val page = WorkshopBrowseParser.parse("<html>empty</html>", page = 4, json = json)

        assertThat(page.page).isEqualTo(4)
        assertThat(page.items).isEmpty()
        assertThat(page.hasNextPage).isFalse()
    }
}
