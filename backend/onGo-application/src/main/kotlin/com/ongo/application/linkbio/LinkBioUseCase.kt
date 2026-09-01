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
    /**
     * 공개 클릭 집계의 남용 상한. **인증된 편집·저장 경로는 이것을 지나지 않는다** —
     * [recordPublicClick] 에서만 쓴다.
     */
    private val clickRateLimiter: LinkBioClickRateLimiter,
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
            buttonColor = request.buttonColor,
            buttonTextColor = request.buttonTextColor,
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
            buttonColor = request.buttonColor ?: page.buttonColor,
            buttonTextColor = request.buttonTextColor ?: page.buttonTextColor,
        )
        val saved = linkBioRepository.updatePage(updated)
        val links = linkBioRepository.findLinksByPageId(saved.id!!)
        return saved.toResponse(links)
    }

    @Transactional
    fun updateLinks(userId: Long, request: UpdateLinksRequest): LinkBioPageResponse {
        val page = linkBioRepository.findPageByUserId(userId) ?: throw NotFoundException("링크 페이지", userId)
        val pageId = page.id!!

        /*
         * **저장하기 전에 전부 검증한다.**
         *
         * 아래에서 기존 링크를 지우고 다시 넣으므로, 중간에 하나가 실패하면 `@Transactional`
         * 이 되돌린다 해도 굳이 지웠다 넣는 일을 할 이유가 없다. 무엇보다 **어떤 링크도
         * 저장되지 않았다는 것**이 사용자에게 명확해야 한다.
         */
        request.links.forEach { validateLinkUrl(it.url) }

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
            buttonColor = page.buttonColor,
            buttonTextColor = page.buttonTextColor,
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

    @Transactional
    /**
     * 공개 페이지의 링크 클릭을 집계한다. **인증이 없는 경로다.**
     *
     * 상한 검사를 **DB 조회보다 먼저** 한다. 뒤에 두면 상한을 넘긴 요청도 매번 페이지·링크
     * 조회를 일으켜, 집계는 막아도 DB 부하는 그대로 받는다. 상한은 인메모리 판정이라
     * 여기서 끊는 것이 가장 싸다.
     *
     * 집계 기준과 IP 대신 링크로 세는 이유는 [LinkBioClickRateLimiter] 에 있다.
     */
    fun recordPublicClick(slug: String, linkId: Long) {
        clickRateLimiter.checkClickRateLimit(linkId)
        val page = linkBioRepository.findPageBySlug(slug) ?: throw NotFoundException("링크 페이지", slug)
        if (!page.isPublished) throw NotFoundException("링크 페이지", slug)
        val link = linkBioRepository.findLinksByPageId(page.id!!).firstOrNull { it.id == linkId && it.isActive }
            ?: throw NotFoundException("링크", linkId)
        linkBioRepository.incrementClickCount(link.id!!)
    }

    private fun validateSlug(slug: String) {
        val slugRegex = Regex("^[a-zA-Z0-9-]+$")
        if (!slugRegex.matches(slug)) {
            throw IllegalArgumentException("Slug는 영문, 숫자, 하이픈만 사용할 수 있습니다.")
        }
    }

    /**
     * `link_bio_links.url` 검증. **공개 페이지 방문자가 클릭하는 주소다.**
     *
     * ## 왜 서버가 막아야 하나
     *
     * 화면(`isValidLinkUrl`)이 이미 같은 규칙으로 거르지만, 그것은 **우리 화면을 쓸 때만**
     * 통한다. 인증된 사용자가 이 API 를 직접 부르면 빈 문자열·`https://`·`javascript:`·
     * 상대 경로가 그대로 저장됐다. 계약은 서버에 있어야 한다.
     *
     * ## 규칙
     *
     * - 공백만 있는 값은 주소가 아니다.
     * - `http`/`https` 만 허용한다. 다른 스킴은 링크가 아니라 **실행·앱 호출**이 되고,
     *   특히 `javascript:` 는 방문자 브라우저에서 코드가 돈다.
     * - 호스트가 있어야 한다. `https://` 는 파싱은 되지만 가리키는 곳이 없다.
     *   상대 경로(`/내-페이지`)는 스킴이 없어 방문자 기준으로 우리 도메인이 된다.
     *
     * [java.net.URI] 는 형식만 본다 — 접속 가능 여부는 확인하지 않는다(하지도 말아야
     * 한다. 저장할 때마다 외부로 요청을 보내게 된다).
     */
    private fun validateLinkUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("링크 주소를 입력해주세요.")
        }

        val parsed = try {
            java.net.URI(trimmed)
        } catch (e: java.net.URISyntaxException) {
            throw IllegalArgumentException("링크 주소 형식이 올바르지 않습니다: $trimmed")
        }

        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("링크 주소는 http:// 또는 https:// 로 시작해야 합니다: $trimmed")
        }
        if (parsed.host.isNullOrBlank()) {
            throw IllegalArgumentException("링크 주소에 사이트 주소가 없습니다: $trimmed")
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
        buttonColor = buttonColor,
        buttonTextColor = buttonTextColor,
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
