package com.ongo.application.linkbio

import com.ongo.application.linkbio.dto.*
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.linkbio.LinkBioLink
import com.ongo.domain.linkbio.LinkBioPage
import com.ongo.domain.linkbio.LinkBioRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LinkBioUseCase(
    private val linkBioRepository: LinkBioRepository,
) {

    fun getPage(userId: Long): LinkBioPageResponse? {
        val page = linkBioRepository.findPageByUserId(userId) ?: return null
        val links = linkBioRepository.findLinksByPageId(page.id!!)
        return page.toResponse(links)
    }

    @Transactional
    fun createPage(userId: Long, request: CreatePageRequest): LinkBioPageResponse {
        validateSlug(request.slug)
        val existing = linkBioRepository.findPageBySlug(request.slug)
        if (existing != null) throw DuplicateResourceException("링크 페이지", request.slug)

        val page = LinkBioPage(
            userId = userId,
            slug = request.slug,
            title = request.title,
            bio = request.bio,
            avatarUrl = request.avatarUrl,
            theme = request.theme,
            backgroundColor = request.backgroundColor,
            textColor = request.textColor,
        )
        val saved = linkBioRepository.savePage(page)
        return saved.toResponse(emptyList())
    }

    @Transactional
    fun updatePage(userId: Long, request: UpdatePageRequest): LinkBioPageResponse {
        val page = linkBioRepository.findPageByUserId(userId) ?: throw NotFoundException("링크 페이지", userId)

        if (request.slug != null && request.slug != page.slug) {
            validateSlug(request.slug)
            val existing = linkBioRepository.findPageBySlug(request.slug)
            if (existing != null) throw DuplicateResourceException("링크 페이지", request.slug)
        }

        val updated = page.copy(
            slug = request.slug ?: page.slug,
            title = request.title ?: page.title,
            bio = request.bio ?: page.bio,
            avatarUrl = request.avatarUrl ?: page.avatarUrl,
            theme = request.theme ?: page.theme,
            backgroundColor = request.backgroundColor ?: page.backgroundColor,
            textColor = request.textColor ?: page.textColor,
        )
        val saved = linkBioRepository.updatePage(updated)
        val links = linkBioRepository.findLinksByPageId(saved.id!!)
        return saved.toResponse(links)
    }

    @Transactional
    fun updateLinks(userId: Long, request: UpdateLinksRequest): LinkBioPageResponse {
        val page = linkBioRepository.findPageByUserId(userId) ?: throw NotFoundException("링크 페이지", userId)
        val pageId = page.id!!

        linkBioRepository.deleteAllLinksByPageId(pageId)

        val savedLinks = request.links.map { item ->
            linkBioRepository.saveLink(
                LinkBioLink(
                    pageId = pageId,
                    title = item.title,
                    url = item.url,
                    icon = item.icon,
                    sortOrder = item.sortOrder,
                    isActive = item.isActive,
                )
            )
        }

        return page.toResponse(savedLinks)
    }

    @Transactional
    fun togglePublish(userId: Long, request: PublishRequest): LinkBioPageResponse {
        val page = linkBioRepository.findPageByUserId(userId) ?: throw NotFoundException("링크 페이지", userId)
        val updated = page.copy(isPublished = request.isPublished)
        val saved = linkBioRepository.updatePage(updated)
        val links = linkBioRepository.findLinksByPageId(saved.id!!)
        return saved.toResponse(links)
    }

    fun getPublicPage(slug: String): LinkBioPublicResponse {
        val page = linkBioRepository.findPageBySlug(slug) ?: throw NotFoundException("링크 페이지", slug)
        if (!page.isPublished) throw NotFoundException("링크 페이지", slug)
        val pageId = page.id!!

        linkBioRepository.incrementViewCount(pageId)

        val links = linkBioRepository.findLinksByPageId(pageId).filter { it.isActive }
        return LinkBioPublicResponse(
            slug = page.slug,
            title = page.title,
            bio = page.bio,
            avatarUrl = page.avatarUrl,
            theme = page.theme,
            backgroundColor = page.backgroundColor,
            textColor = page.textColor,
            links = links.map { PublicLinkResponse(id = it.id!!, title = it.title, url = it.url, icon = it.icon, sortOrder = it.sortOrder) },
        )
    }

    fun getAnalytics(userId: Long): LinkBioAnalyticsResponse {
        val page = linkBioRepository.findPageByUserId(userId)
            ?: return LinkBioAnalyticsResponse(viewCount = 0, links = emptyList())
        val links = linkBioRepository.findLinksByPageId(page.id!!)
        return LinkBioAnalyticsResponse(
            viewCount = page.viewCount,
            links = links.map { LinkClickAnalytics(id = it.id!!, title = it.title, clickCount = it.clickCount) },
        )
    }

    private fun validateSlug(slug: String) {
        val slugRegex = Regex("^[a-zA-Z0-9-]+$")
        if (!slugRegex.matches(slug)) {
            throw IllegalArgumentException("Slug는 영문, 숫자, 하이픈만 사용할 수 있습니다.")
        }
    }

    private fun LinkBioPage.toResponse(links: List<LinkBioLink>): LinkBioPageResponse = LinkBioPageResponse(
        id = id!!,
        slug = slug,
        title = title,
        bio = bio,
        avatarUrl = avatarUrl,
        theme = theme,
        backgroundColor = backgroundColor,
        textColor = textColor,
        isPublished = isPublished,
        viewCount = viewCount,
        links = links.map { it.toResponse() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun LinkBioLink.toResponse(): LinkBioLinkResponse = LinkBioLinkResponse(
        id = id!!,
        title = title,
        url = url,
        icon = icon,
        sortOrder = sortOrder,
        clickCount = clickCount,
        isActive = isActive,
        createdAt = createdAt,
    )
}
