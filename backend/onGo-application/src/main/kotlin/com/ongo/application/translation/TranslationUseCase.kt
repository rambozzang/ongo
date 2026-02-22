package com.ongo.application.translation

import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.application.translation.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.domain.video.VideoRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TranslationUseCase(
    private val translationRepository: TranslationRepository,
    private val videoRepository: VideoRepository,
    private val creditService: CreditService,
    private val chatClientResolver: ChatClientResolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()

    companion object {
        const val CREDIT_PER_LANGUAGE = 3
        val SUPPORTED_LANGUAGES = listOf("ko", "en", "ja", "zh", "es", "fr", "de", "pt")
    }

    fun getTranslations(videoId: Long): List<TranslationResponse> =
        translationRepository.findByVideoId(videoId).map { it.toResponse() }

    @Transactional
    fun requestTranslation(userId: Long, videoId: Long, request: TranslateRequest): List<TranslationResponse> {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 영상에 대한 권한이 없습니다")

        val validLangs = request.languages.filter { it in SUPPORTED_LANGUAGES }
        if (validLangs.isEmpty()) throw BusinessException("INVALID_LANGUAGE", "지원 언어: $SUPPORTED_LANGUAGES")

        val totalCost = validLangs.size * CREDIT_PER_LANGUAGE
        creditService.validateAndDeduct(userId, totalCost, "TRANSLATION")

        return validLangs.map { lang ->
            val existing = translationRepository.findByVideoIdAndLanguage(videoId, lang)
            if (existing != null) {
                val existingId = existing.id!!
                translationRepository.update(existingId, status = "TRANSLATING", title = null, description = null, tags = null, subtitleContent = null)
                translateAsync(existingId, videoId, userId, lang, video.title, video.description)
                translationRepository.findById(existingId)!!.toResponse()
            } else {
                val saved = translationRepository.save(VideoTranslation(videoId = videoId, language = lang, status = "TRANSLATING"))
                translateAsync(saved.id!!, videoId, userId, lang, video.title, video.description)
                saved.toResponse()
            }
        }
    }

    private fun translateAsync(translationId: Long, videoId: Long, userId: Long, language: String, title: String, description: String?) {
        Thread.startVirtualThread {
            try {
                val langName = mapOf("ko" to "한국어", "en" to "English", "ja" to "日本語", "zh" to "中文", "es" to "Español", "fr" to "Français", "de" to "Deutsch", "pt" to "Português")[language] ?: language
                val prompt = buildString {
                    appendLine("다음 영상 메타데이터를 $langName($language)로 번역해주세요.")
                    appendLine("제목: $title")
                    if (!description.isNullOrBlank()) appendLine("설명: $description")
                    appendLine()
                    appendLine("JSON 형식으로 응답:")
                    appendLine("""{"title": "번역된 제목", "description": "번역된 설명"}""")
                }

                val response = chatClientResolver.resolve(userId).prompt()
                    .system("당신은 다국어 콘텐츠 번역 전문가입니다. 플랫폼에 최적화된 자연스러운 번역을 제공합니다.")
                    .user(prompt)
                    .call()
                    .content() ?: ""

                val jsonStart = response.indexOf("{")
                val jsonEnd = response.lastIndexOf("}") + 1
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val json = objectMapper.readTree(response.substring(jsonStart, jsonEnd))
                    translationRepository.update(
                        translationId,
                        title = json.get("title")?.asText(),
                        description = json.get("description")?.asText(),
                        tags = null, subtitleContent = null,
                        status = "COMPLETED",
                    )
                } else {
                    translationRepository.update(translationId, title = null, description = null, tags = null, subtitleContent = null, status = "FAILED")
                }
            } catch (e: Exception) {
                log.error("번역 실패: translationId={}", translationId, e)
                translationRepository.update(translationId, title = null, description = null, tags = null, subtitleContent = null, status = "FAILED")
            }
        }
    }

    @Transactional
    fun updateTranslation(userId: Long, translationId: Long, request: UpdateTranslationRequest): TranslationResponse {
        val translation = translationRepository.findById(translationId) ?: throw NotFoundException("번역", translationId)
        val video = videoRepository.findById(translation.videoId) ?: throw NotFoundException("영상", translation.videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 번역에 대한 권한이 없습니다")

        val tagsJson = request.tags?.let { objectMapper.writeValueAsString(it) }
        translationRepository.update(translationId, request.title, request.description, tagsJson, request.subtitleContent, null)
        return translationRepository.findById(translationId)!!.toResponse()
    }

    @Transactional
    fun deleteTranslation(userId: Long, translationId: Long) {
        val translation = translationRepository.findById(translationId) ?: throw NotFoundException("번역", translationId)
        val video = videoRepository.findById(translation.videoId) ?: throw NotFoundException("영상", translation.videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 번역에 대한 권한이 없습니다")
        translationRepository.delete(translationId)
    }

    private fun VideoTranslation.toResponse(): TranslationResponse {
        val tagList = try { objectMapper.readValue(tags ?: "[]", List::class.java) as List<String> } catch (_: Exception) { emptyList() }
        return TranslationResponse(
            id = id!!,
            videoId = videoId,
            language = language,
            title = title,
            description = description,
            tags = tagList,
            subtitleContent = subtitleContent,
            status = status,
            createdAt = createdAt?.toString(),
        )
    }
}
