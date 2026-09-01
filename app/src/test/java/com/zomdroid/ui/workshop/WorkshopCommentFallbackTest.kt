package com.zomdroid.ui.workshop

import com.google.common.truth.Truth.assertThat
import com.zomdroid.workshop.data.WorkshopItemDetail
import com.zomdroid.workshop.data.WorkshopComment
import org.junit.Test

class WorkshopCommentFallbackTest {
    @Test
    fun missingThreadContextProducesNonPaginatedUnavailablePage() {
        val comment = WorkshopComment(
            id = "1",
            authorName = "Player",
            profileUrl = "",
            content = "Already embedded",
        )
        val detail = WorkshopItemDetail(
            appId = 108600u,
            publishedFileId = 42uL,
            title = "Modern Status",
            authorName = "Author",
            previewImageUrl = "",
            description = "Description",
            fileSizeBytes = null,
            timeUpdatedEpochSeconds = null,
            subscriptions = null,
            favorited = null,
            views = null,
            tags = emptyList(),
            workshopUrl = "https://steamcommunity.com/sharedfiles/filedetails/?id=42",
            changeNotesUrl = "",
            commentsUrl = "https://steamcommunity.com/comment/PublishedFile_Public/42",
            commentCount = 1,
            comments = listOf(comment),
        )

        val page = detail.toUnavailableCommentPage()

        assertThat(page.comments).containsExactly(comment)
        assertThat(page.page).isEqualTo(1)
        assertThat(page.totalPages).isNull()
        assertThat(page.hasPreviousPage).isFalse()
        assertThat(page.hasNextPage).isFalse()
    }
}
