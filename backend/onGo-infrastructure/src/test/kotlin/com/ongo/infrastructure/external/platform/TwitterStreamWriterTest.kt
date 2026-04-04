package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.twitter.TwitterApi
import com.ongo.infrastructure.external.twitter.TwitterConfig
import com.ongo.infrastructure.external.twitter.TwitterMediaApi
import com.ongo.infrastructure.external.twitter.dto.TwitterCreateTweetRequest
import com.ongo.infrastructure.external.twitter.dto.TwitterCreateTweetResponse
import com.ongo.infrastructure.external.twitter.dto.TwitterMediaInitResponse
import com.ongo.infrastructure.external.twitter.dto.TwitterMediaStatusResponse
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class TwitterStreamWriterTest {

    private lateinit var twitterApi: TwitterApi
    private lateinit var twitterMediaApi: TwitterMediaApi
    private lateinit var twitterConfig: TwitterConfig
    private lateinit var fileTransferHelper: PlatformFileTransferHelper
    private lateinit var writer: TwitterStreamWriter

    private val accessToken = "test-access-token"
    private val mediaId = "1234567890"
    private val tweetId = "9876543210"
    private val uploadBaseUrl = "https://upload.twitter.com"

    @BeforeEach
    fun setUp() {
        twitterApi = mockk()
        twitterMediaApi = mockk()
        twitterConfig = mockk()
        fileTransferHelper = mockk()

        every { twitterConfig.getUploadBaseUrl() } returns uploadBaseUrl

        writer = TwitterStreamWriter(twitterApi, twitterMediaApi, twitterConfig, fileTransferHelper)
    }

    private fun defaultMeta(
        title: String = "테스트 영상",
        tags: List<String> = listOf("태그1", "태그2"),
    ) = VideoPlatformMeta(
        videoUploadId = 1L,
        title = title,
        description = "테스트 설명",
        tags = tags,
        visibility = Visibility.PUBLIC,
    )

    private fun stubInitUpload(returnMediaId: String = mediaId) {
        every {
            twitterMediaApi.initUpload(
                authorization = "Bearer $accessToken",
                command = "INIT",
                totalBytes = any(),
                mediaType = "video/mp4",
                mediaCategory = "tweet_video",
            )
        } returns TwitterMediaInitResponse(mediaIdString = returnMediaId, expiresAfterSecs = 86400)
    }

    private fun stubFinalizeUpload(state: String? = null) {
        every {
            twitterMediaApi.finalizeUpload(
                authorization = "Bearer $accessToken",
                command = "FINALIZE",
                mediaId = mediaId,
            )
        } returns TwitterMediaStatusResponse(
            mediaIdString = mediaId,
            processingInfo = if (state != null) {
                TwitterMediaStatusResponse.ProcessingInfo(
                    state = state,
                    checkAfterSecs = 1,
                    error = null,
                )
            } else null,
        )
    }

    private fun stubCreateTweet(returnTweetId: String = tweetId) {
        every {
            twitterApi.createTweet(
                authorization = "Bearer $accessToken",
                request = any(),
            )
        } returns TwitterCreateTweetResponse(
            data = TwitterCreateTweetResponse.TweetData(id = returnTweetId, text = null),
        )
    }

    private fun stubAppendToTwitterMedia() {
        justRun {
            fileTransferHelper.appendToTwitterMedia(
                uploadUrl = any(),
                accessToken = accessToken,
                mediaId = mediaId,
                segmentIndex = any(),
                chunkData = any(),
            )
        }
    }

    // ──────────────────────────────────────────────
    // initSession 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `initSession 성공 시 mediaId 반환`() {
        // Given
        stubInitUpload("9999999999")
        val meta = defaultMeta()

        // When
        val result = writer.initSession(
            meta = meta,
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = 1024L,
            scheduledAt = null,
        )

        // Then
        assertThat(result).isEqualTo("9999999999")
    }

    @Test
    fun `scheduledAt 설정 시 경고만 남기고 정상 처리`() {
        // Given
        stubInitUpload()
        val meta = defaultMeta()
        val scheduledAt = LocalDateTime.now().plusDays(1)

        // When
        val result = writer.initSession(
            meta = meta,
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = 1024L,
            scheduledAt = scheduledAt,
        )

        // Then — 예외 없이 mediaId 반환
        assertThat(result).isEqualTo(mediaId)
    }

    // ──────────────────────────────────────────────
    // complete 테스트
    // ──────────────────────────────────────────────

    @Test
    fun `complete 성공 시 트윗 생성 및 URL 반환`() {
        // Given
        stubInitUpload()
        stubFinalizeUpload(state = null) // processingInfo 없음 — 즉시 완료
        stubAppendToTwitterMedia()
        stubCreateTweet()

        writer.initSession(
            meta = defaultMeta(),
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = 1024L,
            scheduledAt = null,
        )
        writer.writeChunk("hello".toByteArray(), 0L, 5L)

        // When
        val result = writer.complete()

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.platformVideoId).isEqualTo(tweetId)
        assertThat(result.platformUrl).isEqualTo("https://twitter.com/i/status/$tweetId")
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `미디어 처리 대기 후 완료`() {
        // Given
        stubInitUpload()
        stubFinalizeUpload(state = "pending")
        stubAppendToTwitterMedia()
        stubCreateTweet()

        // checkStatus 첫 번째 호출: in_progress, 두 번째 호출: succeeded
        every {
            twitterMediaApi.checkStatus(
                authorization = "Bearer $accessToken",
                command = "STATUS",
                mediaId = mediaId,
            )
        } returnsMany listOf(
            TwitterMediaStatusResponse(
                mediaIdString = mediaId,
                processingInfo = TwitterMediaStatusResponse.ProcessingInfo(
                    state = "in_progress",
                    checkAfterSecs = 0, // 즉시 재폴링 (Thread.sleep(0) 방지를 위해 0 사용)
                    error = null,
                ),
            ),
            TwitterMediaStatusResponse(
                mediaIdString = mediaId,
                processingInfo = TwitterMediaStatusResponse.ProcessingInfo(
                    state = "succeeded",
                    checkAfterSecs = null,
                    error = null,
                ),
            ),
        )

        writer.initSession(
            meta = defaultMeta(),
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = 512L,
            scheduledAt = null,
        )
        writer.writeChunk("data".toByteArray(), 0L, 4L)

        // When
        val result = writer.complete()

        // Then
        assertThat(result.success).isTrue()
        assertThat(result.platformVideoId).isEqualTo(tweetId)
        verify(exactly = 2) {
            twitterMediaApi.checkStatus(any(), any(), any())
        }
    }

    @Test
    fun `미디어 처리 실패 시 에러 반환`() {
        // Given
        stubInitUpload()
        stubFinalizeUpload(state = "pending")
        stubAppendToTwitterMedia()

        every {
            twitterMediaApi.checkStatus(
                authorization = "Bearer $accessToken",
                command = "STATUS",
                mediaId = mediaId,
            )
        } returns TwitterMediaStatusResponse(
            mediaIdString = mediaId,
            processingInfo = TwitterMediaStatusResponse.ProcessingInfo(
                state = "failed",
                checkAfterSecs = null,
                error = TwitterMediaStatusResponse.ProcessingInfo.ErrorInfo(
                    code = 3,
                    message = "동영상 처리 중 오류 발생",
                ),
            ),
        )

        writer.initSession(
            meta = defaultMeta(),
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = 256L,
            scheduledAt = null,
        )
        writer.writeChunk("fail".toByteArray(), 0L, 4L)

        // When
        val result = writer.complete()

        // Then
        assertThat(result.success).isFalse()
        assertThat(result.errorMessage).contains("동영상 처리 중 오류 발생")
    }

    @Test
    fun `initSession 미호출 시 complete에서 예외`() {
        // Given — initSession 호출 없음
        // mediaId가 null인 상태이므로 try 블록 진입 전에 IllegalStateException 발생

        // When & Then
        val ex = assertThrows<IllegalStateException> {
            writer.complete()
        }
        assertThat(ex.message).contains("initSession() 호출 필요")
    }

    @Test
    fun `빈 태그 필터링 — 공백 태그는 트윗 텍스트에 포함되지 않음`() {
        // Given
        val meta = defaultMeta(
            title = "공백태그 테스트",
            tags = listOf("정상태그", "", "   ", "또다른태그"),
        )
        stubInitUpload()
        stubFinalizeUpload(state = null)
        stubAppendToTwitterMedia()

        val tweetRequestSlot = slot<TwitterCreateTweetRequest>()
        every {
            twitterApi.createTweet(
                authorization = "Bearer $accessToken",
                request = capture(tweetRequestSlot),
            )
        } returns TwitterCreateTweetResponse(
            data = TwitterCreateTweetResponse.TweetData(id = tweetId, text = null),
        )

        writer.initSession(
            meta = meta,
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = 100L,
            scheduledAt = null,
        )
        writer.writeChunk("x".toByteArray(), 0L, 1L)

        // When
        writer.complete()

        // Then
        val tweetText = tweetRequestSlot.captured.text
        assertThat(tweetText).contains("#정상태그")
        assertThat(tweetText).contains("#또다른태그")
        assertThat(tweetText).doesNotContain("# ") // 공백만 있는 태그 필터됨
    }

    @Test
    fun `5MB 초과 파일은 여러 APPEND 호출`() {
        // Given
        val fiveMb = 5 * 1024 * 1024
        val largeData = ByteArray(fiveMb + 1024) { it.toByte() } // 5MB + 1KB

        stubInitUpload()
        stubFinalizeUpload(state = null)
        stubCreateTweet()

        val appendCallCount = mutableListOf<Int>()
        every {
            fileTransferHelper.appendToTwitterMedia(
                uploadUrl = "$uploadBaseUrl/1.1/media/upload.json",
                accessToken = accessToken,
                mediaId = mediaId,
                segmentIndex = capture(appendCallCount),
                chunkData = any(),
            )
        } just Runs

        writer.initSession(
            meta = defaultMeta(),
            accessToken = accessToken,
            platformChannelId = null,
            fileSize = largeData.size.toLong(),
            scheduledAt = null,
        )

        // 청크를 한 번에 쓰는 대신 전체 데이터를 writeChunk로 전달
        writer.writeChunk(largeData, 0L, largeData.size.toLong())

        // When
        val result = writer.complete()

        // Then
        assertThat(result.success).isTrue()
        // 5MB + 1KB는 두 번의 APPEND 호출이 필요 (segment 0, segment 1)
        verify(exactly = 2) {
            fileTransferHelper.appendToTwitterMedia(
                uploadUrl = any(),
                accessToken = any(),
                mediaId = any(),
                segmentIndex = any(),
                chunkData = any(),
            )
        }
        assertThat(appendCallCount).containsExactly(0, 1)
    }
}
