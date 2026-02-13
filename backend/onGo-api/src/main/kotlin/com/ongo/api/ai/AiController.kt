package com.ongo.api.ai

import com.ongo.api.ai.dto.*
import com.ongo.application.ai.*
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.enums.Permission
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ai.AiPipeline
import com.ongo.domain.ai.AiPipelineStep
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "AI 도구", description = "AI 기반 콘텐츠 최적화 도구 (크레딧 소모)")
@RestController
@RequestMapping("/api/v1/ai")
class AiController(
    private val generateMetaUseCase: GenerateMetaUseCase,
    private val generateHashtagsUseCase: GenerateHashtagsUseCase,
    private val sttUseCase: SttUseCase,
    private val analyzeScriptUseCase: AnalyzeScriptUseCase,
    private val generateReplyUseCase: GenerateReplyUseCase,
    private val suggestScheduleUseCase: SuggestScheduleUseCase,
    private val generateIdeasUseCase: GenerateIdeasUseCase,
    private val generateReportUseCase: GenerateReportUseCase,
    private val aiPipelineUseCase: AiPipelineUseCase,
    private val weeklyDigestUseCase: WeeklyDigestUseCase,
    private val contentGapAnalysisUseCase: ContentGapAnalysisUseCase,
    private val aiBatchProcessingUseCase: AiBatchProcessingUseCase,
) {

    @Operation(
        summary = "AI 메타데이터 생성 (5크레딧)",
        description = "대본 기반으로 플랫폼별 제목/설명/해시태그를 자동 생성합니다. 대상 플랫폼, 톤, 카테고리를 지정할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "메타데이터 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (스크립트 누락 등)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/generate-meta")
    fun generateMeta(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: GenerateMetaRequest,
    ): ResponseEntity<ResData<GenerateMetaResponse>> {
        val script = request.script
            ?: throw BusinessException("SCRIPT_REQUIRED", "스크립트 또는 STT 옵션이 필요합니다.")

        val result = generateMetaUseCase.execute(
            userId = userId,
            script = script,
            targetPlatforms = request.targetPlatforms,
            tone = request.tone,
            category = request.category,
        )

        val response = GenerateMetaResponse(
            platforms = result.platforms.map {
                GenerateMetaResponse.PlatformMeta(
                    platform = it.platform,
                    titleCandidates = it.titleCandidates,
                    description = it.description,
                    hashtags = it.hashtags,
                )
            }
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "AI 해시태그 생성 (3크레딧)",
        description = "제목과 카테고리를 기반으로 플랫폼별 최적 해시태그를 추천합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "해시태그 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 파라미터 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/generate-hashtags")
    fun generateHashtags(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: GenerateHashtagsRequest,
    ): ResponseEntity<ResData<GenerateHashtagsResponse>> {
        val result = generateHashtagsUseCase.execute(
            userId = userId,
            title = request.title,
            category = request.category,
            targetPlatforms = request.targetPlatforms,
        )

        val response = GenerateHashtagsResponse(
            platforms = result.platforms.map {
                GenerateHashtagsResponse.PlatformHashtags(
                    platform = it.platform,
                    hashtags = it.hashtags,
                )
            }
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "음성 텍스트 변환 (10크레딧)",
        description = "영상의 음성을 텍스트로 변환합니다 (OpenAI Whisper 기반). 타임스탬프별 세그먼트가 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "음성 변환 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (비디오 ID 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/stt")
    fun stt(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: SttRequest,
    ): ResponseEntity<ResData<SttResponse>> {
        val result = sttUseCase.execute(userId = userId, videoId = request.videoId)

        val response = SttResponse(
            text = result.text,
            segments = result.segments.map {
                SttResponse.SttSegment(
                    startTime = it.startTime,
                    endTime = it.endTime,
                    text = it.text,
                )
            }
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "AI 대본 분석 (5크레딧)",
        description = "대본을 분석하여 키워드 추출, 타겟 오디언스 식별, 카테고리 추천 및 요약을 제공합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "대본 분석 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (대본 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/analyze-script")
    fun analyzeScript(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: AnalyzeScriptRequest,
    ): ResponseEntity<ResData<AnalyzeScriptResponse>> {
        val result = analyzeScriptUseCase.execute(userId = userId, script = request.script)

        val response = AnalyzeScriptResponse(
            keywords = result.keywords,
            targetAudience = result.targetAudience,
            suggestedCategory = result.suggestedCategory,
            summary = result.summary,
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "AI 댓글 답변 생성 (2크레딧)",
        description = "댓글에 대한 톤별(친근한, 전문적, 유머러스 등) 답변을 자동 생성합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "댓글 답변 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (댓글 내용 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/generate-reply")
    fun generateReply(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: GenerateReplyRequest,
    ): ResponseEntity<ResData<GenerateReplyResponse>> {
        val result = generateReplyUseCase.execute(
            userId = userId,
            comment = request.comment,
            channelTone = request.channelTone,
            context = request.context,
        )

        val response = GenerateReplyResponse(
            replies = result.replies.map {
                GenerateReplyResponse.ToneReply(
                    tone = it.tone,
                    reply = it.reply,
                )
            }
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "AI 업로드 시간 추천 (3크레딧)",
        description = "채널 분석 데이터를 기반으로 최적의 업로드 요일/시간대를 추천합니다. 예상 성과 향상률이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "업로드 시간 추천 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (채널 ID 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "채널을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/suggest-schedule")
    fun suggestSchedule(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: SuggestScheduleRequest,
    ): ResponseEntity<ResData<SuggestScheduleResponse>> {
        val result = suggestScheduleUseCase.execute(userId = userId, channelId = request.channelId)

        val response = SuggestScheduleResponse(
            suggestions = result.suggestions.map {
                SuggestScheduleResponse.ScheduleSuggestion(
                    dayOfWeek = it.dayOfWeek,
                    time = it.time,
                    reason = it.reason,
                    expectedBoost = it.expectedBoost,
                )
            }
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "AI 콘텐츠 아이디어 생성 (5크레딧)",
        description = "카테고리와 최근 업로드 영상 제목을 기반으로 새로운 콘텐츠 아이디어를 추천합니다. 예상 반응도와 난이도가 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "아이디어 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/generate-ideas")
    fun generateIdeas(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: GenerateIdeasRequest,
    ): ResponseEntity<ResData<GenerateIdeasResponse>> {
        val result = generateIdeasUseCase.execute(
            userId = userId,
            category = request.category,
            recentTitles = request.recentTitles,
        )

        val response = GenerateIdeasResponse(
            ideas = result.ideas.map {
                GenerateIdeasResponse.ContentIdea(
                    title = it.title,
                    description = it.description,
                    expectedReaction = it.expectedReaction,
                    difficulty = it.difficulty,
                )
            }
        )
        return ResData.success(response)
    }

    @Operation(
        summary = "AI 주간 리포트 생성 (8크레딧)",
        description = "채널 성과를 분석하여 하이라이트, 개선 방안, 다음 주 제안사항이 포함된 마크다운 리포트를 생성합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "리포트 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/generate-report")
    fun generateReport(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: GenerateReportRequest,
    ): ResponseEntity<ResData<GenerateReportResponse>> {
        val result = generateReportUseCase.execute(userId = userId, days = request.days)

        val response = GenerateReportResponse(
            reportMarkdown = result.reportMarkdown,
            highlights = result.highlights,
            improvements = result.improvements,
            nextWeekSuggestions = result.nextWeekSuggestions,
        )
        return ResData.success(response)
    }

    // ─── Weekly Digest endpoints ─────────────────────────────────

    @Operation(
        summary = "최신 주간 다이제스트 조회",
        description = "가장 최근에 생성된 AI 주간 다이제스트를 조회합니다. Pro/Business 플랜 전용입니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "다이제스트 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "다이제스트 없음"),
    )
    @GetMapping("/weekly-digest")
    fun getLatestWeeklyDigest(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ResData<com.ongo.application.ai.dto.WeeklyDigestResponse>> {
        val result = weeklyDigestUseCase.getLatestDigest(userId)
        return ResData.success(result)
    }

    @Operation(
        summary = "주간 다이제스트 목록 조회",
        description = "AI 주간 다이제스트 이력을 페이지네이션으로 조회합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "다이제스트 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping("/weekly-digests")
    fun listWeeklyDigests(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ResData<List<com.ongo.application.ai.dto.WeeklyDigestResponse>>> {
        val result = weeklyDigestUseCase.listDigests(userId, page, size)
        return ResData.success(result)
    }

    // ─── Content Gap Analysis endpoint ────────────────────────────

    @Operation(
        summary = "AI 콘텐츠 갭 분석 (10크레딧)",
        description = "사용자 콘텐츠와 경쟁자 데이터를 분석하여 미개척 주제 기회와 과포화 주제를 식별합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "콘텐츠 갭 분석 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/content-gap-analysis")
    fun contentGapAnalysis(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: ContentGapRequest,
    ): ResponseEntity<ResData<ContentGapResponse>> {
        val result = contentGapAnalysisUseCase.execute(
            userId = userId,
            includeCompetitors = request.includeCompetitors,
        )
        val response = ContentGapResponse(
            opportunities = result.opportunities.map {
                ContentOpportunity(
                    topic = it.topic,
                    estimatedDemand = it.estimatedDemand,
                    competitionLevel = it.competitionLevel,
                    suggestedAngle = it.suggestedAngle,
                    relevanceScore = it.relevanceScore,
                )
            },
            oversaturated = result.oversaturated.map {
                OversaturatedTopic(
                    topic = it.topic,
                    saturationLevel = it.saturationLevel,
                    recommendation = it.recommendation,
                )
            },
            analyzedAt = result.analyzedAt,
        )
        return ResData.success(response)
    }

    // ─── Batch Processing endpoints ────────────────────────────────

    @Operation(
        summary = "AI 배치 처리 시작",
        description = "여러 영상에 대해 메타데이터 생성, 해시태그 생성, STT 등을 일괄 처리합니다. 크레딧이 사전 검증됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "배치 처리 시작 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류")
    )
    @RequiresPermission(Permission.AI_USE)
    @PostMapping("/batch")
    fun startBatch(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: com.ongo.application.ai.dto.AiBatchRequest,
    ): ResponseEntity<ResData<com.ongo.application.ai.dto.AiBatchResponse>> {
        val result = aiBatchProcessingUseCase.startBatch(userId, request)
        return ResData.success(result)
    }

    @Operation(
        summary = "AI 배치 처리 상태 조회",
        description = "실행 중인 배치 처리의 진행 상태를 조회합니다. 각 영상별 처리 상태가 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "배치 상태 조회 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (배치 ID 없음)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @GetMapping("/batch/{batchId}")
    fun getBatchStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "배치 ID") @PathVariable batchId: String,
    ): ResponseEntity<ResData<com.ongo.application.ai.dto.AiBatchResponse>> {
        val result = aiBatchProcessingUseCase.getBatchStatus(batchId)
        return ResData.success(result)
    }

    // ─── AI Pipeline endpoints ────────────────────────────────────

    @Operation(
        summary = "AI 파이프라인 시작",
        description = "여러 AI 스텝을 자동 체인으로 실행합니다. 3개 이상 스텝 시 20% 할인이 적용됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "파이프라인 시작 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @PostMapping("/pipeline")
    fun startPipeline(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody request: AiPipelineRequest,
    ): ResponseEntity<ResData<AiPipelineResponse>> {
        val pipeline = aiPipelineUseCase.startPipeline(
            userId = userId,
            videoId = request.videoId,
            stepNames = request.steps,
            channelId = request.channelId,
        )
        return ResData.success(pipeline.toResponse())
    }

    @Operation(
        summary = "AI 파이프라인 상태 조회",
        description = "실행 중인 파이프라인의 진행 상태를 조회합니다."
    )
    @GetMapping("/pipeline/{id}")
    fun getPipelineStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: String,
    ): ResponseEntity<ResData<AiPipelineResponse>> {
        val pipeline = aiPipelineUseCase.getPipelineStatus(userId, id)
        return ResData.success(pipeline.toResponse())
    }

    @Operation(
        summary = "AI 파이프라인 취소",
        description = "실행 중인 파이프라인을 취소합니다. 미실행 스텝의 크레딧이 환불됩니다."
    )
    @DeleteMapping("/pipeline/{id}")
    fun cancelPipeline(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: String,
    ): ResponseEntity<ResData<AiPipelineResponse>> {
        val pipeline = aiPipelineUseCase.cancelPipeline(userId, id)
        return ResData.success(pipeline.toResponse(), "파이프라인이 취소되었습니다")
    }

    private fun AiPipeline.toResponse() = AiPipelineResponse(
        pipelineId = id,
        videoId = videoId,
        steps = steps.map { step ->
            PipelineStepStatusDto(
                step = step.name,
                displayName = step.displayName,
                creditCost = step.creditCost,
                status = (stepStatuses[step] ?: com.ongo.domain.ai.PipelineStepStatus.PENDING).name,
                result = results[step],
                error = errors[step],
            )
        },
        totalCredits = totalCreditsCharged,
        discountApplied = discountApplied,
        status = status.name,
        results = results.mapKeys { it.key.name },
    )
}
