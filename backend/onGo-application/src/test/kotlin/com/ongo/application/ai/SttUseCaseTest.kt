package com.ongo.application.ai

import com.ongo.application.ai.audio.AudioPart
import com.ongo.application.ai.audio.AudioPreparationException
import com.ongo.application.ai.audio.PreparedAudio
import com.ongo.application.ai.audio.TranscriptionAudioPort
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse
import org.springframework.ai.audio.transcription.AudioTranscription
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 쇼츠 전사 입력 준비.
 *
 * 이전 구현은 원본 **영상** URL 을 그대로 전사 모델에 넘겼다. 영상 트랙까지 통째로 올리는
 * 데다 길이 제한도 없어서 실제 롱폼에서는 요청이 거부되거나 API 서버가 파일 전체를
 * 중계했다. 그래서 이 테스트는 "잘 동작한다"보다 **원본을 직접 보내지 않는다**와
 * **준비가 안 되면 모델을 부르지 않는다**를 먼저 고정한다.
 */
@ExtendWith(MockKExtension::class)
class SttUseCaseTest {

    @MockK
    lateinit var transcriptionModel: OpenAiAudioTranscriptionModel

    @MockK
    lateinit var creditService: CreditService

    @MockK
    lateinit var rateLimiter: AiRateLimiter

    @MockK
    lateinit var videoRepository: VideoRepository

    @MockK
    lateinit var audioPort: TranscriptionAudioPort

    private lateinit var useCase: SttUseCase

    private val userId = 7L
    private val videoId = 55L
    private val sourceUrl = "https://storage.test/videos/55/source.mp4?sig=abc"

    @BeforeEach
    fun setUp() {
        useCase = SttUseCase(transcriptionModel, creditService, rateLimiter, videoRepository, audioPort)
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "롱폼", fileUrl = sourceUrl)
    }

    /** close 가 실제로 불렸는지 세는 조각 묶음. 임시 파일 정리는 이 호출에 달려 있다. */
    private class FakePreparedAudio(override val parts: List<AudioPart>) : PreparedAudio {
        var closed = 0
        override fun close() {
            closed++
        }
    }

    private fun part(offsetMs: Long) = AudioPart(ByteArrayResource("audio".toByteArray()), offsetMs)

    private fun verboseJson(text: String, vararg segments: Triple<Double, Double, String>): String {
        val segmentJson = segments.joinToString(",") { (start, end, body) ->
            """{"start":$start,"end":$end,"text":"$body"}"""
        }
        return """{"text":"$text","segments":[$segmentJson]}"""
    }

    private fun respondPerPart(vararg responses: String) {
        val queue = ArrayDeque(responses.toList())
        every { transcriptionModel.call(any<AudioTranscriptionPrompt>()) } answers {
            AudioTranscriptionResponse(AudioTranscription(queue.removeFirst()))
        }
    }

    // ---- 원본을 직접 보내지 않는다 ----

    @Test
    fun `정상 경로에서 원본 영상 URL 을 전사 모델에 넘기지 않는다`() {
        val prepared = FakePreparedAudio(listOf(part(0)))
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } returns prepared
        val prompt = slot<AudioTranscriptionPrompt>()
        every { transcriptionModel.call(capture(prompt)) } returns
            AudioTranscriptionResponse(AudioTranscription(verboseJson("안녕하세요")))

        useCase.executeInternal(userId, videoId)

        // 준비된 조각이어야 한다. UrlResource 면 원본을 그대로 보내던 예전 경로다.
        val sent: Resource = prompt.captured.instructions
        assertTrue(sent !is UrlResource, "원본 URL 리소스를 그대로 전송했다")
        assertEquals(prepared.parts.single().resource, sent)
        verify(exactly = 1) { audioPort.prepare(sourceUrl) }
    }

    // ---- 여러 조각 병합 ----

    @Test
    fun `조각별 타임스탬프에 조각 오프셋을 더해 원본 타임라인으로 되돌린다`() {
        val prepared = FakePreparedAudio(listOf(part(0), part(600_024), part(1_200_048)))
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } returns prepared
        respondPerPart(
            verboseJson("첫 조각", Triple(0.0, 1.5, "첫 조각")),
            verboseJson("둘째 조각", Triple(2.0, 3.5, "둘째 조각")),
            verboseJson("셋째 조각", Triple(4.0, 5.0, "셋째 조각")),
        )

        val result = useCase.executeInternal(userId, videoId)

        assertEquals(3, result.segments.size)
        assertEquals(listOf(0.0, 602.024, 1204.048), result.segments.map { it.startTime })
        assertEquals(listOf(1.5, 603.524, 1205.048), result.segments.map { it.endTime })
        // 본문도 이어붙는다. 조각 하나만 남으면 뒤 단계가 앞부분만 보고 판단한다.
        assertEquals("첫 조각 둘째 조각 셋째 조각", result.text)
        verify(exactly = 3) { transcriptionModel.call(any<AudioTranscriptionPrompt>()) }
    }

    @Test
    fun `조각을 순서대로 하나씩 전사한다`() {
        val prepared = FakePreparedAudio(listOf(part(0), part(600_000)))
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } returns prepared
        val sent = mutableListOf<Resource>()
        every { transcriptionModel.call(any<AudioTranscriptionPrompt>()) } answers {
            sent += firstArg<AudioTranscriptionPrompt>().instructions
            AudioTranscriptionResponse(AudioTranscription(verboseJson("x")))
        }

        useCase.executeInternal(userId, videoId)

        assertEquals(prepared.parts.map { it.resource }, sent)
    }

    // ---- 정리 ----

    @Test
    fun `전사에 성공하면 조각을 정리한다`() {
        val prepared = FakePreparedAudio(listOf(part(0)))
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } returns prepared
        respondPerPart(verboseJson("본문"))

        useCase.executeInternal(userId, videoId)

        assertEquals(1, prepared.closed)
    }

    @Test
    fun `전사 도중 실패해도 조각을 정리한다`() {
        val prepared = FakePreparedAudio(listOf(part(0), part(600_000)))
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } returns prepared
        every { transcriptionModel.call(any<AudioTranscriptionPrompt>()) } throws RuntimeException("provider 5xx")

        assertFailsWith<RuntimeException> { useCase.executeInternal(userId, videoId) }

        assertEquals(1, prepared.closed)
    }

    // ---- 정직한 실패 ----

    @Test
    fun `인코더를 쓸 수 없으면 전사 모델을 부르지 않는다`() {
        every { audioPort.isAvailable() } returns false

        val ex = assertFailsWith<BusinessException> { useCase.executeInternal(userId, videoId) }

        assertEquals("STT_ENCODER_UNAVAILABLE", ex.code)
        verify(exactly = 0) { audioPort.prepare(any()) }
        verify(exactly = 0) { transcriptionModel.call(any<AudioTranscriptionPrompt>()) }
    }

    @Test
    fun `오디오 추출에 실패하면 전사 모델을 부르지 않는다`() {
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } throws AudioPreparationException("오디오 없음")

        val ex = assertFailsWith<BusinessException> { useCase.executeInternal(userId, videoId) }

        assertEquals("STT_AUDIO_PREPARATION_FAILED", ex.code)
        verify(exactly = 0) { transcriptionModel.call(any<AudioTranscriptionPrompt>()) }
    }

    // ---- 기존 소유권/크레딧 동작 보존 ----

    @Test
    fun `남의 영상이면 준비도 전사도 하지 않는다`() {
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = 999L, title = "남의 롱폼", fileUrl = sourceUrl)

        assertFailsWith<ForbiddenException> { useCase.executeInternal(userId, videoId) }

        verify(exactly = 0) { audioPort.prepare(any()) }
        verify(exactly = 0) { transcriptionModel.call(any<AudioTranscriptionPrompt>()) }
    }

    @Test
    fun `파일 URL 이 없으면 준비를 시도하지 않는다`() {
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "URL 없음", fileUrl = null)

        val ex = assertFailsWith<BusinessException> { useCase.executeInternal(userId, videoId) }

        assertEquals("VIDEO_FILE_NOT_FOUND", ex.code)
        verify(exactly = 0) { audioPort.prepare(any()) }
    }

    @Test
    fun `공개 실행은 크레딧을 차감하고 성공하면 환불하지 않는다`() {
        val prepared = FakePreparedAudio(listOf(part(0)))
        every { rateLimiter.checkRateLimit(userId) } just runs
        every { creditService.validateAndDeduct(userId, AiFeature.STT) } returns mockk(relaxed = true)
        every { audioPort.isAvailable() } returns true
        every { audioPort.prepare(sourceUrl) } returns prepared
        respondPerPart(verboseJson("본문"))

        useCase.execute(userId, videoId)

        verify(exactly = 1) { creditService.validateAndDeduct(userId, AiFeature.STT) }
        verify(exactly = 0) { creditService.refundCredit(any(), any(), any()) }
    }

    /*
     * 준비 실패는 사용자 잘못이 아니다. 예전에는 BusinessException 이 그대로 빠져나가
     * 차감한 크레딧이 남았다.
     */
    @Test
    fun `인코더 부재로 실패하면 차감한 크레딧을 환불한다`() {
        every { rateLimiter.checkRateLimit(userId) } just runs
        every { creditService.validateAndDeduct(userId, AiFeature.STT) } returns mockk(relaxed = true)
        every { creditService.refundCredit(any(), any(), any()) } just runs
        every { audioPort.isAvailable() } returns false

        val ex = assertFailsWith<BusinessException> { useCase.execute(userId, videoId) }

        assertEquals("STT_ENCODER_UNAVAILABLE", ex.code)
        verify(exactly = 1) { creditService.refundCredit(userId, AiFeature.STT.creditCost, AiFeature.STT.name) }
    }
}
