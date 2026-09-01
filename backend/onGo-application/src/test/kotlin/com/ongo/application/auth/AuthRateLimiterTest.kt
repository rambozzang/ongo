package com.ongo.application.auth

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * 로그인·토큰 갱신 상한의 경계와 **격리**를 고정한다.
 *
 * ## 왜 격리가 핵심인가
 *
 * 이 상한기는 호출자가 준 키로 버킷을 나눈다. 그런데 nginx 뒤에서 `remoteAddr` 을 그대로
 * 넘기던 동안에는 모든 요청의 키가 `127.0.0.1` 하나였다. 버킷이 하나면 상한기는 공격자를
 * 막는 대신 **정상 사용자 전원을 막는다** — 로그인은 15분에 5회, 갱신은 1분에 10회가
 * 서비스 전체의 상한이 된다.
 *
 * 그래서 여기서 "다른 키는 서로의 몫을 쓰지 않는다"를 못 박는다. 키를 어떻게 만드는지는
 * `ClientAddressResolver` 의 몫이고, 이 클래스는 **받은 키를 실제로 구분하는지**만 책임진다.
 *
 * 상한값 자체(5/15분, 10/1분)는 이번 범위에서 바꾸지 않았고, 경계를 그대로 고정한다.
 */
class AuthRateLimiterTest {

    private val loginCapacity = 5
    private val refreshCapacity = 10

    /* ── 경계 ──────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("로그인은 상한까지 통과하고 그 다음 하나가 막힌다")
    fun loginAllowsExactlyItsCapacity() {
        val limiter = AuthRateLimiter()

        repeat(loginCapacity) { limiter.checkLoginRateLimit("203.0.113.10") }

        val error = assertThrows<AuthRateLimitExceededException> {
            limiter.checkLoginRateLimit("203.0.113.10")
        }
        assertEquals("AUTH_RATE_LIMIT_EXCEEDED", error.code)
    }

    @Test
    @DisplayName("갱신은 상한까지 통과하고 그 다음 하나가 막힌다")
    fun refreshAllowsExactlyItsCapacity() {
        val limiter = AuthRateLimiter()

        repeat(refreshCapacity) { limiter.checkRefreshRateLimit("203.0.113.10") }

        val error = assertThrows<AuthRateLimitExceededException> {
            limiter.checkRefreshRateLimit("203.0.113.10")
        }
        assertEquals("AUTH_RATE_LIMIT_EXCEEDED", error.code)
    }

    /** 상한을 넘긴 뒤에도 계속 막혀야 한다 — 버킷을 매번 새로 만들면 상한이 무의미하다. */
    @Test
    @DisplayName("상한을 넘긴 뒤 반복 호출도 계속 막힌다")
    fun keepsRejectingAfterCapacityIsGone() {
        val limiter = AuthRateLimiter()
        repeat(refreshCapacity) { limiter.checkRefreshRateLimit("203.0.113.10") }

        repeat(5) {
            assertThrows<AuthRateLimitExceededException> {
                limiter.checkRefreshRateLimit("203.0.113.10")
            }
        }
    }

    /* ── 격리: 이 파일의 존재 이유 ────────────────────────────────────── */

    /**
     * **한 사용자가 상한을 채워도 다른 사용자는 영향받지 않는다.**
     *
     * 이 단정이 깨지면 공격자 한 명이 서비스 전체의 로그인을 멈출 수 있다.
     */
    @Test
    @DisplayName("서로 다른 키의 로그인 상한은 서로를 막지 않는다")
    fun exhaustingOneLoginKeyDoesNotBlockAnother() {
        val limiter = AuthRateLimiter()
        repeat(loginCapacity) { limiter.checkLoginRateLimit("203.0.113.10") }
        assertThrows<AuthRateLimitExceededException> { limiter.checkLoginRateLimit("203.0.113.10") }

        // 다른 클라이언트는 자기 몫을 그대로 갖는다.
        repeat(loginCapacity) { limiter.checkLoginRateLimit("203.0.113.11") }
        assertThrows<AuthRateLimitExceededException> { limiter.checkLoginRateLimit("203.0.113.11") }
    }

    @Test
    @DisplayName("서로 다른 키의 갱신 상한은 서로를 막지 않는다")
    fun exhaustingOneRefreshKeyDoesNotBlockAnother() {
        val limiter = AuthRateLimiter()
        repeat(refreshCapacity) { limiter.checkRefreshRateLimit("203.0.113.10") }
        assertThrows<AuthRateLimitExceededException> { limiter.checkRefreshRateLimit("203.0.113.10") }

        repeat(refreshCapacity) { limiter.checkRefreshRateLimit("203.0.113.11") }
    }

    /** 여러 사용자가 섞여 들어와도 각자 자기 몫만 쓴다. */
    @Test
    @DisplayName("많은 키가 동시에 들어와도 각자의 몫이 유지된다")
    fun manyKeysEachKeepTheirOwnAllowance() {
        val limiter = AuthRateLimiter()
        val keys = (1..50).map { "203.0.113.$it" }

        // 모두가 상한 직전까지 쓴다.
        repeat(refreshCapacity) { keys.forEach { limiter.checkRefreshRateLimit(it) } }

        // 그리고 모두가 정확히 여기서 막힌다 — 누구도 남의 몫을 먹지 않았다는 뜻이다.
        keys.forEach { key ->
            assertThrows<AuthRateLimitExceededException>("키 $key 가 상한을 넘겨 통과했다") {
                limiter.checkRefreshRateLimit(key)
            }
        }
    }

    /**
     * 로그인 버킷과 갱신 버킷은 별개다.
     *
     * 합쳐지면 로그인 시도를 반복한 사용자가 자기 세션 갱신까지 막히게 된다.
     */
    @Test
    @DisplayName("로그인 상한을 다 써도 같은 키의 갱신은 막히지 않는다")
    fun loginAndRefreshBucketsAreIndependent() {
        val limiter = AuthRateLimiter()
        val key = "203.0.113.10"

        repeat(loginCapacity) { limiter.checkLoginRateLimit(key) }
        assertThrows<AuthRateLimitExceededException> { limiter.checkLoginRateLimit(key) }

        repeat(refreshCapacity) { limiter.checkRefreshRateLimit(key) }
    }

    /* ── 오류 규약 ────────────────────────────────────────────────────── */

    /**
     * 오류 코드는 프런트가 **로그아웃과 구분하는 근거**다(`client.ts` 의
     * `AUTH_RATE_LIMIT_EXCEEDED` 판정). 코드가 바뀌면 상한 초과가 다시 강제 로그아웃이 된다.
     */
    @Test
    @DisplayName("로그인과 갱신은 같은 안정 코드에 서로 다른 사유를 담는다")
    fun bothPathsShareTheStableCodeButExplainThemselves() {
        val limiter = AuthRateLimiter()
        repeat(loginCapacity) { limiter.checkLoginRateLimit("203.0.113.10") }
        repeat(refreshCapacity) { limiter.checkRefreshRateLimit("203.0.113.10") }

        val login = assertThrows<AuthRateLimitExceededException> {
            limiter.checkLoginRateLimit("203.0.113.10")
        }
        val refresh = assertThrows<AuthRateLimitExceededException> {
            limiter.checkRefreshRateLimit("203.0.113.10")
        }

        assertEquals("AUTH_RATE_LIMIT_EXCEEDED", login.code)
        assertEquals("AUTH_RATE_LIMIT_EXCEEDED", refresh.code)
        // 사용자가 무엇이 막혔는지 알 수 있어야 한다.
        assertEquals(true, login.message?.contains("로그인"), login.message)
        assertEquals(true, refresh.message?.contains("갱신"), refresh.message)
        // 잔여 횟수·상한값은 공격자에게 주는 조절 정보다.
        for (leaked in listOf("5", "10", "15")) {
            assertEquals(false, login.message?.contains(leaked), login.message)
            assertEquals(false, refresh.message?.contains(leaked), refresh.message)
        }
    }
}
