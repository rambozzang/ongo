package com.ongo.infrastructure.security

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
) : OncePerRequestFilter() {

    companion object {
        private val PUBLIC_PATHS = listOf(
            "/api/v1/auth/login/",
            "/api/v1/auth/refresh",
            "/api/v1/auth/dev-login",
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
        val token = resolveToken(request)

        if (token != null && jwtTokenProvider.validateToken(token)) {
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

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
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
