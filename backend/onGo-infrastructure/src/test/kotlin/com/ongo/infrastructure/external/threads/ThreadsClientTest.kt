package com.ongo.infrastructure.external.threads

import com.ongo.common.enums.Visibility
import com.ongo.infrastructure.external.platform.PlatformUploadRequest
import com.ongo.infrastructure.external.threads.dto.ThreadsMediaContainerResponse
import com.ongo.infrastructure.external.threads.dto.ThreadsMediaContainerStatusResponse
import com.ongo.infrastructure.external.threads.dto.ThreadsMediaResponse
import com.ongo.infrastructure.external.threads.dto.ThreadsPublishResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThreadsClientTest {

    @Test
    fun `영상 컨테이너가 처리된 뒤 게시하고 실제 permalink를 반환한다`() {
        val api = mockk<ThreadsApi>()
        val oauthApi = mockk<ThreadsOAuthApi>()
        val config = mockk<ThreadsConfig>()
        every { api.createMediaContainer(any(), any(), any(), any(), any()) } returns
            ThreadsMediaContainerResponse("container-1")
        every { api.getContainerStatus(any(), any(), any()) } returnsMany listOf(
            ThreadsMediaContainerStatusResponse(id = "container-1", status = "IN_PROGRESS"),
            ThreadsMediaContainerStatusResponse(id = "container-1", status = "FINISHED"),
        )
        every { api.publishThread(any(), any(), any()) } returns ThreadsPublishResponse("thread-1")
        every { api.getThread(any(), any(), any()) } returns ThreadsMediaResponse(
            id = "thread-1",
            text = "테스트",
            timestamp = null,
            mediaType = "VIDEO",
            mediaUrl = null,
            permalink = "https://threads.net/@creator/post/thread-1",
            isQuotePost = false,
        )

        val result = ThreadsClient(api, oauthApi, config).uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://storage.test/video.mp4",
                title = "테스트",
                description = "설명",
                tags = listOf("#태그", "topic"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "token",
                platformChannelId = "user-1",
                fileSize = 4,
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("thread-1")
        assertThat(result.platformUrl).isEqualTo("https://threads.net/@creator/post/thread-1")
        verify(exactly = 1) {
            api.createMediaContainer(
                "user-1",
                "VIDEO",
                "https://storage.test/video.mp4",
                "테스트\n\n설명\n\n#태그 #topic",
                "token",
            )
        }
        verify(exactly = 2) { api.getContainerStatus("container-1", "id,status,error_message", "token") }
        verify(exactly = 1) { api.publishThread("user-1", "container-1", "token") }
    }

    @Test
    fun `게시 상태 조회는 thread 상세의 실제 permalink를 반환한다`() {
        val api = mockk<ThreadsApi>()
        every { api.getThread("thread-1", "id,media_type,permalink", "token") } returns ThreadsMediaResponse(
            id = "thread-1",
            text = null,
            timestamp = null,
            mediaType = "VIDEO",
            mediaUrl = null,
            permalink = "https://threads.net/@creator/post/thread-1",
            isQuotePost = false,
        )

        val result = ThreadsClient(api, mockk(), mockk()).getVideoStatus("thread-1", "token")

        assertThat(result.status).isEqualTo("PUBLISHED")
        assertThat(result.platformUrl).isEqualTo("https://threads.net/@creator/post/thread-1")
    }

    @Test
    fun `permalink가 없는 게시 응답은 임의 URL을 만들지 않는다`() {
        val api = mockk<ThreadsApi>()
        every { api.createMediaContainer(any(), any(), any(), any(), any()) } returns
            ThreadsMediaContainerResponse("container-1")
        every { api.getContainerStatus(any(), any(), any()) } returns
            ThreadsMediaContainerStatusResponse(id = "container-1", status = "FINISHED")
        every { api.publishThread(any(), any(), any()) } returns ThreadsPublishResponse("thread-1")
        every { api.getThread(any(), any(), any()) } returns ThreadsMediaResponse(
            id = "thread-1", text = null, timestamp = null, mediaType = "VIDEO",
            mediaUrl = null, permalink = null, isQuotePost = false,
        )

        val result = ThreadsClient(api, mockk(), mockk()).uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://storage.test/video.mp4",
                title = "테스트", description = "", tags = emptyList(),
                visibility = Visibility.PUBLIC.name, thumbnailUrl = null,
                accessToken = "token", platformChannelId = "user-1", fileSize = 4,
            ),
        )

        assertThat(result.platformUrl).isEmpty()
    }
}
