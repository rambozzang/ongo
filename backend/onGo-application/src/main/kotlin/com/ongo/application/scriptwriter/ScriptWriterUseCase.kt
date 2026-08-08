package com.ongo.application.scriptwriter

import com.ongo.application.scriptwriter.dto.*
import com.ongo.domain.scriptwriter.Script
import com.ongo.domain.scriptwriter.ScriptRepository
import com.ongo.domain.scriptwriter.ScriptSection
import com.ongo.domain.scriptwriter.ScriptSectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScriptWriterUseCase(
    private val scriptRepository: ScriptRepository,
    private val sectionRepository: ScriptSectionRepository,
) {

    fun getScripts(userId: Long, status: String? = null): List<ScriptResponse> {
        val scripts = if (status != null) scriptRepository.findByUserIdAndStatus(userId, status)
        else scriptRepository.findByUserId(userId)
        return scripts.map { toResponse(it) }
    }

    fun getScript(userId: Long, id: Long): ScriptResponse {
        val script = scriptRepository.findById(id)
            ?: throw IllegalArgumentException("스크립트를 찾을 수 없습니다")
        return toResponse(script)
    }

    @Transactional
    fun generateScript(userId: Long, request: GenerateScriptRequest): ScriptResponse {
        val script = scriptRepository.save(
            Script(
                userId = userId,
                title = request.topic,
                topic = request.topic,
                format = request.format,
                tone = request.tone,
                targetDuration = request.targetDuration,
                estimatedWordCount = (request.targetDuration * 2.5).toInt(),
                keywords = request.keywords ?: emptyList(),
                targetAudience = request.targetAudience,
                notes = request.additionalNotes,
                creditCost = 3,
            )
        )
        val sections = listOf(
            ScriptSection(scriptId = script.id, type = "HOOK", title = "오프닝 훅", content = "AI 생성 오프닝 문구", duration = 15, orderNumber = 1),
            ScriptSection(scriptId = script.id, type = "INTRO", title = "인트로", content = "AI 생성 인트로", duration = 30, orderNumber = 2),
            ScriptSection(scriptId = script.id, type = "BODY", title = "본문", content = "AI 생성 본문 내용", duration = request.targetDuration - 75, orderNumber = 3),
            ScriptSection(scriptId = script.id, type = "CTA", title = "콜투액션", content = "구독과 좋아요 부탁드립니다!", duration = 15, orderNumber = 4),
            ScriptSection(scriptId = script.id, type = "OUTRO", title = "아웃트로", content = "시청해 주셔서 감사합니다.", duration = 15, orderNumber = 5),
        )
        sections.forEach { sectionRepository.save(it) }
        return toResponse(script)
    }

    @Transactional
    fun updateScript(userId: Long, id: Long, request: UpdateScriptRequest): ScriptResponse {
        val script = scriptRepository.findById(id)
            ?: throw IllegalArgumentException("스크립트를 찾을 수 없습니다")
        val updated = scriptRepository.update(
            script.copy(
                title = request.title ?: script.title,
                status = request.status ?: script.status,
                notes = request.notes ?: script.notes,
            )
        )
        return toResponse(updated)
    }

    @Transactional
    fun deleteScript(userId: Long, id: Long) {
        sectionRepository.deleteByScriptId(id)
        scriptRepository.deleteById(id)
    }

    fun getSummary(userId: Long): ScriptSummaryResponse {
        val scripts = scriptRepository.findByUserId(userId)
        val drafts = scripts.count { it.status == "DRAFT" }
        val finals = scripts.count { it.status == "FINAL" }
        val totalCredits = scripts.sumOf { it.creditCost }
        val avgDuration = if (scripts.isNotEmpty()) scripts.map { it.targetDuration }.average().toInt() else 0
        val favoriteFormat = scripts.groupBy { it.format }.maxByOrNull { it.value.size }?.key ?: "LONG_FORM"
        return ScriptSummaryResponse(scripts.size, drafts, finals, totalCredits, avgDuration, favoriteFormat)
    }

    private fun toResponse(script: Script): ScriptResponse {
        val sections = sectionRepository.findByScriptId(script.id)
        return ScriptResponse(
            id = script.id, title = script.title, topic = script.topic,
            format = script.format, tone = script.tone, status = script.status,
            targetDuration = script.targetDuration, estimatedWordCount = script.estimatedWordCount,
            content = sections.map { ScriptSectionResponse(it.id, it.type, it.title, it.content, it.duration, it.notes, it.orderNumber) },
            keywords = script.keywords, targetAudience = script.targetAudience, notes = script.notes,
            creditCost = script.creditCost, createdAt = script.createdAt.toString(), updatedAt = script.updatedAt.toString(),
        )
    }
}
