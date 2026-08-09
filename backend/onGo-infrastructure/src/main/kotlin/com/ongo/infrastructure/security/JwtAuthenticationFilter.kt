package com.ongo.infrastructure.security

import com.ongo.domain.auth.TokenBlacklistPort
import com.ongo.application.apikey.ApiKeyUseCase
import com.ongo.domain.apikey.ApiKeyRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val tokenBlacklist: TokenBlacklistPort,
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyUseCase: ApiKeyUseCase,
) : OncePerRequestFilter() {

    companion object {
        private val PUBLIC_PATHS = listOf(
            "/api/v1/auth/login/",
            "/api/v1/auth/refresh",
            "/api/v1/portone/webhook",
            "/swagger-ui/",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/api-docs",
            "/actuator/",
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return PUBLIC_PATHS.any { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (authenticateApiKey(request)) {
            filterChain.doFilter(request, response)
            return
        }

        val token = resolveToken(request)

        if (token != null && jwtTokenProvider.validateToken(token)) {
            val jti = jwtTokenProvider.getTokenJti(token)
                ?: token.hashCode().toString()
            if (tokenBlacklist.isBlacklisted(jti)) {
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.writer.write("{\"success\":false,\"message\":\"토큰이 무효화되었습니다\"}")
                response.contentType = "application/json;charset=UTF-8"
                return
            }

            val tokenType = jwtTokenProvider.getTokenType(token)
            if (tokenType == "access" || tokenType == "sse") {
                val userId = jwtTokenProvider.getUserIdFromToken(token)
                val role = jwtTokenProvider.getRoleFromToken(token)
                val authorities = mutableListOf(SimpleGrantedAuthority("ROLE_USER"))
                if (role == "ADMIN") {
                    authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))
                }
                val authentication = UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    authorities,
                )
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }

    /**
     * API keys are deliberately handled before JWT parsing. A key is not a JWT and must never
     * be passed to the JWT provider (which would create noisy parse failures for automation).
     */
    private fun authenticateApiKey(request: HttpServletRequest): Boolean {
        val rawKey = request.getHeader("X-API-Key")?.takeIf { it.isNotBlank() }
            ?: request.getHeader("Authorization")
                ?.takeIf { it.startsWith("Bearer og_live_", ignoreCase = false) }
                ?.substring(7)
            ?: request.getHeader("Authorization")
                ?.takeIf { it.startsWith("og_live_", ignoreCase = false) }
            ?: return false

        val apiKey = apiKeyRepository.findActiveByHash(apiKeyUseCase.hash(rawKey), java.time.LocalDateTime.now())
            ?: return false
        val authentication = UsernamePasswordAuthenticationToken(
            apiKey.userId,
            null,
            listOf(
                SimpleGrantedAuthority("ROLE_USER"),
                SimpleGrantedAuthority("AUTH_API_KEY"),
            ),
        )
        SecurityContextHolder.getContext().authentication = authentication
        apiKeyRepository.touchLastUsed(apiKey.id!!, java.time.LocalDateTime.now())
        return true
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        if (bearerToken?.startsWith("Bearer og_live_") == true) return null
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }
        // SSE 전용: 쿼리 파라미터 토큰은 sse 타입만 허용 (단기 만료 5분)
        // EventSource API가 커스텀 헤더를 지원하지 않으므로 쿼리 파라미터 사용
        val queryToken = request.getParameter("token")
        if (!queryToken.isNullOrBlank()) {
            // sse 타입 토큰만 쿼리 파라미터로 허용 (access/refresh 토큰 URL 노출 방지)
            try {
                val tokenType = jwtTokenProvider.getTokenType(queryToken)
                if (tokenType == "sse") {
                    return queryToken
                }
            } catch (_: Exception) {
                // 유효하지 않은 토큰 무시
            }
            return null
        }
        return null
    }
}
