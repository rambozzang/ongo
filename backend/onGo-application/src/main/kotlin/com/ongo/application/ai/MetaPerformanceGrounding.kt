package com.ongo.application.ai

import com.ongo.domain.analytics.AnalyticsRepository
import org.springframework.stereotype.Component

/**
 * 메타 생성에 넣을 **내 채널의 실측 근거**. 최근 [WINDOW_DAYS]일 조회수 순으로 잘 된 영상과 덜 된 영상의
 * 제목·태그를 나란히 보여 주고, 차이에서 배우게 한다.
 *
 * 예전 메타 생성은 채널과 무관한 일반 규칙만 썼다 — 같은 대본이면 누구에게나 같은 제목이 나왔다.
 * 이것이 생성 → 게시 → 분석 → **다음 생성** 루프를 닫는다.
 *
 * 측정된 영상이 [MIN_VIDEOS] 개 미만이면 쓰지 않는다. 영상 몇 개의 순위는 우연이라 "잘 된 패턴" 이라 부를 수 없다.
 */
@Component
class MetaPerformanceGrounding(
    private val analyticsRepository: AnalyticsRepository,
) {

    data class Grounding(
        /** 프롬프트에 넣을 글. 쓰지 않으면 null. */
        val promptBlock: String?,
        val used: Boolean,
        val topCount: Int,
        val bottomCount: Int,
        /** 쓰지 않은 이유(사람이 읽는 문장). */
        val reason: String?,
    )

    fun forUser(userId: Long): Grounding {
        val ranked = runCatching { analyticsRepository.getTopVideos(userId, WINDOW_DAYS, FETCH_LIMIT) }.getOrElse {
            return Grounding(null, false, 0, 0, "성과 데이터를 불러오지 못해 내 채널 성과를 참고하지 않았습니다")
        }.filter { it.title.isNotBlank() }
        if (ranked.size < MIN_VIDEOS) {
            return Grounding(
                null, false, 0, 0,
                "최근 ${WINDOW_DAYS}일 조회수가 집계된 영상이 ${ranked.size}개라(${MIN_VIDEOS}개 이상 필요) 내 채널 성과를 참고하지 않았습니다",
            )
        }
        val top = ranked.take(TOP)
        val bottom = ranked.takeLast(BOTTOM).filter { it !in top }
        fun line(title: String, tags: List<String>) =
            "- ${title.take(TITLE_MAX)}" + (tags.take(TAGS_MAX).takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = "  [태그: ", postfix = "]") ?: "")
        val block = buildString {
            appendLine("내 채널에서 최근 ${WINDOW_DAYS}일 조회수가 높았던 영상(실측 순위):")
            top.forEach { appendLine(line(it.title, it.tags)) }
            if (bottom.isNotEmpty()) {
                appendLine("같은 기간 조회수가 낮았던 영상:")
                bottom.forEach { appendLine(line(it.title, it.tags)) }
            }
            append("위 두 묶음의 제목 구조·길이·키워드·말투 차이를 참고해 이 채널 시청자에게 맞는 제목을 만들되, 위 제목을 그대로 베끼지 마세요.")
        }
        return Grounding(InputSanitizer.sanitize(block), true, top.size, bottom.size, null)
    }

    companion object {
        const val WINDOW_DAYS = 90
        const val MIN_VIDEOS = 6
        private const val FETCH_LIMIT = 30
        private const val TOP = 5
        private const val BOTTOM = 3
        private const val TITLE_MAX = 100
        private const val TAGS_MAX = 8
    }
}
