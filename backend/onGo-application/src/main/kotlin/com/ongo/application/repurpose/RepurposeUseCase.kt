package com.ongo.application.repurpose

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.ContentRepurposeResult
import com.ongo.application.credit.CreditService
import com.ongo.application.repurpose.dto.RepurposeClipResponse
import com.ongo.application.repurpose.dto.RepurposeDetailResponse
import com.ongo.application.repurpose.dto.RepurposeJobResponse
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.repurpose.RepurposeClip
import com.ongo.domain.repurpose.RepurposeClipRepository
import com.ongo.domain.repurpose.RepurposeJob
import com.ongo.domain.repurpose.RepurposeJobRepository
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RepurposeUseCase(
    private val repurposeJobRepository: RepurposeJobRepository,
    private val repurposeClipRepository: RepurposeClipRepository,
    private val videoRepository: VideoRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(RepurposeUseCase::class.java)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     *
     * ## job 행을 차감 **뒤에** 만드는 이유
     *
     * 예전에는 `withCredits` 보다 먼저 `PROCESSING` job 을 저장했다. 그러면 잔액이 부족해
     * [com.ongo.common.exception.InsufficientCreditException] 이 나는 순간, 아무도 끝내지
     * 않을 `PROCESSING` job 이 목록에 남는다. 사용자는 "분석 중" 을 영원히 보고, 그 행을
     * 정리하는 코드는 어디에도 없다.
     *
     * 차감이 확정된 뒤에만 만든다. 저장 자체가 실패하면 블록 밖의 [CreditService.withCredits]
     * 가 환불하고, 그때는 job 이 아예 없으므로 남길 상태도 없다.
     *
     * job 상태는 트랜잭션 롤백이 아니라 명시적으로 적는다. 원래 흐름 그대로다.
     */
    fun analyzeForRepurpose(userId: Long, videoId: Long): RepurposeDetailResponse {
        rateLimiter.checkRateLimit(userId)

        val video = videoRepository.findById(videoId)
            ?: throw BusinessException("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")

        if (video.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "접근 권한이 없습니다")
        }

        val transcript = video.description ?: video.title

        return creditService.withCredits(userId, AiFeature.CONTENT_REPURPOSE) {
        val job = repurposeJobRepository.save(
            RepurposeJob(
                userId = userId,
                videoId = videoId,
                videoTitle = video.title,
                status = "PROCESSING",
            )
        )

        try {
            val userPrompt = PromptTemplates.CONTENT_REPURPOSE_USER
                .replace("{title}", InputSanitizer.sanitize(video.title))
                .replace("{transcript}", InputSanitizer.sanitize(transcript))

            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.CONTENT_REPURPOSE_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ContentRepurposeResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            val clips = repurposeClipRepository.saveAll(
                result.clips.map { clip ->
                    RepurposeClip(
                        jobId = job.id,
                        startTime = clip.startTime,
                        endTime = clip.endTime,
                        title = clip.title,
                        description = clip.description,
                        viralScore = clip.viralScore,
                        reasoning = clip.reasoning,
                        suggestedPlatform = clip.suggestedPlatform,
                    )
                }
            )

            val completedJob = repurposeJobRepository.updateStatus(job.id, "COMPLETED", clips.size)
                ?: job.copy(status = "COMPLETED", clipCount = clips.size)

            RepurposeDetailResponse(
                job = toJobResponse(completedJob),
                clips = clips.map { toClipResponse(it) },
            )
        } catch (e: BusinessException) {
            repurposeJobRepository.updateStatus(job.id, "FAILED", 0)
            throw e
        } catch (e: Exception) {
            log.error("AI 리퍼포징 분석 실패: userId={}, videoId={}", userId, videoId, e)
            repurposeJobRepository.updateStatus(job.id, "FAILED", 0)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
        }
    }

    fun getRepurposeJobs(userId: Long): List<RepurposeJobResponse> =
        repurposeJobRepository.findByUserId(userId).map { toJobResponse(it) }

    fun getRepurposeDetail(userId: Long, jobId: Long): RepurposeDetailResponse {
        val job = repurposeJobRepository.findById(jobId)
            ?: throw BusinessException("JOB_NOT_FOUND", "리퍼포징 작업을 찾을 수 없습니다: $jobId")

        if (job.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "접근 권한이 없습니다")
        }

        val clips = repurposeClipRepository.findByJobId(jobId)

        return RepurposeDetailResponse(
            job = toJobResponse(job),
            clips = clips.map { toClipResponse(it) },
        )
    }

    private fun toJobResponse(job: RepurposeJob) = RepurposeJobResponse(
        id = job.id,
        videoId = job.videoId,
        videoTitle = job.videoTitle,
        status = job.status,
        clipCount = job.clipCount,
        createdAt = job.createdAt.toString(),
    )

    private fun toClipResponse(clip: RepurposeClip) = RepurposeClipResponse(
        id = clip.id,
        jobId = clip.jobId,
        startTime = clip.startTime,
        endTime = clip.endTime,
        title = clip.title,
        description = clip.description,
        viralScore = clip.viralScore,
        reasoning = clip.reasoning,
        suggestedPlatform = clip.suggestedPlatform,
    )
}
