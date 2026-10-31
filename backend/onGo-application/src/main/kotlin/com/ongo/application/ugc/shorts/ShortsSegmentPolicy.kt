package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.stage.ClipCandidate
import com.ongo.application.ugc.shorts.stage.TranscriptSegmentMs
import com.ongo.domain.ugc.shorts.PipelineRun

/**
 * 맥락 컷(SEGMENT) 결과를 **결정론적으로** 다듬는 규칙.
 *
 * AI 는 구간과 점수를 제안할 뿐이다. 그대로 저장하면 세 가지가 깨진다.
 * 1. 경계가 문장 중간에 떨어진다 — 모델은 타임코드를 정확히 맞추지 못한다. 말이 잘린 쇼츠는
 *    첫 1초에 이탈한다.
 * 2. 길이 지시를 어긴다 — 프롬프트는 권고일 뿐이라 사용자가 정한 길이를 보장하지 못한다.
 * 3. 같은 장면을 겹쳐 고른다 — 거의 같은 클립 두 개는 렌더·게시 비용만 두 배다.
 *
 * 그래서 경계는 전사 세그먼트(문장) 경계로 맞추고, 길이를 지키지 못하면 **버리고**,
 * 겹치면 점수가 낮은 쪽을 버린 뒤, 점수순으로 원하는 개수만 남긴다.
 *
 * 길이를 맞추려고 문장 중간을 자르지 않는다. 문장 경계로 맞출 수 없는 후보는 버리는 것이
 * 정직하다 — 잘린 클립을 "선택됨"으로 내보내면 사용자가 렌더한 뒤에야 안다.
 */
object ShortsSegmentPolicy {

    const val MIN_CLIP_COUNT = 1
    /**
     * 클립 개수 상한. 점수 이유까지 담은 응답이 AI 출력 한도(호출당 4,096 토큰) 안에
     * 들어와야 한다. 여유분을 더해 요청하므로 실제 요청은 이보다 조금 많다.
     */
    const val MAX_CLIP_COUNT = 10
    /** 개수를 지정하지 않으면 이만큼까지 남긴다. 상한 없이 두면 렌더·게시가 끝없이 늘어난다. */
    const val DEFAULT_CLIP_COUNT = 5

    const val MIN_DURATION_MS = 5_000L
    /** 쇼츠·릴스·틱톡에 공통으로 올릴 수 있는 길이. */
    const val MAX_DURATION_MS = 180_000L
    const val DEFAULT_MIN_DURATION_MS = 15_000L
    const val DEFAULT_MAX_DURATION_MS = 90_000L

    /** 점수가 더 높은 클립과 짧은 쪽 길이의 이만큼 이상 겹치면 같은 장면으로 본다. */
    const val OVERLAP_RATIO = 0.5

    /** 사용자가 정한 선택 조건. null 은 "지정 안 함"이다. */
    data class Options(
        val clipCount: Int? = null,
        val minDurationMs: Long? = null,
        val maxDurationMs: Long? = null,
    ) {
        val effectiveClipCount: Int get() = clipCount ?: DEFAULT_CLIP_COUNT

        /** 한쪽만 정하면 다른 쪽 기본값이 그 값을 넘지 않게 맞춘다. */
        val effectiveMaxMs: Long get() = maxDurationMs ?: maxOf(DEFAULT_MAX_DURATION_MS, minDurationMs ?: 0)
        val effectiveMinMs: Long get() = minDurationMs ?: minOf(DEFAULT_MIN_DURATION_MS, effectiveMaxMs)

        /** 여유분을 둔 AI 요청 개수. 버려지는 후보가 있어도 원하는 개수를 채우기 위해서다. */
        val requestCount: Int get() = effectiveClipCount + REQUEST_SLACK

        companion object {
            const val REQUEST_SLACK = 2

            /** 입력 검증. 범위를 벗어나면 [IllegalArgumentException] (400). */
            fun of(clipCount: Int?, minDurationSec: Int?, maxDurationSec: Int?): Options {
                clipCount?.let {
                    require(it in MIN_CLIP_COUNT..MAX_CLIP_COUNT) {
                        "클립 개수는 $MIN_CLIP_COUNT~${MAX_CLIP_COUNT}개 사이여야 합니다"
                    }
                }
                val minMs = minDurationSec?.let { it * 1000L }
                val maxMs = maxDurationSec?.let { it * 1000L }
                listOfNotNull(minMs, maxMs).forEach {
                    require(it in MIN_DURATION_MS..MAX_DURATION_MS) {
                        "클립 길이는 ${MIN_DURATION_MS / 1000}~${MAX_DURATION_MS / 1000}초 사이여야 합니다"
                    }
                }
                if (minMs != null && maxMs != null) {
                    require(minMs <= maxMs) { "최소 길이가 최대 길이보다 길 수 없습니다" }
                }
                return Options(clipCount, minMs, maxMs)
            }
        }
    }

    /** 다듬은 결과와, 버린 후보가 왜 버려졌는지의 집계(스냅샷에 남긴다). */
    data class Selection(
        val clips: List<ClipCandidate>,
        val droppedForLength: Int,
        val droppedForOverlap: Int,
        val droppedForCount: Int,
    )

    fun select(
        candidates: List<ClipCandidate>,
        segments: List<TranscriptSegmentMs>,
        options: Options,
    ): Selection {
        val ordered = segments.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        val minMs = options.effectiveMinMs
        val maxMs = options.effectiveMaxMs

        var droppedForLength = 0
        val fitted = candidates.mapNotNull { candidate ->
            val snapped = if (ordered.isEmpty()) {
                candidate.startMs to candidate.endMs
            } else {
                snapToSentences(candidate.startMs, candidate.endMs, ordered, minMs, maxMs)
            }
            val (start, end) = snapped ?: run { droppedForLength++; return@mapNotNull null }
            if (end - start !in minMs..maxMs) {
                droppedForLength++
                return@mapNotNull null
            }
            candidate.copy(startMs = start, endMs = end, score = candidate.score?.coerceIn(0, 100))
        }

        // 점수 높은 순. 점수가 없는 후보는 뒤로 — 점수를 지어내지 않는다. 같으면 앞 장면이 먼저.
        val ranked = fitted.sortedWith(
            compareByDescending<ClipCandidate> { it.score ?: -1 }.thenBy { it.startMs },
        )

        var droppedForOverlap = 0
        val distinct = mutableListOf<ClipCandidate>()
        for (candidate in ranked) {
            if (distinct.any { overlapsTooMuch(it, candidate) }) {
                droppedForOverlap++
            } else {
                distinct += candidate
            }
        }

        val limit = options.effectiveClipCount
        return Selection(
            clips = distinct.take(limit),
            droppedForLength = droppedForLength,
            droppedForOverlap = droppedForOverlap,
            droppedForCount = (distinct.size - limit).coerceAtLeast(0),
        )
    }

    /**
     * 시작은 그 시각을 품은 문장의 처음으로, 끝은 그 시각을 품은 문장의 끝으로 맞춘다.
     * 문장 사이 빈틈에 떨어지면 시작은 다음 문장, 끝은 이전 문장 경계로 간다.
     *
     * 그 뒤 길이가 넘치면 끝 문장을 하나씩 빼고, 모자라면 뒤 문장을 하나씩 붙인다.
     * 그래도 범위에 못 들면 null — 문장 중간을 자르지 않는다.
     */
    private fun snapToSentences(
        rawStart: Long,
        rawEnd: Long,
        segments: List<TranscriptSegmentMs>,
        minMs: Long,
        maxMs: Long,
    ): Pair<Long, Long>? {
        val startIdx = segments.indexOfFirst { rawStart < it.endMs }.takeIf { it >= 0 } ?: return null
        var endIdx = segments.indexOfLast { it.startMs < rawEnd }.takeIf { it >= 0 } ?: return null
        if (endIdx < startIdx) return null

        val start = segments[startIdx].startMs
        while (endIdx > startIdx && segments[endIdx].endMs - start > maxMs) endIdx--
        while (endIdx < segments.lastIndex && segments[endIdx].endMs - start < minMs) {
            if (segments[endIdx + 1].endMs - start > maxMs) break
            endIdx++
        }
        return start to segments[endIdx].endMs
    }

    private fun overlapsTooMuch(a: ClipCandidate, b: ClipCandidate): Boolean {
        val overlap = minOf(a.endMs, b.endMs) - maxOf(a.startMs, b.startMs)
        if (overlap <= 0) return false
        val shorter = minOf(a.endMs - a.startMs, b.endMs - b.startMs)
        return overlap >= shorter * OVERLAP_RATIO
    }
}

/** 실행에 저장된 선택 조건. 저장값은 생성 때 이미 검증됐다. */
fun PipelineRun.segmentOptions(): ShortsSegmentPolicy.Options = ShortsSegmentPolicy.Options(
    clipCount = segmentClipCount,
    minDurationMs = segmentMinDurationMs,
    maxDurationMs = segmentMaxDurationMs,
)
