package com.ongo.api.auth

import com.ongo.api.auth.dto.RefreshTokenRequest
import com.ongo.api.auth.dto.SocialLoginRequest
import com.ongo.api.config.ClientAddressResolver
import com.ongo.application.auth.AuthOAuthAuthorizationUseCase
import com.ongo.application.auth.AuthRateLimitExceededException
import com.ongo.application.auth.AuthRateLimiter
import com.ongo.application.auth.AuthUseCase
import com.ongo.application.auth.dto.AuthResult
import com.ongo.application.auth.dto.UserResult
import com.ongo.common.exception.UnauthorizedException
import com.ongo.domain.auth.AuthTokenPort
import com.ongo.infrastructure.external.googledrive.OAuthStateManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals

/**
 * 컨트롤러가 상한 키를 **어떻게 만드는지** 고정한다.
 *
 * `ClientAddressResolver` 와 `AuthRateLimiter` 가 각각 옳아도, 컨트롤러가 예전처럼
 * `httpRequest.remoteAddr` 을 넘기면 결과는 그대로 전역 버킷이다. 그 배선이 살아 있는지는
 * 두 클래스의 단위 테스트로는 알 수 없다 — 여기서 실제 컨트롤러를 통해 확인한다.
 *
 * 상한값과 토큰 처리 흐름은 이번 범위에서 바꾸지 않았고, 이 테스트도 그것을 전제로 한다.
 */
class AuthControllerRateLimitKeyTest {

    private val loginCapacity = 5
    private val refreshCapacity = 10

    private val authUseCase = mockk<AuthUseCase>()
    private val authTokenPort = mockk<AuthTokenPort>(relaxed = true)
    private val oAuthStateManager = mockk<OAuthStateManager>(relaxed = true)
    private val authOAuthAuthorizationUseCase = mockk<AuthOAuthAuthorizationUseCase>(relaxed = true)

    /** 상한기와 주소 판정은 실제 구현을 쓴다 — 목으로 바꾸면 배선을 검증하지 못한다. */
    private val rateLimiter = AuthRateLimiter()

    private val controller = AuthController(
        authUseCase = authUseCase,
        authTokenPort = authTokenPort,
        authRateLimiter = rateLimiter,
        oAuthStateManager = oAuthStateManager,
        authOAuthAuthorizationUseCase = authOAuthAuthorizationUseCase,
        clientAddressResolver = ClientAddressResolver(),
    )

    private fun request(remoteAddr: String, realIp: String? = null, forwardedFor: String? = null) =
        MockHttpServletRequest().apply {
            this.remoteAddr = remoteAddr
            realIp?.let { addHeader(ClientAddressResolver.REAL_IP_HEADER, it) }
            forwardedFor?.let { addHeader(ClientAddressResolver.FORWARDED_FOR_HEADER, it) }
        }

    private fun refresh(remoteAddr: String, realIp: String? = null, forwardedFor: String? = null) {
        controller.refreshToken(RefreshTokenRequest("refresh-token"), request(remoteAddr, realIp, forwardedFor))
    }

    private fun login(remoteAddr: String, realIp: String? = null, forwardedFor: String? = null) {
        controller.socialLogin(
            provider = "google",
            request = SocialLoginRequest(code = "code", redirectUri = "https://ongo.example/callback", state = "state"),
            httpRequest = request(remoteAddr, realIp, forwardedFor),
        )
    }

    private fun givenRefreshSucceeds() {
        every { authUseCase.refreshToken(any()) } returns AuthResult(
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token",
            user = UserResult(
                id = 1L,
                email = "creator@example.com",
                name = "creator",
                nickname = null,
                profileImageUrl = null,
                planType = "FREE",
                role = "USER",
                onboardingCompleted = true,
            ),
            isNewUser = false,
        )
    }

    /**
     * **한 사용자가 갱신 상한을 채워도 다른 사용자는 계속 갱신할 수 있다.**
     *
     * 이것이 깨지면 프런트가 갱신 실패를 받고, 그 다음은 세션이 끊기는 경험이다.
     */
    @Test
    @DisplayName("프록시 뒤에서 갱신 상한은 클라이언트별로 나뉜다")
    fun refreshLimitIsPerClientBehindTheProxy() {
        givenRefreshSucceeds()

        repeat(refreshCapacity) { refresh("127.0.0.1", realIp = "203.0.113.10") }
        assertThrows<AuthRateLimitExceededException> { refresh("127.0.0.1", realIp = "203.0.113.10") }

        // 다른 사용자는 아무 영향도 받지 않는다.
        repeat(refreshCapacity) { refresh("127.0.0.1", realIp = "203.0.113.11") }
    }

    @Test
    @DisplayName("프록시 뒤에서 로그인 상한은 클라이언트별로 나뉜다")
    fun loginLimitIsPerClientBehindTheProxy() {
        // 상한을 통과하면 state 검증까지 진행된다(여기서는 실패). 상한에 걸리면 그 전에 멈춘다.
        repeat(loginCapacity) {
            assertThrows<UnauthorizedException> { login("127.0.0.1", realIp = "203.0.113.10") }
        }
        assertThrows<AuthRateLimitExceededException> { login("127.0.0.1", realIp = "203.0.113.10") }

        repeat(loginCapacity) {
            assertThrows<UnauthorizedException> { login("127.0.0.1", realIp = "203.0.113.11") }
        }
    }

    /** X-Real-IP 가 없는 배포에서도 XFF 마지막 hop 으로 나뉘어야 한다. */
    @Test
    @DisplayName("X-Real-IP 가 없으면 XFF 마지막 hop 으로 나뉜다")
    fun fallsBackToTheForwardedForHop() {
        givenRefreshSucceeds()

        repeat(refreshCapacity) { refresh("127.0.0.1", forwardedFor = "203.0.113.10") }
        assertThrows<AuthRateLimitExceededException> { refresh("127.0.0.1", forwardedFor = "203.0.113.10") }

        repeat(refreshCapacity) { refresh("127.0.0.1", forwardedFor = "203.0.113.11") }
    }

    /**
     * **앞 hop 을 조작해도 상한을 벗어나지 못한다.**
     *
     * nginx 는 XFF 에 덧붙이므로 앞쪽 항목은 클라이언트가 써 넣은 값이다. 앞에서부터 읽으면
     * 공격자가 요청마다 다른 값을 넣어 상한을 그대로 통과한다.
     */
    @Test
    @DisplayName("XFF 앞 hop 을 매번 바꿔도 상한을 벗어나지 못한다")
    fun forgedLeadingHopsCannotEscapeTheLimit() {
        givenRefreshSucceeds()

        repeat(refreshCapacity) { attempt ->
            refresh("127.0.0.1", forwardedFor = "10.0.0.$attempt, 203.0.113.10")
        }

        assertThrows<AuthRateLimitExceededException> {
            refresh("127.0.0.1", forwardedFor = "10.0.0.99, 203.0.113.10")
        }
    }

    /**
     * **8070 에 직접 접속해도 위조 헤더로 상한을 늘릴 수 없다.**
     *
     * 지금 백엔드는 모든 인터페이스에 바인딩돼 있고 외부 차단은 방화벽에만 의존한다.
     * 그 방어가 사라져도 헤더 위조로는 상한을 벗어나지 못해야 한다.
     */
    @Test
    @DisplayName("비신뢰 피어는 헤더를 바꿔도 하나의 상한을 공유한다")
    fun anUntrustedPeerCannotForgeItsWayPastTheLimit() {
        givenRefreshSucceeds()

        repeat(refreshCapacity) { attempt ->
            refresh("203.0.113.50", realIp = "10.0.0.$attempt", forwardedFor = "10.1.0.$attempt")
        }

        assertThrows<AuthRateLimitExceededException> {
            refresh("203.0.113.50", realIp = "10.0.0.99", forwardedFor = "10.1.0.99")
        }
    }

    /**
     * **IPv6 클라이언트가 주소를 바꿔가며 상한을 벗어나지 못한다.**
     *
     * nginx 는 IPv6 를 받고, ISP 는 가입자에게 /64 이상을 위임한다. 주소 하나를 키로 쓰면
     * 한 사용자가 사실상 무한한 키를 갖게 되어 IPv6 쪽에만 상한이 사라진다.
     */
    @Test
    @DisplayName("IPv6 주소를 /64 안에서 바꿔도 상한을 벗어나지 못한다")
    fun rotatingIpv6AddressesCannotEscapeTheLimit() {
        givenRefreshSucceeds()

        repeat(refreshCapacity) { attempt ->
            refresh("127.0.0.1", realIp = "2001:db8:1:2::$attempt")
        }

        assertThrows<AuthRateLimitExceededException> {
            refresh("127.0.0.1", realIp = "2001:db8:1:2:aaaa:bbbb:cccc:dddd")
        }
    }

    /** 다른 가입자(/64 가 다름)는 그 영향을 받지 않아야 한다. */
    @Test
    @DisplayName("다른 /64 의 IPv6 사용자는 영향받지 않는다")
    fun aDifferentIpv6SubscriberIsUnaffected() {
        givenRefreshSucceeds()
        repeat(refreshCapacity) { refresh("127.0.0.1", realIp = "2001:db8:1:2::1") }
        assertThrows<AuthRateLimitExceededException> { refresh("127.0.0.1", realIp = "2001:db8:1:2::9") }

        repeat(refreshCapacity) { refresh("127.0.0.1", realIp = "2001:db8:1:3::1") }
    }

    /** 상한을 넘지 않는 한 기존 토큰 응답은 그대로다 — 이번 변경은 키만 바꾼다. */
    @Test
    @DisplayName("상한 안에서는 갱신 응답이 예전 그대로다")
    fun tokenResponseIsUnchangedWithinTheLimit() {
        givenRefreshSucceeds()

        val response = controller.refreshToken(
            RefreshTokenRequest("refresh-token"),
            request("127.0.0.1", realIp = "203.0.113.10"),
        )

        assertEquals(true, response.body?.success)
        assertEquals("new-access-token", response.body?.data?.accessToken)
        assertEquals("new-refresh-token", response.body?.data?.refreshToken)
        assertEquals("토큰이 갱신되었습니다", response.body?.message)
    }
}
