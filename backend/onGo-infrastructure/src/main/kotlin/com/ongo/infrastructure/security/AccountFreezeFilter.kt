package com.ongo.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.ResData
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 삭제 요청으로 동결된 계정의 **쓰기**를 HTTP 진입점에서 막는다.
 *
 * `JwtAuthenticationFilter` 뒤에 놓는다. 인증이 끝나야 대상 사용자를 알 수 있다.
 *
 * ## 쓰기 메서드에만 적용한다
 *
 * `GET`/`HEAD`/`OPTIONS` 는 통과시킨다. 동결된 사용자도 자기 데이터와 삭제 진행 상황은
 * 볼 수 있어야 한다. 부수적으로, 읽기가 대부분인 트래픽에 게이트 조회 비용을 지우지 않는다.
 * (`JwtAuthenticationFilter` 는 DB 를 치지 않으므로 이 조회가 요청당 새 비용이다)
 *
 * ## 허용 목록은 메서드 + 정확한 경로다
 *
 * 접두사로 두면 하위 경로가 딸려 들어와 우회가 생긴다. `POST /api/v1/auth/logout` 을
 * 허용하려다 `/api/v1/auth/logout/everything` 까지 허용되면 안 된다.
 *
 * `refresh` 와 웹훅은 여기 없다. 둘 다 `JwtAuthenticationFilter` 의 public path 라
 * 인증 컨텍스트가 없고, 이 필터는 인증된 요청만 본다. 웹훅은 사용자 게이트가 아니라
 * `SystemWritePathRegistry` 쪽 문제다.
 */
@Component
class AccountFreezeFilter(
    private val userWriteGuard: UserWriteGuard,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    companion object {
        /** 동결 중에도 허용하는 (메서드, 정확한 경로). 최소로 유지한다. */
        private val ALLOWED = setOf(
            "DELETE /api/v1/auth/account",   // 삭제 요청 자체
            "POST /api/v1/auth/logout",      // 세션 종료
        )

        private val READ_METHODS = setOf("GET", "HEAD", "OPTIONS")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val method = request.method.uppercase()

        if (method in READ_METHODS) {
            filterChain.doFilter(request, response)
            return
        }

        // 접두사 매칭이 아니라 정확 일치다. 하위 경로가 딸려 들어오지 않게 한다.
        if ("$method ${request.requestURI}" in ALLOWED) {
            filterChain.doFilter(request, response)
            return
        }

        val userId = SecurityContextHolder.getContext().authentication?.principal as? Long
        if (userId == null) {
            // 인증되지 않은 요청은 이 필터의 관심사가 아니다. 인증 자체는 뒤에서 판정된다.
            filterChain.doFilter(request, response)
            return
        }

        try {
            userWriteGuard.requireWritable(userId)
        } catch (e: AccountFrozenException) {
            response.status = HttpServletResponse.SC_CONFLICT
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"
            objectMapper.writeValue(
                response.outputStream,
                ResData<Nothing>(success = false, message = e.message, error = AccountFrozenException.CODE),
            )
            return
        }

        filterChain.doFilter(request, response)
    }
}
