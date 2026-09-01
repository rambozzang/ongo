package com.ongo.application.ai

import com.ongo.domain.analytics.MetricChange
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 성과 프롬프트에 **없는 수치가 들어가지 않는지** 고정한다.
 *
 * `GenerateReportUseCase` 와 `StrategyCoachUseCase` 는 KPI 의 증감률을 프롬프트 문자열에
 * 끼워 넣는다. 그 값은 이전 기간 데이터가 없으면 `null` 이다. 예전 코드는
 * `String.format("%.1f", kpi.totalViewsChange)` 를 썼는데, **Java Formatter 는 null 인자를
 * 문자열 `"null"` 로 출력한다.** 모델은 그것을 수치로 읽고 없는 추세를 설명한다.
 *
 * 템플릿 쪽도 함께 고정한다. 예전 템플릿은 `{viewsChange}%` 처럼 `%` 를 밖에 붙여 두어,
 * 비교 불가 문구를 넣으면 `비교 불가(...)%` 라는 문장이 만들어졌다. 단위는 값이 들고 온다.
 */
class PerformancePromptChangeTest {

    private val templates = listOf(
        "PERFORMANCE_REPORT_USER" to PromptTemplates.PERFORMANCE_REPORT_USER,
        "STRATEGY_COACH_USER" to PromptTemplates.STRATEGY_COACH_USER,
    )

    /**
     * 템플릿이 `%` 를 밖에 붙이고 있으면 비교 불가 문구가 `비교 불가(...)%` 가 된다.
     * 단위는 [MetricChange.describePercent] 가 값과 함께 만든다.
     */
    @Test
    @DisplayName("템플릿이 증감률 자리 뒤에 % 를 붙이지 않는다 — 단위는 값이 들고 온다")
    fun templatesDoNotAppendPercentAfterPlaceholder() {
        templates.forEach { (name, template) ->
            assertFalse(
                template.contains("{viewsChange}%"),
                "$name 이 {viewsChange} 뒤에 % 를 붙인다 — 비교 불가일 때 '비교 불가(...)%' 가 된다",
            )
            assertFalse(
                template.contains("{likesChange}%"),
                "$name 이 {likesChange} 뒤에 % 를 붙인다",
            )
        }
    }

    /** 자리표시자는 그대로 있어야 한다. 없으면 치환이 조용히 아무것도 하지 않는다. */
    @Test
    @DisplayName("증감률 자리표시자는 그대로 유지한다")
    fun placeholdersStillExist() {
        templates.forEach { (name, template) ->
            assertTrue(template.contains("{viewsChange}"), "$name 에 {viewsChange} 가 없다")
            assertTrue(template.contains("{likesChange}"), "$name 에 {likesChange} 가 없다")
        }
    }

    /**
     * **이 테스트가 프롬프트 오염의 마지막 방어선이다.**
     *
     * 사용처와 같은 방식으로 치환했을 때, 비교 불가인 값이 `"null"` 이나 숫자로 바뀌지
     * 않는지 본다.
     */
    @Test
    @DisplayName("비교 불가 KPI 를 치환해도 프롬프트에 null 이나 지어낸 숫자가 남지 않는다")
    fun unavailableChangeDoesNotPolluteThePrompt() {
        templates.forEach { (name, template) ->
            val rendered = template
                .replace("{days}", "30")
                .replace("{totalViews}", "50000")
                .replace("{viewsChange}", MetricChange.describePercent(null))
                .replace("{totalLikes}", "1200")
                .replace("{likesChange}", MetricChange.describePercent(null))
                .replace("{totalComments}", "34")
                .replace("{subscriberChange}", "12")
                .replace("{topVideos}", "1. 테스트 영상")

            assertFalse(rendered.contains("null"), "$name 프롬프트에 문자열 'null' 이 남았다:\n$rendered")
            assertTrue(
                rendered.contains(MetricChange.UNAVAILABLE_TEXT),
                "$name 프롬프트가 비교 불가를 문장으로 알리지 않는다:\n$rendered",
            )
            // 예전 하드코딩 값이 되살아나면 여기서 걸린다.
            assertFalse(
                rendered.contains("변화율: 100.0"),
                "$name 프롬프트가 비교 불가를 100% 로 채웠다:\n$rendered",
            )
        }
    }

    /** 측정된 값은 단위까지 포함해 그대로 들어가야 한다. */
    @Test
    @DisplayName("측정된 증감률은 단위와 함께 프롬프트에 들어간다")
    fun measuredChangeKeepsItsUnit() {
        val rendered = PromptTemplates.PERFORMANCE_REPORT_USER
            .replace("{viewsChange}", MetricChange.describePercent(-12.34))

        assertTrue(rendered.contains("-12.3%"), "측정값이 단위 없이 들어갔다:\n$rendered")
    }
}
