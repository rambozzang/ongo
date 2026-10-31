package com.ongo.application.ai

import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.common.enums.Platform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 메타 생성 규칙이 **게시 검증과 같은 한도표**를 따르고, 생성 직후 한도 안으로 맞춰지는지 고정한다. */
class MetaPlatformRulesTest {

    @Test
    fun `프롬프트 규칙은 한도표의 값을 쓰고 TikTok 을 150자로 적지 않는다`() {
        val text = MetaPlatformRules.rulesText(listOf(Platform.YOUTUBE, Platform.TIKTOK, Platform.THREADS))

        assertTrue(text.contains("- YOUTUBE: 제목 100자 이내, 설명 5000자 이내"), text)
        assertTrue(text.contains("- TIKTOK: 제목 ${PlatformUploadCapabilities.get(Platform.TIKTOK)!!.maxTitleLength}자"), text)
        assertFalse(text.contains("150자"), text)
        assertTrue(text.contains("- THREADS:") && text.contains("해시태그 1개 이내"), text)
        assertFalse(text.contains("INSTAGRAM"), "대상이 아닌 플랫폼 규칙은 넣지 않는다: $text")
    }

    @Test
    fun `생성 결과를 한도 안으로 맞추고 해시태그를 정리한다`() {
        val longTitle = "가".repeat(60) + " " + "나".repeat(60)
        val raw = MetaGenerationResult(
            listOf(
                MetaGenerationResult.PlatformMetaResult(
                    platform = "youtube",
                    titleCandidates = listOf(longTitle, " 짧은 제목 ", "짧은 제목"),
                    description = "설명",
                    hashtags = listOf("#브이로그", "브이로그", "일상 기록", "", "#a#b", "x".repeat(40)) + (1..20).map { "태그$it" },
                ),
                MetaGenerationResult.PlatformMetaResult("INSTAGRAM", listOf("대상 아님"), "", emptyList()),
            ),
        )

        val fitted = MetaPlatformRules.fit(raw, listOf(Platform.YOUTUBE)).platforms.single()

        assertEquals("YOUTUBE", fitted.platform)
        assertEquals("가".repeat(60), fitted.titleCandidates[0], "어절 경계에서 잘라야 한다")
        assertEquals(listOf("가".repeat(60), "짧은 제목"), fitted.titleCandidates)
        assertEquals("브이로그", fitted.hashtags[0])
        assertEquals("일상기록", fitted.hashtags[1])
        assertTrue(fitted.hashtags.none { it.startsWith("#") || it.length > 30 || it.contains("#") })
        assertEquals(MetaPlatformRules.hashtagCount(Platform.YOUTUBE), fitted.hashtags.size)
    }

    @Test
    fun `모든 게시 가능 플랫폼의 맞춘 제목·설명이 한도표를 넘지 않는다`() {
        Platform.entries.filter { PlatformUploadCapabilities.get(it) != null }.forEach { p ->
            val raw = MetaGenerationResult(
                listOf(MetaGenerationResult.PlatformMetaResult(p.name, listOf("제".repeat(6_000)), "설".repeat(6_000), (1..50).map { "t$it" })),
            )
            val fitted = MetaPlatformRules.fit(raw, listOf(p)).platforms.single()
            assertTrue(fitted.titleCandidates.all { it.length <= MetaPlatformRules.titleLimit(p) }, "$p 제목")
            assertTrue(fitted.description.length <= MetaPlatformRules.descriptionLimit(p), "$p 설명")
            assertTrue(fitted.hashtags.size <= MetaPlatformRules.hashtagCount(p), "$p 태그")
        }
    }
}
