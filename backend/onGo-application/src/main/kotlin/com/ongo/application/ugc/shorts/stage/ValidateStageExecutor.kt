package com.ongo.application.ugc.shorts.stage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.InputSanitizer
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.ClipStatus
import com.ongo.domain.ugc.shorts.ClipValidation
import com.ongo.domain.ugc.shorts.ClipValidationRepository
import com.ongo.domain.ugc.shorts.ClipValidationSeverity
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.domain.ugc.shorts.ShortsPromptRepository
import org.springframework.stereotype.Component

/**
 * VALIDATE 단계 (AI). 클립 검증 리포트(길이·자막 수·후킹 선택·렌더 스펙 여부)를 만들고
 * AI 총평을 붙여 스냅샷으로 남긴다.
 */
@Component
class ValidateStageExecutor(
    private val chatClientResolver: ChatClientResolver,
    private val shortsPromptRepository: ShortsPromptRepository,
    private val clipValidationRepository: ClipValidationRepository,
) : ShortsStageExecutor {

    override val stage: PipelineStage = PipelineStage.VALIDATE

    private val mapper = jacksonObjectMapper()

    override fun execute(context: ShortsStageContext): ShortsStageOutput {
        val targets = context.clips.filter { it.status != ClipStatus.DISCARDED }
        if (targets.isEmpty()) {
            throw BusinessException("SHORTS_RUN_INVALID_STATE", "검증할 클립이 없습니다")
        }

        // 결정론적 검증 리포트
        val report = targets.map { clip ->
            val durationMs = clip.endMs - clip.startMs
            val subtitleCount = countSubtitles(clip.subtitleJson)
            val hookSelected = context.hooks[clip.id]?.any { it.selected } == true
            val issues = buildList {
                if (durationMs <= 0) add("클립 구간이 올바르지 않습니다")
                if (subtitleCount == 0) add("자막이 없습니다")
                if (!hookSelected) add("후킹 문구가 선택되지 않았습니다")
                if (clip.renderSpec == null) add("렌더 스펙이 없습니다")
            }
            mapOf(
                "clipId" to clip.id,
                "clipSeq" to clip.seq,
                "durationMs" to durationMs,
                "subtitleCount" to subtitleCount,
                "hookSelected" to hookSelected,
                "hasRenderSpec" to (clip.renderSpec != null),
                "issues" to issues,
            )
        }

        val validations = targets.flatMap { clip ->
            val selectedHook = context.hooks[clip.id]
                ?.firstOrNull { it.selected }
                ?.text
                ?.takeIf { it.isNotBlank() }
            val subtitleTexts = subtitleTexts(clip.subtitleJson)
            val durationSeconds = (clip.endMs - clip.startMs) / 1000.0
            listOf(
                ClipValidation(
                    clipId = clip.id,
                    ruleCode = "HOOK_MISSING",
                    severity = ClipValidationSeverity.ERROR,
                    passed = selectedHook != null,
                    message = if (selectedHook == null) "선택된 후킹 문구가 없습니다" else null,
                ),
                ClipValidation(
                    clipId = clip.id,
                    ruleCode = "SUBTITLE_LINE_LENGTH",
                    severity = ClipValidationSeverity.WARNING,
                    passed = subtitleTexts.all { it.length in 5..9 },
                    message = subtitleTexts.firstOrNull { it.length !in 5..9 }
                        ?.let { "자막 한 줄 길이가 5~9자를 벗어났습니다: ${it.length}자" },
                ),
                ClipValidation(
                    clipId = clip.id,
                    ruleCode = "CLIP_DURATION",
                    severity = ClipValidationSeverity.WARNING,
                    passed = durationSeconds in 20.0..90.0,
                    message = if (durationSeconds !in 20.0..90.0) "클립 길이는 20~90초를 권장합니다" else null,
                ),
                ClipValidation(
                    clipId = clip.id,
                    ruleCode = "META_MISSING",
                    severity = ClipValidationSeverity.ERROR,
                    passed = !clip.title.isNullOrBlank() && !clip.caption.isNullOrBlank(),
                    message = if (clip.title.isNullOrBlank() || clip.caption.isNullOrBlank()) {
                        "제목 또는 캡션이 비어 있습니다"
                    } else {
                        null
                    },
                ),
            )
        }
        clipValidationRepository.saveAll(validations)

        val prompt = loadStagePrompt(shortsPromptRepository, context.workspaceId, stage)

        val userPrompt = buildString {
            append(prompt.userPrompt)
            append("\n\n검증 리포트:\n").append(InputSanitizer.sanitize(mapper.writeValueAsString(report)))
            append("\n\n응답은 {passed, summary} JSON으로만 해 줘.")
        }

        val verdict = chatClientResolver.resolve(context.userId).prompt()
            .system(prompt.systemPrompt ?: DEFAULT_SYSTEM)
            .user(userPrompt)
            .call()
            .entity(ValidateVerdictResult::class.java)
            ?: throw BusinessException("AI_PARSE_ERROR", "검증 응답을 파싱할 수 없습니다")

        val snapshot = mapper.writeValueAsString(
            mapOf(
                "clips" to report,
                "validations" to validations.map {
                    mapOf(
                        "clipId" to it.clipId,
                        "ruleCode" to it.ruleCode,
                        "severity" to it.severity.name,
                        "passed" to it.passed,
                        "message" to it.message,
                    )
                },
                "passed" to verdict.passed,
                "summary" to verdict.summary,
            ),
        )

        return ShortsStageOutput(
            outputSnapshot = snapshot,
            inputSnapshot = """{"clipCount":${targets.size}}""",
            promptId = prompt.recordableId(),
            promptRevision = prompt.revision,
        )
    }

    private fun countSubtitles(subtitleJson: String?): Int {
        if (subtitleJson.isNullOrBlank()) return 0
        return runCatching { mapper.readTree(subtitleJson).size() }.getOrDefault(0)
    }

    private fun subtitleTexts(subtitleJson: String?): List<String> {
        if (subtitleJson.isNullOrBlank()) return emptyList()
        return runCatching {
            mapper.readTree(subtitleJson).mapNotNull { node ->
                node.path("text").asText(null)?.takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val DEFAULT_SYSTEM =
            "당신은 쇼츠 게시 전 검증자입니다. 화면 깨짐, 소리 찢어짐, 자막 문단, 후킹 문구를 점검하고 총평을 내립니다."
    }
}
