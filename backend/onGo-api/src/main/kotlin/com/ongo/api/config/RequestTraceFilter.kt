package com.ongo.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * 모든 HTTP 요청에 고유 requestId를 부여하여 분산 추적을 지원합니다.
 * - MDC에 requestId 설정 → 로그 패턴에서 %X{requestId}로 출력
 * - 응답 헤더 X-Request-Id로 클라이언트에 전달
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestTraceFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader("X-Request-Id")
            ?: UUID.randomUUID().toString().substring(0, 8)

        MDC.put("requestId", requestId)
        response.setHeader("X-Request-Id", requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
        }
    }
}
