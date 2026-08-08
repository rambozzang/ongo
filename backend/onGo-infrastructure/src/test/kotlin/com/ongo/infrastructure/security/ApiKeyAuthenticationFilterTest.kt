package com.ongo.infrastructure.security

import com.ongo.application.apikey.ApiKeyUseCase
import com.ongo.domain.apikey.ApiKey
import com.ongo.domain.apikey.ApiKeyRepository
import com.ongo.domain.auth.TokenBlacklistPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class ApiKeyAuthenticationFilterTest {
    private val jwtProvider = mockk<JwtTokenProvider>(relaxed = true)
    private val blacklist = mockk<TokenBlacklistPort>(relaxed = true)
    private val repository = mockk<ApiKeyRepository>(relaxed = true)
    private val useCase = ApiKeyUseCase(repository)
    private val filter = JwtAuthenticationFilter(jwtProvider, blacklist, repository, useCase)
    private val chain = mockk<FilterChain>(relaxed = true)

    @BeforeEach
    fun setUp() = SecurityContextHolder.clearContext()

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `X-API-Key authenticates as the owning user`() {
        val raw = "og_live_test-secret"
        every { repository.findActiveByHash(useCase.hash(raw), any()) } returns apiKey()

        val request = MockHttpServletRequest("GET", "/api/v1/videos")
        request.addHeader("X-API-Key", raw)
        filter.doFilter(request, MockHttpServletResponse(), chain)

        val authentication = requireNotNull(SecurityContextHolder.getContext().authentication)
        assertEquals(77L, authentication.principal)
        assertTrue(authentication.authorities.any { it.authority == "AUTH_API_KEY" })
        verify { repository.touchLastUsed(7L, any()) }
        verify { chain.doFilter(any(), any()) }
    }

    @Test
    fun `Bearer API key authenticates without invoking JWT validation`() {
        val raw = "og_live_test-secret"
        every { repository.findActiveByHash(useCase.hash(raw), any()) } returns apiKey()

        val request = MockHttpServletRequest("GET", "/api/v1/videos")
        request.addHeader("Authorization", "Bearer $raw")
        filter.doFilter(request, MockHttpServletResponse(), chain)

        assertEquals(77L, requireNotNull(SecurityContextHolder.getContext().authentication).principal)
        verify(exactly = 0) { jwtProvider.validateToken(any()) }
    }

    private fun apiKey() = ApiKey(
        id = 7L,
        userId = 77L,
        name = "test",
        keyPrefix = "og_live_test",
        keyHash = useCase.hash("og_live_test-secret"),
    )
}
