package com.ongo.application.linkbio

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.linkbio.LinkBioLink
import com.ongo.domain.linkbio.LinkBioPage
import com.ongo.domain.linkbio.LinkBioRepository
import com.ongo.application.linkbio.dto.LinkItem
import com.ongo.application.linkbio.dto.UpdateLinksRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinkBioUseCaseTest {

    private val repository = mockk<LinkBioRepository>(relaxed = true)

    /*
     * 실제 상한기를 쓴다. 목으로 바꾸면 "상한기를 불렀다" 까지만 재고, 정작 중요한
     * **막혔을 때 집계가 일어나지 않는가** 는 검증되지 않는다. 상한(분당 300)이 아래
     * 테스트들의 호출 수보다 훨씬 커서 기존 케이스에는 영향이 없다.
     */
    private val clickRateLimiter = LinkBioClickRateLimiter()
    private val useCase = LinkBioUseCase(repository, clickRateLimiter)

    @Test
    fun `empty link update removes the final persisted link`() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        every { repository.findPageByUserId(1L) } returns page
        every { repository.findLinksByPageId(5L) } returns emptyList()

        useCase.updateLinks(1L, UpdateLinksRequest(emptyList()))

        verify(exactly = 1) { repository.deleteAllLinksByPageId(5L) }
        verify(exactly = 0) { repository.saveLink(any()) }
    }

    // ══ 링크 주소 검증 ══════════════════════════════════════════════════════
    //
    // 화면(`isValidLinkUrl`)이 같은 규칙으로 이미 거르지만 그것은 **우리 화면을 쓸 때만**
    // 통한다. 인증된 사용자가 이 API 를 직접 부르면 빈 문자열·`https://`·`javascript:`·
    // 상대 경로가 그대로 저장됐고, 공개 페이지 방문자가 그 주소를 클릭했다.
    //
    // 저장되는 값은 남이 클릭하는 `href` 다. 계약은 서버에 있어야 한다.

    private fun givenPage() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        every { repository.findPageByUserId(1L) } returns page
        every { repository.findLinksByPageId(5L) } returns emptyList()
    }

    private fun updateWith(url: String) =
        useCase.updateLinks(1L, UpdateLinksRequest(listOf(LinkItem(title = "내 채널", url = url))))

    /** **이 값들이 그대로 저장되던 자리다.** */
    @Test
    @DisplayName("주소가 비어 있으면 저장하지 않는다")
    fun blankUrlIsRejected() {
        givenPage()

        for (blank in listOf("", "   ", "\t")) {
            assertThrows<IllegalArgumentException> { updateWith(blank) }
        }
        // 하나도 저장되지 않아야 한다 — 지웠다 넣는 일조차 하지 않는다.
        verify(exactly = 0) { repository.saveLink(any()) }
        verify(exactly = 0) { repository.deleteAllLinksByPageId(any()) }
    }

    @Test
    @DisplayName("프로토콜만 있는 주소는 저장하지 않는다")
    fun schemeOnlyUrlIsRejected() {
        givenPage()

        for (schemeOnly in listOf("https://", "http://")) {
            assertThrows<IllegalArgumentException> { updateWith(schemeOnly) }
        }
        verify(exactly = 0) { repository.saveLink(any()) }
    }

    /** `javascript:` 는 링크가 아니라 방문자 브라우저에서 도는 코드다. */
    @Test
    @DisplayName("http/https 가 아닌 스킴은 저장하지 않는다")
    fun nonHttpSchemeIsRejected() {
        givenPage()

        for (scheme in listOf("javascript:alert(1)", "ftp://example.com", "data:text/html,x")) {
            assertThrows<IllegalArgumentException> { updateWith(scheme) }
        }
        verify(exactly = 0) { repository.saveLink(any()) }
    }

    /** 상대 경로는 방문자 기준으로 우리 도메인이 된다 — 크리에이터가 의도한 링크가 아니다. */
    @Test
    @DisplayName("상대 경로는 저장하지 않는다")
    fun relativeUrlIsRejected() {
        givenPage()

        for (relative in listOf("/my-page", "example.com", "www.example.com/path")) {
            assertThrows<IllegalArgumentException> { updateWith(relative) }
        }
        verify(exactly = 0) { repository.saveLink(any()) }
    }

    /** 사용자가 무엇을 고쳐야 하는지 알 수 있어야 한다. */
    @Test
    @DisplayName("거부 사유를 한국어로 알린다")
    fun rejectionMessageIsKorean() {
        givenPage()

        val blank = assertThrows<IllegalArgumentException> { updateWith("") }
        val scheme = assertThrows<IllegalArgumentException> { updateWith("javascript:alert(1)") }
        val host = assertThrows<IllegalArgumentException> { updateWith("https://") }

        assertTrue(blank.message!!.contains("입력"), blank.message)
        assertTrue(scheme.message!!.contains("http"), scheme.message)
        assertTrue(host.message!!.contains("주소"), host.message)
    }

    /** **기존 유효 URL 은 그대로 저장된다.** 과도한 차단 회귀를 막는다. */
    @Test
    @DisplayName("유효한 http/https 주소는 그대로 저장한다")
    fun validUrlsAreSaved() {
        givenPage()
        val saved = mutableListOf<LinkBioLink>()
        every { repository.saveLink(capture(saved)) } answers { firstArg<LinkBioLink>().copy(id = 9L) }

        for (valid in listOf("https://youtube.com/@creator", "http://example.com/path?q=1")) {
            updateWith(valid)
        }

        assertEquals(
            listOf("https://youtube.com/@creator", "http://example.com/path?q=1"),
            saved.map { it.url },
        )
    }

    /** 여러 링크 중 하나만 잘못돼도 전부 저장하지 않는다 — 부분 저장은 더 혼란스럽다. */
    @Test
    @DisplayName("한 링크라도 주소가 잘못되면 전부 저장하지 않는다")
    fun oneInvalidUrlRejectsTheWholeBatch() {
        givenPage()

        assertThrows<IllegalArgumentException> {
            useCase.updateLinks(
                1L,
                UpdateLinksRequest(
                    listOf(
                        LinkItem(title = "정상", url = "https://example.com"),
                        LinkItem(title = "잘못됨", url = "https://"),
                    ),
                ),
            )
        }

        verify(exactly = 0) { repository.saveLink(any()) }
        verify(exactly = 0) { repository.deleteAllLinksByPageId(any()) }
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

    /* ---- 공개 클릭 집계 남용 상한 ---- */

    /**
     * 상한을 넘으면 **집계하지 않는다.**
     *
     * 이 엔드포인트는 인증이 없고 `slug`·`linkId` 가 공개 응답에 있어, 상한이 없으면
     * 누구나 크리에이터의 클릭 수를 무제한으로 올릴 수 있었다. 그 숫자는 성과 화면에
     * 그대로 보고된다 — 이번 세션이 없애 온 "가짜 수치" 가 외부 입력으로 되살아나는 경로다.
     */
    @Test
    @DisplayName("클릭 상한을 넘으면 집계하지 않고 오류로 알린다")
    fun `public click beyond the rate limit is not counted`() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        val link = LinkBioLink(id = 7L, pageId = 5L, title = "YouTube", url = "https://youtube.com")
        every { repository.findPageBySlug("creator") } returns page
        every { repository.findLinksByPageId(5L) } returns listOf(link)

        // 상한(분당 300)까지는 정상 집계된다.
        repeat(300) { useCase.recordPublicClick("creator", 7L) }
        verify(exactly = 300) { repository.incrementClickCount(7L) }

        val error = assertThrows<LinkBioClickRateLimitExceededException> {
            useCase.recordPublicClick("creator", 7L)
        }

        assertEquals("LINKBIO_CLICK_RATE_LIMIT_EXCEEDED", error.code)
        // 넘긴 뒤에도 집계 횟수는 그대로다.
        verify(exactly = 300) { repository.incrementClickCount(7L) }
    }

    /**
     * 상한 검사는 **DB 조회보다 먼저** 끝나야 한다.
     *
     * 뒤에 두면 집계는 막아도 요청마다 페이지·링크 조회가 그대로 일어나, 남용이
     * 집계 오염 대신 DB 부하로 바뀔 뿐이다.
     */
    @Test
    @DisplayName("상한을 넘은 요청은 페이지 조회조차 하지 않는다")
    fun `rate limited click does not touch the database`() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        val link = LinkBioLink(id = 7L, pageId = 5L, title = "YouTube", url = "https://youtube.com")
        every { repository.findPageBySlug("creator") } returns page
        every { repository.findLinksByPageId(5L) } returns listOf(link)
        repeat(300) { useCase.recordPublicClick("creator", 7L) }

        io.mockk.clearMocks(repository, answers = false)

        assertThrows<LinkBioClickRateLimitExceededException> {
            useCase.recordPublicClick("creator", 7L)
        }

        verify(exactly = 0) { repository.findPageBySlug(any()) }
        verify(exactly = 0) { repository.findLinksByPageId(any()) }
    }

    /**
     * **인증된 편집 경로는 이 상한을 지나지 않는다.**
     *
     * 상한을 공용 경로에 잘못 걸면 크리에이터가 자기 페이지를 저장하지 못하게 된다.
     * 링크 하나의 클릭 상한을 모두 소진한 상태에서도 편집이 정상 동작해야 한다.
     */
    @Test
    @DisplayName("클릭 상한이 소진돼도 인증된 링크 저장은 막히지 않는다")
    fun `authenticated link update is unaffected by the click rate limit`() {
        val page = LinkBioPage(id = 5L, userId = 1L, slug = "creator", isPublished = true)
        val link = LinkBioLink(id = 7L, pageId = 5L, title = "YouTube", url = "https://youtube.com")
        every { repository.findPageBySlug("creator") } returns page
        every { repository.findLinksByPageId(5L) } returns listOf(link)
        every { repository.findPageByUserId(1L) } returns page
        repeat(300) { useCase.recordPublicClick("creator", 7L) }
        assertThrows<LinkBioClickRateLimitExceededException> { useCase.recordPublicClick("creator", 7L) }

        useCase.updateLinks(
            1L,
            UpdateLinksRequest(links = listOf(LinkItem(title = "YouTube", url = "https://youtube.com"))),
        )

        verify(exactly = 1) { repository.deleteAllLinksByPageId(5L) }
    }
}
