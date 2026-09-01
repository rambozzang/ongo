package com.ongo.application.video

import com.ongo.application.video.dto.AiOptimizationRequest
import com.ongo.application.video.dto.OptimizationCheckRequest
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 크로스 플랫폼 최적화의 **과금 경계와 fail-closed 계약**을 고정한다.
 *
 * ## 무엇이 문제였나
 *
 * `optimizeContent` 는 요금이 정해지지 않은 채 LLM 을 불렀다. 생성자에 `CreditService`
 * 도 `AiRateLimiter` 도 없었고, `platforms` 가 비면 `Platform.entries` 전체로 확장돼
 * **요청 한 건에 순차 13 회** 모델을 태웠다. `VIDEO_CREATE` 권한만 있으면 누구나
 * 무제한으로 0 크레딧에 그 비용을 발생시킬 수 있었다.
 *
 * 부분 실패도 성공처럼 보였다. 플랫폼별 `catch` 가 **원본 title/description/tags 를
 * 그대로 담아** 돌려줘서, 호출자에게는 "최적화됨"과 "실패해서 원본 그대로"가 같은
 * 성공 응답이었다.
 *
 * ## 왜 요금을 붙여 살리지 않았나
 *
 * 기존 `AiFeature` 중 값을 빌려올 만큼 동등한 것이 없다. `META_REWRITE(3)` 만이 작업이
 * 같은데, 그쪽은 전 플랫폼을 **한 프롬프트, LLM 1 회**로 처리한다. 이 구조에 3 크레딧을
 * 붙이면 1 회분 가격으로 13 회를 태운다. 없는 가격을 지어내는 대신 막았다.
 */
class CrossPlatformOptimizationUseCaseTest {

    private val useCase = CrossPlatformOptimizationUseCase()

    // ── AI 경로는 fail-closed ────────────────────────────────────────────────

    @Test
    @DisplayName("AI 최적화는 비활성이며 조용히 성공하지 않는다")
    fun aiOptimizationFailsClosed() {
        val e = assertFailsWith<BusinessException> {
            useCase.optimizeContent(7L, AiOptimizationRequest("원본", platforms = listOf(Platform.YOUTUBE)))
        }

        assertEquals("FEATURE_NOT_AVAILABLE", e.code)
        // 대체 수단을 알려주지 않으면 사용자는 기능이 사라진 줄만 안다.
        assertTrue(e.message!!.contains("리라이트"), "대체 기능 안내가 없다: ${e.message}")
    }

    /**
     * **이것이 원래의 손실 지점이다.** `platforms` 가 비면 13 개 플랫폼으로 확장돼
     * 요청 한 건에 LLM 13 회가 나갔다. 빈 목록이 조용한 성공으로 되살아나면 안 된다.
     */
    @Test
    @DisplayName("플랫폼 목록이 비어도 전체 플랫폼으로 확장해 호출하지 않는다")
    fun emptyPlatformsDoesNotFanOut() {
        val e = assertFailsWith<BusinessException> {
            useCase.optimizeContent(7L, AiOptimizationRequest("원본"))
        }

        assertEquals("FEATURE_NOT_AVAILABLE", e.code)
    }

    /**
     * 유스케이스가 LLM 을 **부를 수단 자체를 갖고 있지 않아야** 한다. 생성자에
     * `ChatClientResolver` 가 남아 있으면 다음 사람이 가격 결정 없이 다시 배선할 수 있다.
     * 인자 없는 생성자로 만들어지는지가 그 증거다.
     */
    @Test
    @DisplayName("유스케이스에 AI 클라이언트 의존이 남아 있지 않다")
    fun noAiClientDependencyRemains() {
        val constructors = CrossPlatformOptimizationUseCase::class.java.constructors

        assertEquals(1, constructors.size, "생성자가 여러 개다: ${constructors.toList()}")
        assertEquals(
            0,
            constructors.single().parameterCount,
            "AI 의존이 남아 있다: ${constructors.single().parameterTypes.toList()}",
        )
    }

    // ── 무료 규칙 기반 검사는 그대로 살아 있다 ───────────────────────────────

    /**
     * [CrossPlatformOptimizationUseCase.checkOptimization] 은 LLM 을 부르지 않는 규칙 기반
     * 점수 계산이고 **프론트가 실제로 쓴다**(`/videos/optimization-check`). AI 경로를 막으면서
     * 이쪽까지 죽이면 실제 사용자 기능이 사라진다.
     */
    @Test
    @DisplayName("규칙 기반 최적화 검사는 계속 동작한다")
    fun ruleBasedCheckStillWorks() {
        val response = useCase.checkOptimization(
            OptimizationCheckRequest(
                title = "짧음",
                description = null,
                tags = emptyList(),
                platforms = listOf(Platform.YOUTUBE, Platform.TIKTOK),
            ),
        )

        assertEquals(2, response.results.size)
        // 개선점이 있는 입력이므로 만점이 나오면 검사가 실제로 돌지 않은 것이다.
        assertTrue(response.results.all { it.score < 100 }, "검사가 제안을 하나도 만들지 않았다")
    }

    @Test
    @DisplayName("규칙 기반 검사는 플랫폼 목록이 비면 전체 플랫폼을 평가한다")
    fun ruleBasedCheckDefaultsToAllPlatforms() {
        val response = useCase.checkOptimization(OptimizationCheckRequest(title = "제목"))

        // LLM 을 부르지 않으므로 전체 확장이 비용 문제가 되지 않는다.
        assertEquals(Platform.entries.size, response.results.size)
    }
}
