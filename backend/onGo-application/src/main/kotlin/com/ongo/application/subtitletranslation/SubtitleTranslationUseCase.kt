package com.ongo.application.subtitletranslation

import com.ongo.application.subtitletranslation.dto.*
import com.ongo.domain.subtitletranslation.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SubtitleTranslationUseCase(
    private val translationRepository: SubtitleTranslationRepository,
    private val lineRepository: TranslationLineRepository,
    private val languageRepository: SupportedLanguageRepository,
) {

    fun getTranslations(workspaceId: Long, status: String? = null): List<SubtitleTranslationResponse> {
        val list = if (status != null) translationRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else translationRepository.findByWorkspaceId(workspaceId)
        return list.map { toTranslationResponse(it) }
    }

    fun getTranslation(id: Long): SubtitleTranslationResponse? {
        return translationRepository.findById(id)?.let { toTranslationResponse(it) }
    }

    @Transactional
    fun createTranslation(workspaceId: Long, request: CreateTranslationRequest): SubtitleTranslationResponse {
        val saved = translationRepository.save(
            SubtitleTranslation(
                workspaceId = workspaceId,
                videoTitle = request.videoTitle,
                sourceLanguage = request.sourceLanguage,
                targetLanguage = request.targetLanguage,
            )
        )
        // AI 번역 시작 연동 포인트 — Phase 1에서는 PENDING 상태 반환
        return toTranslationResponse(saved)
    }

    fun getLines(translationId: Long): List<TranslationLineResponse> {
        return lineRepository.findByTranslationId(translationId).map { toLineResponse(it) }
    }

    @Transactional
    fun updateLine(translationId: Long, lineId: Long, request: UpdateTranslationLineRequest): TranslationLineResponse {
        val line = lineRepository.findById(lineId) ?: throw IllegalArgumentException("번역 라인을 찾을 수 없습니다")
        val updated = lineRepository.update(line.copy(translatedText = request.translatedText, isEdited = true))
        return toLineResponse(updated)
    }

    fun getSupportedLanguages(): List<SupportedLanguageResponse> {
        return languageRepository.findAll().map { SupportedLanguageResponse(it.code, it.name, it.nativeName) }
    }

    fun getSummary(workspaceId: Long): SubtitleTranslationSummaryResponse {
        val all = translationRepository.findByWorkspaceId(workspaceId)
        val completed = all.count { it.status == "COMPLETED" }
        val inProgress = all.count { it.status in listOf("TRANSLATING", "REVIEWING") }
        val totalCredits = all.sumOf { it.cost }
        val completedItems = all.filter { it.status == "COMPLETED" }
        val avgQuality = if (completedItems.isNotEmpty()) completedItems.map { it.quality.toDouble() }.average() else 0.0
        return SubtitleTranslationSummaryResponse(all.size, completed, inProgress, totalCredits, avgQuality)
    }

    private fun toTranslationResponse(t: SubtitleTranslation) = SubtitleTranslationResponse(
        id = t.id, videoTitle = t.videoTitle, sourceLanguage = t.sourceLanguage,
        targetLanguage = t.targetLanguage, status = t.status, progress = t.progress,
        sourceLineCount = t.sourceLineCount, translatedLineCount = t.translatedLineCount,
        quality = t.quality, cost = t.cost, createdAt = t.createdAt.toString(),
        completedAt = t.completedAt?.toString(),
    )

    private fun toLineResponse(l: TranslationLine) = TranslationLineResponse(
        id = l.id, translationId = l.translationId, lineNumber = l.lineNumber,
        startTime = l.startTime, endTime = l.endTime, sourceText = l.sourceText,
        translatedText = l.translatedText, isEdited = l.isEdited,
    )
}
