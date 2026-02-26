package com.ongo.application.contenttranslator

import com.ongo.application.contenttranslator.dto.*
import com.ongo.domain.contenttranslator.TranslationGlossary
import com.ongo.domain.contenttranslator.TranslationGlossaryRepository
import com.ongo.domain.contenttranslator.TranslationJob
import com.ongo.domain.contenttranslator.TranslationJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ContentTranslatorUseCase(
    private val jobRepository: TranslationJobRepository,
    private val glossaryRepository: TranslationGlossaryRepository,
) {

    fun getJobs(userId: Long): List<TranslationJobResponse> {
        return jobRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun translate(userId: Long, request: TranslateRequest): TranslationJobResponse {
        val job = TranslationJob(
            userId = userId,
            videoId = request.videoId,
            videoTitle = "",
            sourceLanguage = "ko",
            targetLanguage = request.targetLanguage,
            contentType = request.contentType,
            originalText = "",
            status = "TRANSLATING",
        )
        return jobRepository.save(job).toResponse()
    }

    fun getGlossary(userId: Long): List<TranslationGlossaryResponse> {
        return glossaryRepository.findByUserId(userId).map { it.toGlossaryResponse() }
    }

    @Transactional
    fun addGlossaryTerm(userId: Long, request: AddGlossaryTermRequest): TranslationGlossaryResponse {
        val term = TranslationGlossary(
            userId = userId,
            sourceWord = request.sourceWord,
            targetWord = request.targetWord,
            sourceLanguage = request.sourceLanguage,
            targetLanguage = request.targetLanguage,
        )
        return glossaryRepository.save(term).toGlossaryResponse()
    }

    fun getSummary(userId: Long): ContentTranslatorSummaryResponse {
        val jobs = jobRepository.findByUserId(userId)
        val completed = jobs.count { it.status == "COMPLETED" }
        val avgQuality = jobs.mapNotNull { it.quality }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
        val topPair = jobs.groupBy { "${it.sourceLanguage}→${it.targetLanguage}" }
            .maxByOrNull { it.value.size }?.key ?: ""
        val glossarySize = glossaryRepository.findByUserId(userId).size
        return ContentTranslatorSummaryResponse(
            totalTranslations = jobs.size,
            completedTranslations = completed,
            avgQuality = avgQuality,
            topLanguagePair = topPair,
            glossarySize = glossarySize,
        )
    }

    private fun TranslationJob.toResponse() = TranslationJobResponse(
        id = id!!,
        videoId = videoId,
        videoTitle = videoTitle,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        contentType = contentType,
        originalText = originalText,
        translatedText = translatedText,
        quality = quality,
        status = status,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun TranslationGlossary.toGlossaryResponse() = TranslationGlossaryResponse(
        id = id!!,
        sourceWord = sourceWord,
        targetWord = targetWord,
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        context = context,
        createdAt = createdAt,
    )
}
