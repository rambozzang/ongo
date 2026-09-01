package com.ongo.application.ai

import com.fasterxml.jackson.databind.JsonNode
import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.ai.result.ScriptAnalysisResult
import com.ongo.application.ai.result.SttResult
import com.ongo.application.credit.CreditAllocation
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineRepository
import com.ongo.domain.ai.AiPipelineSettlement
import com.ongo.domain.ai.AiPipelineStep
import com.ongo.domain.ai.PipelineCreditAllocation
import com.ongo.domain.ai.PipelineStatus
import com.ongo.domain.ai.PipelineStepStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
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
    transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(AiPipelineUseCase::class.java)
    private val runningPipelineIds = ConcurrentHashMap.newKeySet<String>()

    /**
     * 정산의 커밋 경계.
     *
     * 표식(`refunded_credits`)과 실제 환불이 **한 트랜잭션**이어야 한다. 표식을 먼저
     * 커밋하고 환불을 따로 부르면, 환불이 실패했을 때 표식 때문에 다시 시도할 수 없다 —
     * 크레딧은 사라지고 자동 복구 경로도 닫힌다.
     *
     * `REQUIRES_NEW` 인 이유: 실행 스레드의 `finally` 는 트랜잭션 밖이지만, 취소 요청은
     * 호출자의 트랜잭션 안일 수 있다. 그 트랜잭션이 뒤에 롤백되면 정산만 남아야 하는지
     * 사라져야 하는지가 모호해지므로 경계를 직접 잡는다.
     */
    private val settleTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

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

        /*
         * **중복 스텝은 거절한다.**
         *
         * 선차감은 [AiPipelineStep.calculateTotalCost] 가 리스트를 그대로 합산하므로 중복이
         * 두 번 계산되지만, 상태·결과·오류는 모두 스텝을 키로 하는 맵이라 중복이 하나로
         * 합쳐진다. 정산 배분도 스텝 단위라, 두 번 청구하고 한 번만 돌려주게 된다.
         *
         * 도메인 모델이 같은 스텝의 두 실행을 표현하지 못하는데 과금만 두 번 하는 것은
         * 어느 쪽으로도 옳지 않다. 계산을 맞추는 대신 입력을 막는다 — 같은 분석을 두 번
         * 요청할 이유도 없다.
         */
        val duplicated = steps.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicated.isNotEmpty()) {
            throw BusinessException(
                "DUPLICATE_STEP",
                "같은 스텝을 두 번 선택할 수 없습니다: ${duplicated.joinToString { it.displayName }}",
            )
        }

        val totalCost = AiPipelineStep.calculateTotalCost(steps)
        val discountApplied = steps.size >= AiPipelineStep.MIN_STEPS_FOR_DISCOUNT

        // Reserve credits before starting an asynchronous job. If durable job
        // creation fails, compensate immediately so a DB outage cannot consume
        // credits for a pipeline the user can never inspect.
        val allocation = creditService.validateAndDeduct(userId, totalCost, "AI_PIPELINE")

        val pipeline = AiPipeline(
            id = UUID.randomUUID().toString(),
            userId = userId,
            videoId = videoId,
            channelId = channelId,
            steps = steps,
            totalCreditsCharged = totalCost,
            /*
             * 차감 출처를 **행과 함께 저장한다.**
             *
             * 정산(취소·자연 실패·재시작 뒤 복구)은 다른 요청에서 실행되므로 이 인메모리
             * 영수증을 가질 수 없다. 저장해 두지 않으면 그때 환불이 출처를 모른 채 전액을
             * 무료분에 얹고, 구매분이 만료되는 무료분으로 바뀌거나 사라진다.
             */
            creditAllocation = PipelineCreditAllocation(
                freeAmount = allocation.freeAmount,
                purchasedAmounts = allocation.purchasedPortions.associate { it.purchasedCreditId to it.amount },
            ),
            discountApplied = discountApplied,
        )

        try {
            pipelineRepository.save(pipeline)
        } catch (e: Exception) {
            runCatching { creditService.refundAllocation(allocation) }
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
        // 스텝 상태를 먼저 남긴다. 아래 정산이 실패해도 어떤 스텝이 취소됐는지는 보인다.
        pipelineRepository.save(pipeline)

        settle(pipeline, PipelineStatus.CANCELLED, "AI_PIPELINE_CANCEL")
        return pipeline
    }

    /**
     * 미사용 스텝분을 돌려주고 파이프라인을 종료 상태로 확정한다.
     *
     * ## 왜 한 번만 일어나야 하는가
     *
     * 자연 실패 정산(실행 스레드)과 사용자의 취소가 같은 파이프라인을 동시에 끝낼 수 있다.
     * 예전에는 환불을 먼저 하고 상태를 나중에 저장해서, 저장이 실패하면 "이미 취소됨" 가드를
     * 다시 통과해 **두 번 환불**됐다.
     *
     * 이제 순서를 뒤집는다 — [AiPipelineRepository.settleRefund] 의 조건부 갱신
     * (`refunded_credits = 0`)이 먼저 승자를 정하고, **이긴 쪽만** 크레딧을 돌려준다.
     * 진 쪽은 아무것도 하지 않는다.
     *
     * 표식을 먼저 쓰기 때문에, 환불 호출이 실패하면 크레딧이 돌아가지 않은 채 정산됨으로
     * 남는다. 그건 조용히 넘길 수 없는 사고이므로 금액·사용자와 함께 error 로그를 남겨
     * 운영이 수기로 복구하게 한다. 반대 순서(환불 먼저)는 이중 환불을 만들고, 그쪽이
     * 훨씬 되돌리기 어렵다.
     */
    private fun settle(pipeline: AiPipeline, finalStatus: PipelineStatus, reason: String) {
        val refundAmount = AiPipelineSettlement.unusedAmount(
            steps = pipeline.steps,
            stepStatuses = pipeline.stepStatuses,
            totalCharged = pipeline.totalCreditsCharged,
        )
        val completedAt = LocalDateTime.now()
        pipeline.currentStep = null
        pipeline.completedAt = completedAt

        settleTx.execute {
            val won = pipelineRepository.settleRefund(
                id = pipeline.id,
                refundedCredits = refundAmount,
                status = finalStatus,
                completedAt = completedAt,
            )
            if (!won) {
                log.info("이미 정산된 파이프라인이라 환불하지 않는다: pipelineId={}", pipeline.id)
                return@execute
            }
            if (refundAmount <= 0) return@execute

            /*
             * **여기서 예외를 잡지 않는다.**
             *
             * 환불이 실패하면 같은 트랜잭션이 롤백되어 표식과 상태가 함께 사라진다. 그래야
             * 다음 복구 tick 이나 사용자의 재시도가 다시 정산할 수 있다. 여기서 삼키면
             * 표식만 남아 **자동 재시도가 영구히 막힌다** — 크레딧은 사라지고 복구 경로도
             * 닫힌다.
             *
             * 취소 요청은 이 예외를 그대로 받아 실패하고, 사용자가 다시 누르면 된다.
             * 실행 스레드의 자연 실패 정산은 호출부에서 로그로 감싸 다음 tick 에 맡긴다.
             */
            val snapshot = pipeline.creditAllocation
            if (snapshot == null) {
                /*
                 * **V108 이전에 만들어진 행이다. 자동 환불하지 않는다.**
                 *
                 * 출처를 모르는 채 전액을 무료분으로 돌려주면, 구매분에서 나간 크레딧이
                 * 월말에 사라지는 무료분이 되거나 free_monthly 한도에 걸려 증발한다.
                 * 그것이 이 스냅샷이 막으려는 손실이므로 여기서 다시 만들지 않는다.
                 *
                 * 상태는 확정한다(표식은 이미 위에서 이겼다). 파이프라인을 미정산으로
                 * 남겨 두면 복구 tick 이 영원히 같은 행을 다시 집는다.
                 *
                 * 대신 수기 정산에 필요한 값을 전부 남긴다. 이 로그가 유일한 복구 근거다.
                 */
                log.error(
                    "CRITICAL 수기 정산 필요: 차감 출처 분해가 없어 자동 환불을 하지 않았다. " +
                        "pipelineId={} userId={} refundAmount={} totalCharged={} reason={}",
                    pipeline.id, pipeline.userId, refundAmount, pipeline.totalCreditsCharged, reason,
                )
                return@execute
            }

            /*
             * 매 정산마다 스냅샷에서 새 영수증을 만든다. 인메모리 카운터는 재시작을
             * 견디지 못하므로 **중복 환불 방어는 위 settleRefund 의 조건부 갱신**이 한다.
             * 여기서는 그 판정에서 이긴 한 번만 실행된다.
             *
             * refundAmount 는 미사용 스텝분이라 총 차감액보다 작을 수 있다. 영수증에서
             * 그만큼만 떼어낸다.
             */
            val allocation = CreditAllocation.restored(
                userId = pipeline.userId,
                featureName = reason,
                freeAmount = snapshot.freeAmount,
                purchasedAmounts = snapshot.purchasedAmounts,
            )
            creditService.refundAllocation(allocation, refundAmount)
        }
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
            /*
             * **자연 실패도 환불한다.**
             *
             * 예전에는 취소를 눌러야만 미사용분이 돌아왔다. 스텝이 실패하거나 STT 실패로
             * 의존 스텝이 SKIPPED 된 채 끝나면, 외부 호출조차 없던 스텝의 크레딧이 그대로
             * 사라졌다. 어느 쪽으로 끝나든 돌려받는 금액이 같아야 한다.
             *
             * 취소 경로와 같은 [settle] 을 쓰므로 소비 판정(COMPLETED·RUNNING)도 같고,
             * 조건부 갱신이 이중 환불을 막는다.
             */
            runCatching { settle(pipeline, pipeline.status, "AI_PIPELINE_SETTLE") }
                .onFailure { settleError ->
                    /*
                     * 정산 트랜잭션이 롤백됐으므로 표식도 없다. 다음 복구 tick 이 이 잡을
                     * 다시 집어 정산한다 — 여기서 파이프라인 실행 자체를 실패시킬 이유는 없다.
                     */
                    log.error(
                        "AI pipeline 정산 실패. 다음 복구 실행에서 재시도한다. pipelineId={} userId={}",
                        pipeline.id, pipeline.userId, settleError,
                    )
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
