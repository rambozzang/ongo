package com.ongo.application.ugc.shorts

import com.ongo.application.video.PlatformCaptionRules
import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 플랫폼별 게시 메타. 여기서 맞춘 값은 게시 검증(`PublishVideoUseCase`)이 쓰는 한도와 같은 표로
 * 다시 검사해, "메타는 통과했는데 게시에서 거절"이 없음을 고정한다.
 */
class ShortsPlatformMetaTest {

    /** 게시 흐름과 같은 기준으로 통과하는지. */
    private fun assertPublishable(platform: Platform, entry: ShortsPlatformMeta.Entry) {
        val capability = PlatformUploadCapabilities.get(platform)!!
        assertTrue(entry.title.length <= capability.maxTitleLength, "$platform 제목 길이")
        if (capability.maxDescriptionLength > 0) assertTrue(entry.description.length <= capability.maxDescriptionLength)
        assertTrue(entry.hashtags.size <= capability.maxTagCount)
        capability.maxCaptionLength?.let { limit ->
            val caption = PlatformCaptionRules.compose(platform, entry.title, entry.description, entry.hashtags).orEmpty()
            assertTrue(caption.length <= limit, "$platform 게시글 ${caption.length}자 > $limit")
        }
    }

    @Test
    fun `맥락 컷 직후 기본 대상 셋의 메타를 만든다`() {
        val entries = ShortsPlatformMeta.initial("월급 관리 첫 단계", "통장 쪼개기부터 시작하세요", listOf("재테크", "#월급", "통장 쪼개기"))

        assertEquals(listOf("YOUTUBE", "TIKTOK", "INSTAGRAM"), entries.map { it.platform })
        entries.forEach {
            assertEquals("월급 관리 첫 단계", it.title)
            assertEquals(listOf("재테크", "월급", "통장쪼개기"), it.hashtags) // # 제거, 띄어쓰기 제거
        }
    }

    @Test
    fun `해시태그는 중복·너무 긴 것·특수문자를 빼고 5개까지`() {
        val tags = ShortsPlatformMeta.normalizeHashtags(
            listOf("A", "a", "", " # ", "가".repeat(31), "쉼표,태그", "1", "2", "3", "4", "5"),
        )
        assertEquals(listOf("A", "1", "2", "3", "4", "5"), tags)
        assertEquals(5, ShortsPlatformMeta.fit(Platform.TIKTOK, "t", "", tags).hashtags.size)
    }

    @Test
    fun `긴 제목·설명은 플랫폼 한도에 맞춰 줄이고 게시 검증을 통과한다`() {
        val longTitle = "가".repeat(3_000)
        val longDesc = "나".repeat(6_000)
        val tags = listOf("하나", "둘", "셋")

        Platform.entries.filter { PlatformUploadCapabilities.get(it) != null }.forEach { platform ->
            assertPublishable(platform, ShortsPlatformMeta.fit(platform, longTitle, longDesc, tags))
        }
        val youtube = ShortsPlatformMeta.fit(Platform.YOUTUBE, longTitle, longDesc, tags)
        assertEquals(100, youtube.title.length)
        assertEquals(3, youtube.hashtags.size) // 설명을 줄여도 태그는 지킨다
    }

    @Test
    fun `게시글 한도를 넘으면 설명을 먼저 줄이고 해시태그는 남긴다`() {
        val entry = ShortsPlatformMeta.fit(Platform.INSTAGRAM, "제목", "설".repeat(3_000), listOf("태그1", "태그2"))

        assertEquals(listOf("태그1", "태그2"), entry.hashtags)
        val caption = PlatformCaptionRules.compose(Platform.INSTAGRAM, entry.title, entry.description, entry.hashtags)!!
        assertTrue(caption.length <= 2_200)
        assertTrue(caption.endsWith("#태그1 #태그2"))
    }

    @Test
    fun `게시 대상 메타가 저장돼 있으면 그것을 쓴다`() {
        val json = ShortsPlatformMeta.toJson(
            listOf(ShortsPlatformMeta.Entry("TIKTOK", "틱톡 제목", "틱톡 설명", listOf("틱톡"))),
        )

        val entry = ShortsPlatformMeta.forTarget(Platform.TIKTOK, json, "클립 제목", "클립 설명", "원본", listOf("원본태그"))

        assertEquals(ShortsPlatformMeta.Entry("TIKTOK", "틱톡 제목", "틱톡 설명", listOf("틱톡")), entry)
    }

    @Test
    fun `그 플랫폼 메타가 없으면 클립 제목·설명에 다른 플랫폼 해시태그를 쓴다`() {
        val json = ShortsPlatformMeta.toJson(ShortsPlatformMeta.initial("제목", "설명", listOf("공통")))

        val entry = ShortsPlatformMeta.forTarget(Platform.THREADS, json, "클립 제목", "클립 설명", "원본", listOf("원본태그"))

        assertEquals("THREADS", entry.platform)
        assertEquals("클립 제목", entry.title)
        assertEquals(listOf("공통"), entry.hashtags)
    }

    @Test
    fun `메타가 전혀 없으면 원본 영상 태그를 쓰고, 그것도 없으면 빈 목록이다 — 지어내지 않는다`() {
        val withSource = ShortsPlatformMeta.forTarget(Platform.YOUTUBE, null, null, null, "원본 제목", listOf("원본태그"))
        assertEquals("원본 제목", withSource.title)
        assertEquals(listOf("원본태그"), withSource.hashtags)

        val nothing = ShortsPlatformMeta.forTarget(Platform.YOUTUBE, null, "", null, "원본 제목", emptyList())
        assertEquals("원본 제목", nothing.title)
        assertTrue(nothing.hashtags.isEmpty())
    }

    @Test
    fun `깨진 저장값은 없는 것으로 읽는다`() {
        assertTrue(ShortsPlatformMeta.parse("{not json").isEmpty())
        assertTrue(ShortsPlatformMeta.parse("""[{"title":"플랫폼 없음"}]""").isEmpty())
    }

    @Test
    fun `편집은 한도를 넘으면 자르지 않고 거절한다`() {
        val ok = ShortsPlatformMeta.validateEdit(listOf(ShortsPlatformMeta.Entry("youtube", " 제목 ", "설명", listOf("#a", "b"))))
        assertEquals(ShortsPlatformMeta.Entry("YOUTUBE", "제목", "설명", listOf("a", "b")), ok.single())

        val cases = listOf(
            ShortsPlatformMeta.Entry("YOUTUBE", "가".repeat(101), "", emptyList()),
            ShortsPlatformMeta.Entry("TIKTOK", "제목", "가".repeat(2_000), emptyList()), // 제목+설명이 2000자 초과
            ShortsPlatformMeta.Entry("INSTAGRAM", "제목", "", (1..6).map { "t$it" }),
            ShortsPlatformMeta.Entry("NOT_A_PLATFORM", "제목", "", emptyList()),
        )
        cases.forEach { entry ->
            val ex = assertFailsWith<BusinessException> { ShortsPlatformMeta.validateEdit(listOf(entry)) }
            assertEquals("SHORTS_CLIP_EDIT_INVALID", ex.code)
        }
        assertFailsWith<BusinessException> {
            ShortsPlatformMeta.validateEdit(
                listOf(ShortsPlatformMeta.Entry("TIKTOK", "a", "", emptyList()), ShortsPlatformMeta.Entry("TIKTOK", "b", "", emptyList())),
            )
        }
    }
}
