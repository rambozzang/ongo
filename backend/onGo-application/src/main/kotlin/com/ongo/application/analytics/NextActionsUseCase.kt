package com.ongo.application.analytics

import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import org.springframework.stereotype.Service

/**
 * "다음에 할 일" — **실측 데이터에서만** 나오는 추천. AI 가 지어낸 조언을 섞지 않는다.
 *
 * 지금은 하나다: 최근 [WINDOW_DAYS]일 조회수가 가장 높은데 아직 쇼츠로 만들지 않은 영상.
 * 잘 된 롱폼을 쇼츠로 잘라 다시 내는 것이 크리에이터가 가장 적은 노력으로 도달을 늘리는 길이고,
 * 우리 쇼츠 파이프라인으로 바로 이어진다. 근거가 없으면 아무것도 권하지 않는다(빈 목록).
 */
@Service
class NextActionsUseCase(
    private val analyticsRepository: AnalyticsRepository,
    private val pipelineRunRepository: PipelineRunRepository,
) {

    data class NextAction(
        val type: String,
        val videoId: Long,
        val title: String,
        /** 왜 이 추천인지(사람이 읽는 문장, 실측 근거). */
        val reason: String,
    )

    fun nextActions(userId: Long): List<NextAction> {
        val ranked = analyticsRepository.getTopVideos(userId, WINDOW_DAYS, CANDIDATES).filter { it.id != null }
        if (ranked.isEmpty()) return emptyList()
        val done = pipelineRunRepository.findSourceVideoIdsWithRuns(userId, ranked.mapNotNull { it.id })
        return ranked.withIndex()
            .firstOrNull { (_, v) -> v.id !in done }
            ?.let { (rank, v) ->
                listOf(
                    NextAction(
                        type = TYPE_MAKE_SHORTS,
                        videoId = v.id!!,
                        title = v.title,
                        reason = "최근 ${WINDOW_DAYS}일 조회수 ${rank + 1}위 영상이고 아직 쇼츠로 만들지 않았습니다",
                    ),
                )
            }
            .orEmpty()
    }

    companion object {
        const val TYPE_MAKE_SHORTS = "MAKE_SHORTS"
        const val WINDOW_DAYS = 14
        private const val CANDIDATES = 10
    }
}
