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
import org.springframework.transaction.annotation.Transactional

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

    @Transactional
    fun analyzeForRepurpose(userId: Long, videoId: Long): RepurposeDetailResponse {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.CONTENT_REPURPOSE)

        val video = videoRepository.findById(videoId)
            ?: throw BusinessException("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")

        if (video.userId != userId) {
            throw BusinessException("ACCESS_DENIED", "접근 권한이 없습니다")
        }

        val transcript = video.description ?: video.title

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

            return RepurposeDetailResponse(
                job = toJobResponse(completedJob),
                clips = clips.map { toClipResponse(it) },
            )
        } catch (e: BusinessException) {
            repurposeJobRepository.updateStatus(job.id, "FAILED", 0)
            throw e
        } catch (e: Exception) {
            log.error("AI 리퍼포징 분석 실패, 크레딧 환불: userId={}, videoId={}", userId, videoId, e)
            repurposeJobRepository.updateStatus(job.id, "FAILED", 0)
            creditService.refundCredit(userId, AiFeature.CONTENT_REPURPOSE.creditCost, AiFeature.CONTENT_REPURPOSE.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
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
