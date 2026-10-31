package com.ongo.application.videoseo

import com.ongo.common.enums.Platform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SEO 규칙 점수. 같은 입력이면 같은 점수가 나오고, 점수의 모든 감점에 실제 값이 붙는지를 고정한다.
 */
class SeoRubricTest {

    private val goodDescription =
        "월급 관리 첫 단계로 통장 쪼개기를 시작하는 방법을 정리했습니다. " +
            "생활비·저축·비상금 통장을 나누는 기준과 자동이체 설정 순서를 실제 화면으로 보여 드립니다. " +
            "재테크를 처음 시작하는 분께 추천합니다."

    private fun evaluate(
        title: String = "월급 관리 첫 단계 통장 쪼개기",
        description: String? = goodDescription,
        tags: List<String> = listOf("재테크", "통장쪼개기", "월급"),
        category: String? = "금융",
        platforms: List<Platform> = listOf(Platform.YOUTUBE),
    ) = SeoRubric.evaluate(title, description, tags, category, platforms)

    @Test
    fun `모든 항목을 통과하면 100점이고 네 영역의 합이다`() {
        val result = evaluate()

        assertEquals(100, result.overallScore)
        assertEquals(listOf(25, 25, 25, 25), result.categories.map { it.score })
        assertTrue(result.failedChecks.isEmpty())
    }

    @Test
    fun `같은 입력이면 같은 점수 — AI 처럼 실행마다 달라지지 않는다`() {
        assertEquals(evaluate(tags = listOf("x")).overallScore, evaluate(tags = listOf("x")).overallScore)
    }

    @Test
    fun `제목이 게시 대상 중 가장 엄격한 한도를 넘으면 감점하고 실제 글자 수를 보인다`() {
        val title = "통장쪼개기".repeat(25) // 125자 — 유튜브 한도 100자 초과
        val result = evaluate(title = title, platforms = listOf(Platform.TIKTOK, Platform.YOUTUBE))

        val check = result.failedChecks.single { it.key == "titleWithinLimit" }
        assertEquals("125자 / 한도 100자", check.detail)
    }

    @Test
    fun `태그가 제목에 하나도 없으면 감점한다`() {
        val result = evaluate(title = "오늘 있었던 일 이야기")

        assertTrue(result.failedChecks.any { it.key == "titleHasTag" })
    }

    @Test
    fun `설명이 비면 설명 영역은 0점`() {
        val description = evaluate(description = null).categories.single { it.key == "description" }

        assertEquals(0, description.score)
        assertEquals("설명이 비어 있습니다", description.checks.first().detail)
    }

    @Test
    fun `태그 1~2개는 절반, 없으면 0, 중복은 대소문자·공백을 무시하고 센다`() {
        assertEquals(5, evaluate(tags = listOf("재테크")).categories.single { it.key == "tags" }.checks.single { it.key == "tagCount" }.points)
        assertEquals(0, evaluate(tags = emptyList()).categories.single { it.key == "tags" }.checks.single { it.key == "tagCount" }.points)

        val dup = evaluate(tags = listOf("Money", "money", "재테크", "#재테크")).failedChecks.single { it.key == "tagNoDuplicate" }
        assertEquals("중복 2개", dup.detail)
    }

    @Test
    fun `게시 대상이 없으면 제목 한도는 보지 않았다고 말하고 그 항목은 감점한다`() {
        val result = evaluate(platforms = emptyList())

        val title = result.categories.single { it.key == "title" }.checks.single { it.key == "titleWithinLimit" }
        assertTrue(title.passed)
        assertTrue(title.detail.contains("게시 대상이 없어"))
        assertFalse(result.categories.single { it.key == "general" }.checks.single { it.key == "hasTarget" }.passed)
    }

    @Test
    fun `태그 개수 한도는 게시 대상 한도표를 따른다`() {
        val tags = (1..31).map { "태그$it" }
        val tiktok = evaluate(tags = tags, platforms = listOf(Platform.TIKTOK))

        assertEquals("31개 / 한도 30개", tiktok.failedChecks.single { it.key == "tagWithinLimit" }.detail)
    }
}
