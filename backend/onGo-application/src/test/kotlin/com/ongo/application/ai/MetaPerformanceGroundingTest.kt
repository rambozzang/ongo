package com.ongo.application.ai

import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.Video
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 내 채널 실측 성과를 메타 생성 근거로 넣되, 데이터가 적으면 넣지 않고 그 이유를 말한다. */
class MetaPerformanceGroundingTest {

    private val repo = mockk<AnalyticsRepository>()
    private val grounding = MetaPerformanceGrounding(repo)

    private fun videos(n: Int) = (1..n).map { Video(id = it.toLong(), userId = 7, title = "영상$it 제목", tags = listOf("태그$it")) }

    @Test
    fun `측정된 영상이 6개 미만이면 근거를 넣지 않고 이유를 돌려준다`() {
        every { repo.getTopVideos(7, 90, any()) } returns videos(5)

        val g = grounding.forUser(7)

        assertFalse(g.used)
        assertNull(g.promptBlock)
        assertTrue(g.reason!!.contains("5개") && g.reason!!.contains("6개 이상"), g.reason)
    }

    @Test
    fun `잘 된 영상 5개와 덜 된 영상을 실측 순서대로 나눠 넣는다`() {
        every { repo.getTopVideos(7, 90, any()) } returns videos(10)

        val g = grounding.forUser(7)
        val block = g.promptBlock!!

        assertTrue(g.used)
        assertEquals(5, g.topCount)
        assertEquals(3, g.bottomCount)
        assertTrue(block.startsWith("<user_input>"), "사용자 데이터는 지시가 아니라 자료로 감싼다")
        assertTrue(block.indexOf("영상1 제목") < block.indexOf("조회수가 낮았던") && block.indexOf("영상10 제목") > block.indexOf("조회수가 낮았던"), block)
        assertTrue(block.contains("[태그: 태그1]"), block)
        assertTrue(block.contains("베끼지 마세요"), block)
    }

    @Test
    fun `성과 조회가 실패하면 생성은 막지 않고 근거 없이 진행한다`() {
        every { repo.getTopVideos(any(), any(), any()) } throws IllegalStateException("db")

        val g = grounding.forUser(7)

        assertFalse(g.used)
        assertTrue(g.reason!!.isNotBlank())
    }
}
