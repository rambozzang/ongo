package com.ongo.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.auth.TokenBlacklistPort
import com.ongo.domain.apikey.ApiKeyRepository
import com.ongo.application.apikey.ApiKeyUseCase
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

/**
 * **실제 필터 체인**에서 토큰 갱신이 동결을 풀지 않는지 검증한다.
 *
 * 이 테스트가 따로 있는 이유가 있다. `AccountFreezeFilterTest` 는 `AccountFreezeFilter`
 * 하나만 단독 호출하면서 `SecurityContext` 에 principal 을 손으로 넣는다. 그런데
 * **실제 refresh 요청에서는 그런 일이 일어나지 않는다** — `JwtAuthenticationFilter` 의
 * `shouldNotFilter` 가 `/api/v1/auth/refresh` 를 public path 로 건너뛰므로 principal 이
 * 아예 만들어지지 않는다.
 *
 * 즉 단독 테스트만으로는 "refresh 가 막힌다"를 증명할 수 없다. 실제 체인에서 refresh 는
 * principal 없이 통과한다. 그게 **의도된 계약**이다(아래 A안).
 *
 * ## 채택한 계약 (A)
 *
 * refresh 는 공개 경로로 **허용한다.** 토큰 갱신 자체를 막으면 동결된 사용자가 로그아웃
 * 되기만 할 뿐, 삭제 진행 상황조차 확인하지 못한다.
 *
 * 요구사항은 "갱신이 동결을 풀지 않는다"이지 "갱신을 막는다"가 아니다.
 * 갱신으로 새 access token 을 받아도 **그 토큰으로 하는 쓰기는 여전히 409** 여야 한다.
 * 게이트는 토큰이 아니라 `users.deletion_state` 를 보기 때문이다.
 */
class RefreshDoesNotUnfreezeChainTest {

    private val guard = mockk<UserWriteGuard>()
    private val blacklist = mockk<TokenBlacklistPort>(relaxed = true)
    private val jwtProvider = mockk<JwtTokenProvider>(relaxed = true)
    private val apiKeyRepository = mockk<ApiKeyRepository>(relaxed = true)
    private val apiKeyUseCase = ApiKeyUseCase(apiKeyRepository)

    private val jwtFilter = JwtAuthenticationFilter(jwtProvider, blacklist, apiKeyRepository, apiKeyUseCase)
    private val freezeFilter = AccountFreezeFilter(guard, ObjectMapper())

    private companion object {
        const val USER_ID = 11L
    }

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        every { guard.requireWritable(USER_ID, any(), any()) } throws AccountFrozenException()
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    /** 두 필터를 실제 순서대로 태운다. */
    private fun runChain(method: String, uri: String, token: String? = null): MockHttpServletResponse {
        val req = MockHttpServletRequest(method, uri)
        token?.let { req.addHeader("Authorization", "Bearer $it") }
        val res = MockHttpServletResponse()

        // SecurityConfig 와 같은 순서: JwtAuthenticationFilter -> AccountFreezeFilter
        val chain = MockFilterChain(TerminalServlet(), jwtFilter, freezeFilter)
        chain.doFilter(req, res)
        return res
    }

    /** 체인 끝. 여기까지 왔다는 것은 두 필터를 모두 통과했다는 뜻이다. */
    private class TerminalServlet : jakarta.servlet.http.HttpServlet() {
        override fun service(req: jakarta.servlet.ServletRequest, res: jakarta.servlet.ServletResponse) {
            // 아무것도 하지 않는다. 상태는 200 으로 남는다.
        }
    }

    @Test
    @DisplayName("refresh 는 실제 체인에서 principal 이 만들어지지 않는다")
    fun refreshNeverGetsAPrincipal() {
        // JwtAuthenticationFilter 가 public path 로 건너뛰므로 인증 컨텍스트가 비어 있다.
        // 따라서 AccountFreezeFilter 는 판정할 대상이 없어 통과시킨다.
        val res = runChain("POST", "/api/v1/auth/refresh")

        assertNull(SecurityContextHolder.getContext().authentication) {
            "refresh 에서 principal 이 만들어졌다. shouldNotFilter 설계가 바뀌었다"
        }
        assertEquals(200, res.status) { "refresh 는 허용된다(A안). 막으면 로그아웃만 될 뿐이다" }
    }

    @Test
    @DisplayName("갱신 이후 access token 으로 하는 쓰기는 여전히 막힌다")
    fun writesAfterRefreshAreStillBlocked() {
        // 갱신으로 새 access token 을 받았다고 가정한다.
        every { jwtProvider.validateToken("new-access") } returns true
        every { jwtProvider.getTokenJti("new-access") } returns "jti-new"
        every { jwtProvider.getTokenType("new-access") } returns "access"
        every { jwtProvider.getUserIdFromToken("new-access") } returns USER_ID
        every { jwtProvider.getRoleFromToken("new-access") } returns "USER"
        every { blacklist.isBlacklisted("jti-new") } returns false

        val res = runChain("POST", "/api/v1/videos", token = "new-access")

        // 게이트는 토큰이 아니라 users.deletion_state 를 본다. 새 토큰이어도 소용없다.
        assertEquals(409, res.status) {
            "갱신한 토큰으로 쓰기가 통과했다. 갱신이 동결을 푼 것이다"
        }
        assertEquals(true, res.contentAsString.contains(AccountFrozenException.CODE))
    }
}
