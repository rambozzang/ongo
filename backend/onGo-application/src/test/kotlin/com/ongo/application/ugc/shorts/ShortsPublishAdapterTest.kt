package com.ongo.application.ugc.shorts

import com.ongo.application.video.PlatformUploadStatus
import com.ongo.application.video.PublishResult
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ShortsPublishAdapterTest {

    private val publishVideoUseCase = mockk<PublishVideoUseCase>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val adapter = ShortsPublishAdapter(publishVideoUseCase, videoRepository, videoUploadRepository)

    @Test
    fun `encoded channel target resolves its own durable upload row`() {
        val userId = 42L
        val videoId = 904L
        val scheduledAt = Instant.parse("2026-03-01T09:00:00Z")
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "렌더 쇼츠",
            fileUrl = "https://storage.test/short.mp4",
        )
        every {
            publishVideoUseCase.publishVideo(userId, videoId, match { configs ->
                configs.single().platform == Platform.YOUTUBE &&
                    configs.single().channelId == 202L
            })
        } returns PublishResult(
            videoId = videoId,
            uploads = listOf(PlatformUploadStatus(Platform.YOUTUBE, UploadStatus.UPLOADING)),
        )
        every { videoUploadRepository.findByVideoIdAndChannelId(videoId, 202L) } returns VideoUpload(
            id = 802L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 202L,
            status = UploadStatus.UPLOADING,
        )
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) } returns VideoUpload(
            id = 801L,
            videoId = videoId,
            platform = Platform.YOUTUBE,
            channelId = 201L,
            status = UploadStatus.PUBLISHED,
        )

        val result = adapter.publishAll(
            userId = userId,
            videoId = videoId,
            requests = listOf(ShortsPublishRequest("YOUTUBE#202", null, null, scheduledAt)),
        )

        assertEquals("YOUTUBE#202", result.single().platform)
        assertEquals(802L, result.single().videoUploadId)
        verify(exactly = 0) { videoUploadRepository.findByVideoIdAndPlatform(videoId, Platform.YOUTUBE) }
    }

    // ---- 플랫폼별 메타 → 게시 값 ----

    private val userId = 42L
    private val videoId = 905L
    private val scheduledAt: Instant = Instant.parse("2026-03-01T09:00:00Z")

    /** 게시 흐름에 넘긴 설정을 잡아 둔다. 게시 결과는 대상마다 UPLOADING 이다. */
    private fun capturePublish(): io.mockk.CapturingSlot<List<com.ongo.application.video.PlatformUploadConfig>> {
        val configs = io.mockk.slot<List<com.ongo.application.video.PlatformUploadConfig>>()
        every { videoRepository.findById(videoId) } returns Video(id = videoId, userId = userId, title = "렌더 쇼츠")
        every { videoRepository.findById(5L) } returns Video(id = 5L, userId = userId, title = "원본", tags = listOf("원본태그"))
        every { publishVideoUseCase.publishVideo(userId, videoId, capture(configs)) } answers {
            PublishResult(videoId, configs.captured.map { PlatformUploadStatus(it.platform, UploadStatus.UPLOADING) })
        }
        every { videoUploadRepository.findByVideoIdAndPlatform(videoId, any()) } returns null
        return configs
    }

    @Test
    fun `플랫폼별 메타의 제목·설명·해시태그로 게시한다 — 태그를 빈 값으로 보내지 않는다`() {
        val configs = capturePublish()
        val meta = ShortsPlatformMeta.toJson(
            listOf(
                ShortsPlatformMeta.Entry("YOUTUBE", "유튜브 제목", "유튜브 설명", listOf("재테크", "월급")),
                ShortsPlatformMeta.Entry("TIKTOK", "틱톡 제목", "틱톡 설명", listOf("틱톡태그")),
            ),
        )

        adapter.publishAll(
            userId, videoId,
            listOf("YOUTUBE", "TIKTOK").map {
                ShortsPublishRequest(it, "클립 제목", "클립 설명", scheduledAt, platformMetaJson = meta, sourceVideoId = 5L)
            },
        )

        val byPlatform = configs.captured.associateBy { it.platform }
        assertEquals("유튜브 제목", byPlatform.getValue(Platform.YOUTUBE).title)
        assertEquals("유튜브 설명", byPlatform.getValue(Platform.YOUTUBE).description)
        assertEquals(listOf("재테크", "월급"), byPlatform.getValue(Platform.YOUTUBE).tags)
        assertEquals("틱톡 제목", byPlatform.getValue(Platform.TIKTOK).title)
        assertEquals(listOf("틱톡태그"), byPlatform.getValue(Platform.TIKTOK).tags)
    }

    @Test
    fun `메타가 없는 클립은 원본 영상 태그를 쓰고 제목이 없으면 렌더 영상 제목을 쓴다`() {
        val configs = capturePublish()

        adapter.publishAll(userId, videoId, listOf(ShortsPublishRequest("INSTAGRAM", null, null, scheduledAt, sourceVideoId = 5L)))

        val config = configs.captured.single()
        assertEquals("렌더 쇼츠", config.title)
        assertEquals(listOf("원본태그"), config.tags)
        assertEquals(null, config.description)
    }

    @Test
    fun `게시 한도를 넘는 클립 제목은 게시 전에 한도에 맞춘다 — 게시에서 거절되지 않게`() {
        val configs = capturePublish()

        adapter.publishAll(userId, videoId, listOf(ShortsPublishRequest("YOUTUBE", "가".repeat(150), null, scheduledAt)))

        assertEquals(100, configs.captured.single().title.length)
    }
}
