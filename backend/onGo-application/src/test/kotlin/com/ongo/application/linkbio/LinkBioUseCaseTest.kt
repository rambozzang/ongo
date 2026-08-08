package com.ongo.application.linkbio

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.linkbio.LinkBioLink
import com.ongo.domain.linkbio.LinkBioPage
import com.ongo.domain.linkbio.LinkBioRepository
import com.ongo.application.linkbio.dto.UpdateLinksRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LinkBioUseCaseTest {

    private val repository = mockk<LinkBioRepository>(relaxed = true)
    private val useCase = LinkBioUseCase(repository)

    @Test
    fun `empty link update removes the final persisted link`() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        every { repository.findPageByUserId(1L) } returns page
        every { repository.findLinksByPageId(5L) } returns emptyList()

        useCase.updateLinks(1L, UpdateLinksRequest(emptyList()))

        verify(exactly = 1) { repository.deleteAllLinksByPageId(5L) }
        verify(exactly = 0) { repository.saveLink(any()) }
    }

    @Test
    fun `public click increments only an active link on a published page`() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        val link = LinkBioLink(id = 7L, pageId = 5L, title = "YouTube", url = "https://youtube.com")
        every { repository.findPageBySlug("creator") } returns page
        every { repository.findLinksByPageId(5L) } returns listOf(link)

        useCase.recordPublicClick("creator", 7L)

        verify(exactly = 1) { repository.incrementClickCount(7L) }
    }

    @Test
    fun `public click rejects an unpublished page`() {
        every { repository.findPageBySlug("private") } returns LinkBioPage(
            id = 5L,
            userId = 1L,
            slug = "private",
            isPublished = false,
        )

        assertThrows<NotFoundException> {
            useCase.recordPublicClick("private", 7L)
        }
        verify(exactly = 0) { repository.incrementClickCount(any()) }
    }
}
