package com.ongo.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

/**
 * 동결 계정의 HTTP 쓰기 차단 계약을 고정한다.
 *
 * 특히 **허용 목록이 접두사가 아니라 메서드 + 정확한 경로**라는 점을 본다.
 * 접두사로 두면 하위 경로가 딸려 들어와 우회가 생긴다.
 */
class AccountFreezeFilterTest {

    private val guard = mockk<UserWriteGuard>()
    private val filter = AccountFreezeFilter(guard, ObjectMapper())
    private lateinit var chain: FilterChain

    private companion object {
        const val USER_ID = 7L
    }

    @BeforeEach
    fun setUp() {
        chain = mockk(relaxed = true)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(USER_ID, null, emptyList())
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    private fun run(method: String, uri: String): MockHttpServletResponse {
        val req = MockHttpServletRequest(method, uri)
        val res = MockHttpServletResponse()
        filter.doFilter(req, res, chain)
        return res
    }

    private fun frozen() {
        every { guard.requireWritable(USER_ID, any(), any()) } throws AccountFrozenException()
    }

    private fun active() {
        every { guard.requireWritable(USER_ID, any(), any()) } returns Unit
    }

    @Test
    @DisplayName("동결 계정의 쓰기는 409 로 막힌다")
    fun frozenWriteIsBlocked() {
        frozen()

        val res = run("POST", "/api/v1/videos")

        assertEquals(409, res.status)
        assertTrue(res.contentAsString.contains(AccountFrozenException.CODE)) {
            "안정적인 코드가 없다: ${res.contentAsString}"
        }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }

    @Test
    @DisplayName("동결 계정도 읽기는 허용한다 — 자기 데이터와 진행 상황은 봐야 한다")
    fun frozenReadIsAllowed() {
        frozen()

        listOf("GET", "HEAD", "OPTIONS").forEach { m ->
            val res = run(m, "/api/v1/videos")
            assertEquals(200, res.status) { "$m 이 막혔다" }
        }
        // 읽기에는 게이트 조회 자체를 하지 않는다. 요청당 DB 비용을 지우지 않기 위해서다.
        verify(exactly = 0) { guard.requireWritable(any(), any(), any()) }
    }

    @Test
    @DisplayName("삭제 요청과 로그아웃은 동결 중에도 통과한다")
    fun allowlistedPathsPassWhileFrozen() {
        frozen()

        assertEquals(200, run("DELETE", "/api/v1/auth/account").status)
        assertEquals(200, run("POST", "/api/v1/auth/logout").status)
        verify(exactly = 0) { guard.requireWritable(any(), any(), any()) }
    }

    @Test
    @DisplayName("허용 목록은 접두사가 아니다 — 하위 경로가 딸려 들어오지 않는다")
    fun allowlistIsExactPathNotPrefix() {
        frozen()

        // /api/v1/auth/account 를 허용한다고 그 아래가 열리면 안 된다.
        assertEquals(409, run("DELETE", "/api/v1/auth/account/data").status)
        assertEquals(409, run("POST", "/api/v1/auth/logout/everything").status)
    }

    @Test
    @DisplayName("허용 목록은 메서드까지 본다 — 같은 경로의 다른 메서드는 막힌다")
    fun allowlistMatchesMethodToo() {
        frozen()

        // DELETE 만 허용했는데 POST 로 같은 경로를 치면 막혀야 한다.
        assertEquals(409, run("POST", "/api/v1/auth/account").status)
        assertEquals(409, run("PUT", "/api/v1/auth/account").status)
    }

    @Test
    @DisplayName("정상 계정의 쓰기는 통과한다")
    fun activeAccountWritesPass() {
        active()

        val res = run("POST", "/api/v1/videos")

        assertEquals(200, res.status)
        verify(exactly = 1) { chain.doFilter(any(), any()) }
    }

    @Test
    @DisplayName("인증되지 않은 요청은 이 필터가 판정하지 않는다")
    fun unauthenticatedRequestIsNotThisFiltersConcern() {
        SecurityContextHolder.clearContext()

        val res = run("POST", "/api/v1/videos")

        // 인증 자체는 뒤에서 401 로 판정된다. 여기서 409 를 내면 원인을 오해하게 만든다.
        assertEquals(200, res.status)
        verify(exactly = 0) { guard.requireWritable(any(), any(), any()) }
    }

    @Test
    @DisplayName("갱신 이후의 쓰기는 여전히 막힌다")
    fun writesStayBlockedAfterRefresh() {
        frozen()

        // 갱신은 세션을 잇는 것이지 게이트를 푸는 것이 아니다.
        // 게이트는 토큰이 아니라 users.deletion_state 를 본다.
        assertEquals(409, run("POST", "/api/v1/videos").status)
    }

    // refresh 요청 자체가 이 필터에서 어떻게 다뤄지는지는 여기서 검증하지 않는다.
    //
    // 이 테스트는 필터를 단독 호출하면서 principal 을 손으로 넣는다. 그런데 실제
    // refresh 요청에는 principal 이 만들어지지 않는다 — JwtAuthenticationFilter 의
    // shouldNotFilter 가 public path 로 건너뛰기 때문이다. 여기서 refresh 를 흉내 내면
    // **일어날 수 없는 상황**을 검증하게 되고, 실제로 그런 테스트를 썼다가 계약을
    // 잘못 적어뒀다.
    //
    // 실제 체인 기준 동작은 RefreshDoesNotUnfreezeChainTest 가 두 필터를 함께 태워 고정한다.
}
