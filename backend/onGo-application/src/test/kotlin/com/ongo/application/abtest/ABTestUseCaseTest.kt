package com.ongo.application.abtest

import com.ongo.application.abtest.dto.CreateABTestRequest
import com.ongo.application.abtest.dto.CreateVariantRequest
import com.ongo.common.enums.MediaType
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariantRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ABTestUseCaseTest {

    private val tests = mockk<ABTestRepository>(relaxed = true)
    private val variants = mockk<ABTestVariantRepository>(relaxed = true)
    private val videos = mockk<VideoRepository>()
    private val useCase = ABTestUseCase(tests, variants, videos)

    private val video = Video(id = 10L, userId = 1L, title = "내 영상", mediaType = MediaType.VIDEO)

    @Test
    fun `create rejects video owned by another user`() {
        every { videos.findById(10L) } returns video

        assertThrows<ForbiddenException> {
            useCase.createTest(
                2L,
                CreateABTestRequest(
                    videoId = 10L,
                    testName = "테스트",
                    variants = listOf(CreateVariantRequest("A"), CreateVariantRequest("B")),
                ),
            )
        }
        verify(exactly = 0) { tests.save(any()) }
    }

    @Test
    fun `create requires between two and four distinct variants`() {
        assertThrows<IllegalArgumentException> {
            useCase.createTest(
                1L,
                CreateABTestRequest(
                    testName = "테스트",
                    variants = listOf(CreateVariantRequest("A")),
                ),
            )
        }
        verify(exactly = 0) { tests.save(any()) }
    }

    @Test
    fun `create preserves the selected metric and variants`() {
        every { videos.findById(10L) } returns video
        every { tests.save(any()) } answers { firstArg<com.ongo.domain.abtest.ABTest>().copy(id = 3L) }
        every { variants.findByTestId(3L) } returns emptyList()
        every { variants.save(any()) } answers { firstArg<com.ongo.domain.abtest.ABTestVariant>().copy(id = 4L) }

        val result = useCase.createTest(
            1L,
            CreateABTestRequest(
                videoId = 10L,
                testName = "썸네일 테스트",
                metricType = "THUMBNAIL",
                variants = listOf(CreateVariantRequest("A", title = "첫 제목"), CreateVariantRequest("B", title = "둘째 제목")),
            ),
        )

        assertEquals("썸네일 테스트", result.testName)
        verify { tests.save(match { it.metricType == "THUMBNAIL" && it.videoId == 10L }) }
        verify(exactly = 2) { variants.save(any()) }
    }
}
