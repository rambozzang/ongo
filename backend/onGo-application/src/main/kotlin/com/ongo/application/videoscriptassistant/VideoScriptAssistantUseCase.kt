package com.ongo.application.videoscriptassistant

import com.ongo.application.videoscriptassistant.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.videoscriptassistant.ScriptSuggestionRepository
import com.ongo.domain.videoscriptassistant.VideoScript
import com.ongo.domain.videoscriptassistant.VideoScriptRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VideoScriptAssistantUseCase(
    private val scriptRepository: VideoScriptRepository,
    private val suggestionRepository: ScriptSuggestionRepository,
) {

    fun getScripts(userId: Long): List<VideoScriptResponse> {
        return scriptRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun generate(userId: Long, request: GenerateScriptRequest): VideoScriptResponse {
        val script = VideoScript(
            userId = userId,
            title = request.title,
            tone = request.tone,
            targetLength = request.targetLength,
            status = "GENERATING",
        )
        return scriptRepository.save(script).toResponse()
    }

    fun getSuggestions(scriptId: Long): List<ScriptSuggestionResponse> {
        return suggestionRepository.findByScriptId(scriptId).map { it.toSuggestionResponse() }
    }

    @Transactional
    fun applySuggestion(userId: Long, scriptId: Long, suggestionId: Long): VideoScriptResponse {
        suggestionRepository.updateApplied(suggestionId, true)
        val script = scriptRepository.findById(scriptId)
            ?: throw NotFoundException("스크립트", scriptId)
        return script.toResponse()
    }

    fun getSummary(userId: Long): VideoScriptAssistantSummaryResponse {
        val scripts = scriptRepository.findByUserId(userId)
        val completed = scripts.count { it.status == "COMPLETED" }
        val avgWords = if (scripts.isNotEmpty()) scripts.map { it.wordCount }.average().toInt() else 0
        val topTone = scripts.groupBy { it.tone }.maxByOrNull { it.value.size }?.key ?: ""
        return VideoScriptAssistantSummaryResponse(
            totalScripts = scripts.size,
            completedScripts = completed,
            avgWordCount = avgWords,
            topTone = topTone,
            suggestionsApplied = 0,
        )
    }

    private fun VideoScript.toResponse() = VideoScriptResponse(
        id = id!!,
        title = title,
        videoId = videoId,
        videoTitle = videoTitle,
        content = content,
        tone = tone,
        targetLength = targetLength,
        wordCount = wordCount,
        hookLine = hookLine,
        ctaText = ctaText,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun com.ongo.domain.videoscriptassistant.ScriptSuggestion.toSuggestionResponse() = ScriptSuggestionResponse(
        id = id!!,
        scriptId = scriptId,
        sectionType = sectionType,
        originalText = originalText,
        suggestedText = suggestedText,
        reason = reason,
        isApplied = isApplied,
        createdAt = createdAt,
    )
}
