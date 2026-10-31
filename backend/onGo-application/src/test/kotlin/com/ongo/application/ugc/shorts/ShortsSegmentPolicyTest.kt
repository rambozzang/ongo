package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.stage.ClipCandidate
import com.ongo.application.ugc.shorts.stage.TranscriptSegmentMs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 맥락 컷 결과 다듬기 규칙. AI 제안을 그대로 저장하면 문장 중간에서 끊긴 쇼츠, 사용자가 정한
 * 길이를 어긴 쇼츠, 같은 장면 두 개가 나온다 — 각각을 숫자로 고정한다.
 */
class ShortsSegmentPolicyTest {

    /** 10초짜리 문장 12개(0~120초)가 빈틈 없이 이어진 전사. */
    private val sentences = (0 until 12).map { TranscriptSegmentMs(it * 10_000L, (it + 1) * 10_000L, "문장${it + 1}") }

    private fun candidate(startMs: Long, endMs: Long, score: Int? = 50, title: String = "t") =
        ClipCandidate(title = title, caption = null, startMs = startMs, endMs = endMs, score = score, scoreReason = "이유")

    private fun options(count: Int? = null, minSec: Int? = 15, maxSec: Int? = 60) =
        ShortsSegmentPolicy.Options.of(count, minSec, maxSec)

    @Nested
    inner class 문장_경계 {

        @Test
        fun `문장 중간의 시작과 끝은 그 문장의 처음과 끝으로 맞춘다`() {
            val result = ShortsSegmentPolicy.select(listOf(candidate(13_400, 38_700)), sentences, options())

            val clip = result.clips.single()
            assertEquals(10_000, clip.startMs)
            assertEquals(40_000, clip.endMs)
        }

        @Test
        fun `문장 사이 빈틈에 떨어진 시작은 다음 문장, 끝은 이전 문장으로 간다`() {
            val gapped = listOf(
                TranscriptSegmentMs(0, 8_000, "a"),
                TranscriptSegmentMs(10_000, 28_000, "b"),
                TranscriptSegmentMs(30_000, 48_000, "c"),
            )
            val result = ShortsSegmentPolicy.select(listOf(candidate(9_000, 29_500)), gapped, options(minSec = 10))

            val clip = result.clips.single()
            assertEquals(10_000, clip.startMs)
            assertEquals(28_000, clip.endMs)
        }

        @Test
        fun `최대 길이를 넘으면 끝 문장을 하나씩 뺀다 — 문장 중간을 자르지 않는다`() {
            // 0~85초 요청, 최대 60초 → 0~60초(문장 6개)
            val result = ShortsSegmentPolicy.select(listOf(candidate(0, 85_000)), sentences, options(maxSec = 60))

            assertEquals(0, result.clips.single().startMs)
            assertEquals(60_000, result.clips.single().endMs)
        }

        @Test
        fun `최소 길이에 모자라면 뒤 문장을 붙인다`() {
            val result = ShortsSegmentPolicy.select(listOf(candidate(20_000, 28_000)), sentences, options(minSec = 25))

            assertEquals(20_000, result.clips.single().startMs)
            assertEquals(50_000, result.clips.single().endMs)
        }

        @Test
        fun `문장 경계로 길이 범위를 맞출 수 없으면 버린다`() {
            // 문장 하나가 40초인데 최대 30초 — 맞추려면 문장 중간을 잘라야 한다.
            val long = listOf(TranscriptSegmentMs(0, 40_000, "긴 문장"))
            val result = ShortsSegmentPolicy.select(listOf(candidate(0, 40_000)), long, options(minSec = 10, maxSec = 30))

            assertTrue(result.clips.isEmpty())
            assertEquals(1, result.droppedForLength)
        }

        @Test
        fun `영상 끝이라 붙일 문장이 없으면 최소 길이 미달로 버린다`() {
            val result = ShortsSegmentPolicy.select(listOf(candidate(110_000, 120_000)), sentences, options(minSec = 30))

            assertTrue(result.clips.isEmpty())
            assertEquals(1, result.droppedForLength)
        }

        @Test
        fun `전사 범위 밖의 후보는 버린다`() {
            val result = ShortsSegmentPolicy.select(listOf(candidate(130_000, 160_000)), sentences, options())

            assertTrue(result.clips.isEmpty())
        }

        @Test
        fun `세그먼트가 없으면 경계는 그대로 두고 길이만 검사한다`() {
            val result = ShortsSegmentPolicy.select(
                listOf(candidate(1_234, 31_234), candidate(0, 5_000)),
                emptyList(),
                options(),
            )

            assertEquals(listOf(1_234L to 31_234L), result.clips.map { it.startMs to it.endMs })
            assertEquals(1, result.droppedForLength)
        }
    }

    @Nested
    inner class 점수_정렬과_개수 {

        @Test
        fun `점수 높은 순으로 정렬하고 점수가 없는 후보는 맨 뒤로 보낸다`() {
            val result = ShortsSegmentPolicy.select(
                listOf(
                    candidate(0, 20_000, score = 40, title = "중"),
                    candidate(30_000, 50_000, score = null, title = "없음"),
                    candidate(60_000, 80_000, score = 90, title = "상"),
                ),
                sentences,
                options(),
            )

            assertEquals(listOf("상", "중", "없음"), result.clips.map { it.title })
            assertNull(result.clips.last().score) // 점수를 지어내지 않는다
        }

        @Test
        fun `범위를 벗어난 점수는 0~100으로 묶는다`() {
            val result = ShortsSegmentPolicy.select(
                listOf(candidate(0, 20_000, score = 140), candidate(30_000, 50_000, score = -5)),
                sentences,
                options(),
            )

            assertEquals(listOf(100, 0), result.clips.map { it.score })
        }

        @Test
        fun `정한 개수만 남기고 나머지는 개수 초과로 센다`() {
            val many = (0 until 6).map { candidate(it * 20_000L, it * 20_000L + 20_000, score = 10 * it) }
            val result = ShortsSegmentPolicy.select(many, sentences, options(count = 2))

            assertEquals(listOf(50, 40), result.clips.map { it.score })
            assertEquals(4, result.droppedForCount)
        }

        @Test
        fun `개수를 정하지 않으면 기본 개수까지만 남긴다`() {
            val many = (0 until 6).map { candidate(it * 20_000L, it * 20_000L + 20_000) }
            val result = ShortsSegmentPolicy.select(many, sentences, options(count = null))

            assertEquals(ShortsSegmentPolicy.DEFAULT_CLIP_COUNT, result.clips.size)
        }
    }

    @Nested
    inner class 중복 {

        @Test
        fun `점수 높은 클립과 절반 이상 겹치면 낮은 쪽을 버린다`() {
            val result = ShortsSegmentPolicy.select(
                listOf(
                    candidate(0, 40_000, score = 70, title = "낮음"),
                    candidate(10_000, 40_000, score = 90, title = "높음"),
                ),
                sentences,
                options(),
            )

            assertEquals(listOf("높음"), result.clips.map { it.title })
            assertEquals(1, result.droppedForOverlap)
        }

        @Test
        fun `조금 겹치는 것은 다른 장면으로 둔다`() {
            // 0~40초와 30~60초: 10초 겹침, 짧은 쪽 30초의 1/3
            val result = ShortsSegmentPolicy.select(
                listOf(candidate(0, 40_000, score = 90), candidate(30_000, 60_000, score = 80)),
                sentences,
                options(),
            )

            assertEquals(2, result.clips.size)
        }
    }

    @Nested
    inner class 조건_검증 {

        @Test
        fun `개수는 1~10개`() {
            assertFailsWith<IllegalArgumentException> { ShortsSegmentPolicy.Options.of(0, null, null) }
            assertFailsWith<IllegalArgumentException> { ShortsSegmentPolicy.Options.of(11, null, null) }
            assertEquals(10, ShortsSegmentPolicy.Options.of(10, null, null).effectiveClipCount)
        }

        @Test
        fun `길이는 5~180초이고 최소가 최대보다 길 수 없다`() {
            assertFailsWith<IllegalArgumentException> { ShortsSegmentPolicy.Options.of(null, 4, null) }
            assertFailsWith<IllegalArgumentException> { ShortsSegmentPolicy.Options.of(null, null, 181) }
            assertFailsWith<IllegalArgumentException> { ShortsSegmentPolicy.Options.of(null, 60, 30) }
        }

        @Test
        fun `한쪽만 정하면 다른 쪽 기본값이 그 값과 모순되지 않는다`() {
            val onlyMin = ShortsSegmentPolicy.Options.of(null, 120, null)
            assertEquals(120_000, onlyMin.effectiveMinMs)
            assertEquals(120_000, onlyMin.effectiveMaxMs) // 기본 최대 90초보다 길게 맞춘다

            val onlyMax = ShortsSegmentPolicy.Options.of(null, null, 10)
            assertEquals(10_000, onlyMax.effectiveMinMs) // 기본 최소 15초보다 짧게 맞춘다
            assertEquals(10_000, onlyMax.effectiveMaxMs)
        }

        @Test
        fun `AI 에게는 여유분을 더해 요청하되 출력 한도를 넘지 않는다`() {
            assertEquals(12, ShortsSegmentPolicy.Options.of(10, null, null).requestCount)
            assertEquals(ShortsSegmentPolicy.DEFAULT_CLIP_COUNT + 2, ShortsSegmentPolicy.Options().requestCount)
        }
    }
}
