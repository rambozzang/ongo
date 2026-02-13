package com.ongo.application.ai

import com.ongo.application.credit.CreditService
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.ai.PipelineStepStatus
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AiPipelineUseCase(
    private val sttUseCase: SttUseCase,
    private val analyzeScriptUseCase: AnalyzeScriptUseCase,
    private val generateMetaUseCase: GenerateMetaUseCase,
    private val generateHashtagsUseCase: GenerateHashtagsUseCase,
    private val suggestScheduleUseCase: SuggestScheduleUseCase,
    private val creditService: CreditService,
    private val videoRepository: VideoRepository,
) {

    private val log = LoggerFactory.getLogger(AiPipelineUseCase::class.java)
    private val pipelines = ConcurrentHashMap<String, AiPipeline>()

    fun startPipeline(userId: Long, videoId: Long, stepNames: List<String>, channelId: Long?): AiPipeline {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근 권한이 없습니다")
        }

        val steps = stepNames.map { name ->
            try {
                AiPipelineStep.valueOf(name)
            } catch (e: IllegalArgumentException) {
                throw BusinessException("INVALID_STEP", "유효하지 않은 파이프라인 스텝: $name")
            }
        }

        if (steps.isEmpty()) {
            throw BusinessException("EMPTY_STEPS", "파이프라인 스텝을 1개 이상 선택해주세요")
        }

        val totalCost = AiPipelineStep.calculateTotalCost(steps)
        val discountApplied = steps.size >= AiPipelineStep.MIN_STEPS_FOR_DISCOUNT

        // Pre-validate and reserve credits
        creditService.validateAndDeduct(userId, totalCost, "AI_PIPELINE")

        val pipelineId = UUID.randomUUID().toString()
        val pipeline = AiPipeline(
            id = pipelineId,
            userId = userId,
            videoId = videoId,
            steps = steps,
            totalCreditsCharged = totalCost,
            discountApplied = discountApplied,
        )

        pipelines[pipelineId] = pipeline

        // Execute pipeline in a virtual thread
        Thread.startVirtualThread {
            executePipeline(pipeline, channelId)
        }

        return pipeline
    }

    fun getPipelineStatus(userId: Long, pipelineId: String): AiPipeline {
        val pipeline = pipelines[pipelineId]
            ?: throw NotFoundException("파이프라인", pipelineId)

        if (pipeline.userId != userId) {
            throw ForbiddenException("해당 파이프라인에 접근 권한이 없습니다")
        }

        return pipeline
    }

    fun cancelPipeline(userId: Long, pipelineId: String): AiPipeline {
        val pipeline = pipelines[pipelineId]
            ?: throw NotFoundException("파이프라인", pipelineId)

        if (pipeline.userId != userId) {
            throw ForbiddenException("해당 파이프라인에 접근 권한이 없습니다")
        }

        if (pipeline.status == PipelineStatus.COMPLETED || pipeline.status == PipelineStatus.CANCELLED) {
            throw BusinessException("PIPELINE_ALREADY_DONE", "이미 완료되었거나 취소된 파이프라인입니다")
        }

        pipeline.status = PipelineStatus.CANCELLED

        // Mark remaining pending steps as skipped
        pipeline.steps.forEach { step ->
            if (pipeline.stepStatuses[step] == PipelineStepStatus.PENDING) {
                pipeline.stepStatuses[step] = PipelineStepStatus.SKIPPED
            }
        }

        // Calculate credits for unexecuted steps and refund
        val executedSteps = pipeline.steps.filter {
            pipeline.stepStatuses[it] == PipelineStepStatus.COMPLETED ||
                pipeline.stepStatuses[it] == PipelineStepStatus.RUNNING
        }
        val executedCost = AiPipelineStep.calculateTotalCost(executedSteps)
        val refundAmount = pipeline.totalCreditsCharged - executedCost
        if (refundAmount > 0) {
            creditService.refundCredit(pipeline.userId, refundAmount, "AI_PIPELINE_CANCEL")
        }

        pipeline.completedAt = LocalDateTime.now()

        return pipeline
    }

    private fun executePipeline(pipeline: AiPipeline, channelId: Long?) {
        pipeline.status = PipelineStatus.RUNNING
        var transcript: String? = null
        var analysisKeywords: List<String>? = null
        var analysisCategory: String? = null
        var generatedTitle: String? = null

        try {
            for (step in pipeline.steps) {
                if (pipeline.status == PipelineStatus.CANCELLED) {
                    break
                }

                pipeline.currentStep = step
                pipeline.stepStatuses[step] = PipelineStepStatus.RUNNING

                try {
                    val result: Any? = when (step) {
                        AiPipelineStep.STT -> {
                            val sttResult = sttUseCase.executeInternal(pipeline.userId, pipeline.videoId)
                            transcript = sttResult.text
                            sttResult
                        }

                        AiPipelineStep.ANALYZE_SCRIPT -> {
                            val script = transcript ?: throw BusinessException(
                                "PIPELINE_DEPENDENCY",
                                "대본 분석에는 STT 결과가 필요합니다. STT 스텝을 먼저 추가해주세요."
                            )
                            val analysisResult = analyzeScriptUseCase.executeInternal(pipeline.userId, script)
                            analysisKeywords = analysisResult.keywords
                            analysisCategory = analysisResult.suggestedCategory
                            analysisResult
                        }

                        AiPipelineStep.GENERATE_META -> {
                            val script = transcript ?: throw BusinessException(
                                "PIPELINE_DEPENDENCY",
                                "메타데이터 생성에는 STT 결과가 필요합니다."
                            )
                            val platforms = listOf(com.ongo.common.enums.Platform.YOUTUBE)
                            val category = analysisCategory ?: "엔터테인먼트"
                            val metaResult = generateMetaUseCase.executeInternal(
                                userId = pipeline.userId,
                                script = script,
                                targetPlatforms = platforms,
                                tone = "friendly",
                                category = category,
                            )
                            if (metaResult.platforms.isNotEmpty()) {
                                generatedTitle = metaResult.platforms[0].titleCandidates.firstOrNull()
                            }
                            metaResult
                        }

                        AiPipelineStep.GENERATE_HASHTAGS -> {
                            val title = generatedTitle ?: "영상 제목"
                            val category = analysisCategory ?: "엔터테인먼트"
                            val platforms = listOf(com.ongo.common.enums.Platform.YOUTUBE)
                            generateHashtagsUseCase.executeInternal(
                                userId = pipeline.userId,
                                title = title,
                                category = category,
                                targetPlatforms = platforms,
                            )
                        }

                        AiPipelineStep.SUGGEST_SCHEDULE -> {
                            if (channelId != null) {
                                suggestScheduleUseCase.executeInternal(pipeline.userId, channelId)
                            } else {
                                null
                            }
                        }
                    }

                    pipeline.results[step] = result
                    pipeline.stepStatuses[step] = PipelineStepStatus.COMPLETED
                } catch (e: Exception) {
                    log.error("파이프라인 스텝 실패: pipelineId={}, step={}", pipeline.id, step, e)
                    pipeline.errors[step] = e.message
                    pipeline.stepStatuses[step] = PipelineStepStatus.FAILED

                    // If a dependency step fails, skip dependent steps
                    if (step == AiPipelineStep.STT) {
                        pipeline.steps.filter {
                            it == AiPipelineStep.ANALYZE_SCRIPT || it == AiPipelineStep.GENERATE_META
                        }.forEach { dependentStep ->
                            if (pipeline.stepStatuses[dependentStep] == PipelineStepStatus.PENDING) {
                                pipeline.stepStatuses[dependentStep] = PipelineStepStatus.SKIPPED
                                pipeline.errors[dependentStep] = "STT 실패로 인해 건너뜀"
                            }
                        }
                    }
                }
            }

            // Determine final status
            val hasFailures = pipeline.stepStatuses.values.any { it == PipelineStepStatus.FAILED }
            val hasCompleted = pipeline.stepStatuses.values.any { it == PipelineStepStatus.COMPLETED }

            pipeline.status = when {
                pipeline.status == PipelineStatus.CANCELLED -> PipelineStatus.CANCELLED
                hasFailures && !hasCompleted -> PipelineStatus.FAILED
                hasFailures && hasCompleted -> PipelineStatus.COMPLETED
                else -> PipelineStatus.COMPLETED
            }
        } catch (e: Exception) {
            log.error("파이프라인 실행 실패: pipelineId={}", pipeline.id, e)
            pipeline.status = PipelineStatus.FAILED
        } finally {
            pipeline.currentStep = null
            pipeline.completedAt = LocalDateTime.now()
        }
    }
}
