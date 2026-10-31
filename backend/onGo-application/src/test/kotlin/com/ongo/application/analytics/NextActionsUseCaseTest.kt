package com.ongo.application.analytics

import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.video.Video
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "다음에 할 일" 은 실측 순위에서만 나오고, 이미 쇼츠로 만든 영상은 건너뛰며, 근거가 없으면 비어 있다. */
class NextActionsUseCaseTest {

    private val analytics = mockk<AnalyticsRepository>()
    private val runs = mockk<PipelineRunRepository>()
    private val useCase = NextActionsUseCase(analytics, runs)

    private fun video(id: Long) = Video(id = id, userId = 7, title = "영상$id")

    @Test
    fun `이미 쇼츠로 만든 영상은 건너뛰고 그다음 순위를 근거와 함께 권한다`() {
        every { analytics.getTopVideos(7, 14, any()) } returns listOf(video(1), video(2), video(3))
        every { runs.findSourceVideoIdsWithRuns(7, listOf(1L, 2L, 3L)) } returns setOf(1L)

        val actions = useCase.nextActions(7)

        assertEquals(1, actions.size)
        assertEquals(2L, actions[0].videoId)
        assertEquals(NextActionsUseCase.TYPE_MAKE_SHORTS, actions[0].type)
        assertTrue(actions[0].reason.contains("2위"), actions[0].reason)
    }

    @Test
    fun `측정된 영상이 없거나 모두 쇼츠로 만들었으면 아무것도 권하지 않는다`() {
        every { analytics.getTopVideos(7, 14, any()) } returns emptyList()
        assertEquals(emptyList(), useCase.nextActions(7))

        every { analytics.getTopVideos(7, 14, any()) } returns listOf(video(1))
        every { runs.findSourceVideoIdsWithRuns(7, listOf(1L)) } returns setOf(1L)
        assertEquals(emptyList(), useCase.nextActions(7))
    }
}
