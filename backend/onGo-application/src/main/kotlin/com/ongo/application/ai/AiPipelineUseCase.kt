package com.ongo.application.ai

import com.fasterxml.jackson.databind.JsonNode
import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.ai.result.ScriptAnalysisResult
import com.ongo.application.ai.result.SttResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineRepository
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.ai.PipelineStepStatus
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * AI pipeline orchestration.
 *
 * The database is the source of truth. The virtual thread is only an execution
 * mechanism; it may disappear during a deploy and the recovery tick will claim
 * the persisted PENDING/stale RUNNING job again. Individual steps are persisted
 * after every state transition, so completed steps are not repeated on recovery.
 */
@Service
class AiPipelineUseCase(
    private val sttUseCase: SttUseCase,
    private val analyzeScriptUseCase: AnalyzeScriptUseCase,
    private val generateMetaUseCase: GenerateMetaUseCase,
    private val generateHashtagsUseCase: GenerateHashtagsUseCase,
    private val suggestScheduleUseCase: SuggestScheduleUseCase,
    private val creditService: CreditService,
    private val videoRepository: VideoRepository,
    private val pipelineRepository: AiPipelineRepository,
) {

    private val log = LoggerFactory.getLogger(AiPipelineUseCase::class.java)
    private val runningPipelineIds = ConcurrentHashMap.newKeySet<String>()

    fun startPipeline(userId: Long, videoId: Long, stepNames: List<String>, channelId: Long?): AiPipeline {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근 권한이 없습니다")
        }

        val steps = stepNames.map { name ->
            try {
                AiPipelineStep.valueOf(name)
            } catch (_: IllegalArgumentException) {
                throw BusinessException("INVALID_STEP", "유효하지 않은 파이프라인 스텝: $name")
            }
        }

        if (steps.isEmpty()) {
            throw BusinessException("EMPTY_STEPS", "파이프라인 스텝을 1개 이상 선택해주세요")
        }

        val totalCost = AiPipelineStep.calculateTotalCost(steps)
        val discountApplied = steps.size >= AiPipelineStep.MIN_STEPS_FOR_DISCOUNT

        // Reserve credits before starting an asynchronous job. If durable job
        // creation fails, compensate immediately so a DB outage cannot consume
        // credits for a pipeline the user can never inspect.
        creditService.validateAndDeduct(userId, totalCost, "AI_PIPELINE")

        val pipeline = AiPipeline(
            id = UUID.randomUUID().toString(),
            userId = userId,
            videoId = videoId,
            channelId = channelId,
            steps = steps,
            totalCreditsCharged = totalCost,
            discountApplied = discountApplied,
        )

        try {
            pipelineRepository.save(pipeline)
        } catch (e: Exception) {
            runCatching { creditService.refundCredit(userId, totalCost, "AI_PIPELINE_PERSIST_FAILED") }
                .onFailure { refundError ->
                    log.error("AI pipeline 저장 실패 후 크레딧 환불도 실패했습니다: pipelineId={}", pipeline.id, refundError)
                }
            throw e
        }

        launch(pipeline.id)
        return pipeline
    }

    fun getPipelineStatus(userId: Long, pipelineId: String): AiPipeline {
        val pipeline = pipelineRepository.findById(pipelineId)
            ?: throw NotFoundException("파이프라인", pipelineId)

        if (pipeline.userId != userId) {
            throw ForbiddenException("해당 파이프라인에 접근 권한이 없습니다")
        }

        return pipeline
    }

    fun cancelPipeline(userId: Long, pipelineId: String): AiPipeline {
        val pipeline = getPipelineStatus(userId, pipelineId)

        if (pipeline.status == PipelineStatus.COMPLETED || pipeline.status == PipelineStatus.CANCELLED) {
            throw BusinessException("PIPELINE_ALREADY_DONE", "이미 완료되었거나 취소된 파이프라인입니다")
        }

        pipeline.status = PipelineStatus.CANCELLED
        pipeline.steps.forEach { step ->
            if (pipeline.stepStatuses[step] == PipelineStepStatus.PENDING) {
                pipeline.stepStatuses[step] = PipelineStepStatus.SKIPPED
            }
        }

        // A RUNNING step may already have called an external AI provider, so it
        // is treated as consumed. Only steps that never started are refunded.
        val executedSteps = pipeline.steps.filter {
            pipeline.stepStatuses[it] == PipelineStepStatus.COMPLETED ||
                pipeline.stepStatuses[it] == PipelineStepStatus.RUNNING
        }
        val executedCost = AiPipelineStep.calculateTotalCost(executedSteps)
        val refundAmount = pipeline.totalCreditsCharged - executedCost
        if (refundAmount > 0) {
            creditService.refundCredit(pipeline.userId, refundAmount, "AI_PIPELINE_CANCEL")
        }

        pipeline.currentStep = null
        pipeline.completedAt = LocalDateTime.now()
        pipelineRepository.save(pipeline)
        return pipeline
    }

    /** Recover jobs after restart and also acts as the liveness poller for stale workers. */
    @Scheduled(
        fixedDelayString = "\${ai.pipeline.recovery-delay-ms:15000}",
        initialDelayString = "\${ai.pipeline.recovery-initial-delay-ms:5000}",
    )
    fun recoverActivePipelines() {
        pipelineRepository.findActive(50).forEach { launch(it.id) }
    }

    private fun launch(pipelineId: String) {
        if (!runningPipelineIds.add(pipelineId)) return

        Thread.ofVirtual().name("ai-pipeline-$pipelineId").start {
            try {
                val now = LocalDateTime.now()
                val pipeline = pipelineRepository.claimForExecution(
                    id = pipelineId,
                    now = now,
                    staleBefore = now.minusMinutes(30),
                ) ?: return@start
                executePipeline(pipeline)
            } catch (e: Exception) {
                log.error("AI pipeline worker failed: pipelineId={}", pipelineId, e)
            } finally {
                runningPipelineIds.remove(pipelineId)
            }
        }
    }

    private fun executePipeline(pipeline: AiPipeline) {
        // A process can die after marking a step RUNNING but before its result is
        // stored. Re-run that step (at-least-once semantics) while preserving all
        // steps already marked COMPLETED.
        pipeline.stepStatuses.entries
            .filter { it.value == PipelineStepStatus.RUNNING }
            .forEach { it.setValue(PipelineStepStatus.PENDING) }
        pipeline.status = PipelineStatus.RUNNING
        pipeline.currentStep = null
        pipelineRepository.save(pipeline)

        var transcript = storedTranscript(pipeline.results[AiPipelineStep.STT])
        var analysisCategory = storedCategory(pipeline.results[AiPipelineStep.ANALYZE_SCRIPT])
        var generatedTitle = storedTitle(pipeline.results[AiPipelineStep.GENERATE_META])

        try {
            for (step in pipeline.steps) {
                val latest = pipelineRepository.findById(pipeline.id)
                if (latest?.status == PipelineStatus.CANCELLED) {
                    pipeline.status = PipelineStatus.CANCELLED
                    pipeline.currentStep = null
                    break
                }

                when (pipeline.stepStatuses[step]) {
                    PipelineStepStatus.COMPLETED,
                    PipelineStepStatus.FAILED,
                    PipelineStepStatus.SKIPPED,
                    -> continue
                    else -> Unit
                }

                pipeline.currentStep = step
                pipeline.stepStatuses[step] = PipelineStepStatus.RUNNING
                pipelineRepository.save(pipeline)

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
                                "대본 분석에는 STT 결과가 필요합니다. STT 스텝을 먼저 추가해주세요.",
                            )
                            val analysisResult = analyzeScriptUseCase.executeInternal(pipeline.userId, script)
                            analysisCategory = analysisResult.suggestedCategory
                            analysisResult
                        }

                        AiPipelineStep.GENERATE_META -> {
                            val script = transcript ?: throw BusinessException(
                                "PIPELINE_DEPENDENCY",
                                "메타데이터 생성에는 STT 결과가 필요합니다.",
                            )
                            val metaResult = generateMetaUseCase.executeInternal(
                                userId = pipeline.userId,
                                script = script,
                                targetPlatforms = listOf(Platform.YOUTUBE),
                                tone = "friendly",
                                category = analysisCategory ?: "엔터테인먼트",
                            )
                            generatedTitle = metaResult.platforms.firstOrNull()?.titleCandidates?.firstOrNull()
                            metaResult
                        }

                        AiPipelineStep.GENERATE_HASHTAGS -> generateHashtagsUseCase.executeInternal(
                            userId = pipeline.userId,
                            title = generatedTitle ?: "영상 제목",
                            category = analysisCategory ?: "엔터테인먼트",
                            targetPlatforms = listOf(Platform.YOUTUBE),
                        )

                        AiPipelineStep.SUGGEST_SCHEDULE -> pipeline.channelId?.let { channelId ->
                            suggestScheduleUseCase.executeInternal(pipeline.userId, channelId)
                        }
                    }

                    // ConcurrentHashMap intentionally contains no null values. A
                    // schedule suggestion without a channel is a valid no-op;
                    // mark it complete without inserting a null result.
                    if (result != null) pipeline.results[step] = result
                    pipeline.errors.remove(step)
                    pipeline.stepStatuses[step] = PipelineStepStatus.COMPLETED
                    pipelineRepository.save(pipeline)
                } catch (e: Exception) {
                    log.error("파이프라인 스텝 실패: pipelineId={}, step={}", pipeline.id, step, e)
                    pipeline.errors[step] = e.message ?: "AI 스텝 실행에 실패했습니다."
                    pipeline.stepStatuses[step] = PipelineStepStatus.FAILED

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
                    pipelineRepository.save(pipeline)
                }
            }

            if (pipeline.status != PipelineStatus.CANCELLED) {
                val hasFailures = pipeline.stepStatuses.values.any { it == PipelineStepStatus.FAILED }
                pipeline.status = if (hasFailures) PipelineStatus.FAILED else PipelineStatus.COMPLETED
            }
        } catch (e: Exception) {
            log.error("파이프라인 실행 실패: pipelineId={}", pipeline.id, e)
            pipeline.status = PipelineStatus.FAILED
        } finally {
            pipeline.currentStep = null
            pipeline.completedAt = LocalDateTime.now()
            runCatching { pipelineRepository.save(pipeline) }
                .onFailure { saveError ->
                    log.error("AI pipeline 최종 상태 저장 실패: pipelineId={}", pipeline.id, saveError)
                }
        }
    }

    private fun storedTranscript(value: Any?): String? = when (value) {
        is SttResult -> value.text
        is JsonNode -> value.path("text").asText(null)
        else -> null
    }?.takeIf(String::isNotBlank)

    private fun storedCategory(value: Any?): String? = when (value) {
        is ScriptAnalysisResult -> value.suggestedCategory
        is JsonNode -> value.path("suggestedCategory").asText(null)
        else -> null
    }?.takeIf(String::isNotBlank)

    private fun storedTitle(value: Any?): String? = when (value) {
        is MetaGenerationResult -> value.platforms.firstOrNull()?.titleCandidates?.firstOrNull()
        is JsonNode -> value.path("platforms").firstOrNull()?.path("titleCandidates")?.firstOrNull()?.asText(null)
        else -> null
    }?.takeIf(String::isNotBlank)
}
