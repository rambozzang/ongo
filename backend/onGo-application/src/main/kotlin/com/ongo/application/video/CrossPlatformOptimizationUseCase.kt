package com.ongo.application.video

import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.video.dto.*
import com.ongo.common.enums.Platform
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CrossPlatformOptimizationUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun checkOptimization(request: OptimizationCheckRequest): OptimizationCheckResponse {
        val platforms = request.platforms.ifEmpty { Platform.entries }
        val results = platforms.map { platform ->
            checkPlatform(platform, request)
        }
        return OptimizationCheckResponse(results = results)
    }

    private fun checkPlatform(platform: Platform, request: OptimizationCheckRequest): OptimizationResult {
        val suggestions = mutableListOf<OptimizationSuggestion>()

        when (platform) {
            Platform.YOUTUBE -> checkYouTube(request, suggestions)
            Platform.TIKTOK -> checkTikTok(request, suggestions)
            Platform.INSTAGRAM -> checkInstagram(request, suggestions)
            Platform.NAVER_CLIP -> suggestions.add(
                OptimizationSuggestion(
                    "platform",
                    OptimizationSeverity.WARNING,
                    "네이버 클립은 공개 업로드 API가 없어 현재 최적화·게시를 지원하지 않습니다",
                    "지원되지 않음",
                    null,
                ),
            )
            Platform.TWITTER -> checkGenericShortForm(request, suggestions, "X (Twitter)", 280)
            Platform.FACEBOOK -> checkGenericLongForm(request, suggestions, "Facebook", 5000)
            Platform.THREADS -> checkGenericShortForm(request, suggestions, "Threads", 500)
            Platform.PINTEREST -> checkGenericShortForm(request, suggestions, "Pinterest", 500)
            Platform.LINKEDIN -> checkGenericLongForm(request, suggestions, "LinkedIn", 3000)
            Platform.WORDPRESS -> checkGenericLongForm(request, suggestions, "WordPress", 5000)
            Platform.TUMBLR -> checkGenericLongForm(request, suggestions, "Tumblr", 5000)
            Platform.VIMEO -> checkGenericLongForm(request, suggestions, "Vimeo", 5000)
            Platform.DAILYMOTION -> checkGenericLongForm(request, suggestions, "Dailymotion", 3000)
        }

        // Calculate score: start at 100, deduct points per issue
        var score = 100
        for (s in suggestions) {
            when (s.severity) {
                OptimizationSeverity.ERROR -> score -= 20
                OptimizationSeverity.WARNING -> score -= 10
                OptimizationSeverity.GOOD -> {} // no deduction
            }
        }
        score = score.coerceIn(0, 100)

        return OptimizationResult(
            platform = platform,
            score = score,
            suggestions = suggestions,
        )
    }

    private fun checkYouTube(req: OptimizationCheckRequest, suggestions: MutableList<OptimizationSuggestion>) {
        // Title checks
        when {
            req.title.length > 100 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.ERROR, "YouTube 제목은 100자 이내여야 합니다", "${req.title.length}자", "100자 이내")
            )
            req.title.length < 10 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.WARNING, "제목이 너무 짧습니다. 검색에 불리할 수 있습니다", "${req.title.length}자", "10-70자 권장")
            )
            req.title.length in 10..70 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.GOOD, "제목 길이가 적절합니다", "${req.title.length}자", null)
            )
        }

        // Description checks
        val descLen = req.description?.length ?: 0
        when {
            descLen > 5000 -> suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.ERROR, "YouTube 설명은 5,000자 이내여야 합니다", "${descLen}자", "5,000자 이내")
            )
            descLen < 50 -> suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.WARNING, "설명이 너무 짧습니다. SEO에 불리할 수 있습니다", "${descLen}자", "200자 이상 권장")
            )
        }

        // Tags checks
        when {
            req.tags.size < 3 -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "태그가 부족합니다. 3개 이상 추가하세요", "${req.tags.size}개", "3-15개 권장")
            )
            req.tags.size > 15 -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "태그가 너무 많습니다. 15개 이내로 줄이세요", "${req.tags.size}개", "3-15개 권장")
            )
            else -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.GOOD, "태그 수가 적절합니다", "${req.tags.size}개", null)
            )
        }

        // Thumbnail check
        if (req.thumbnailUrl.isNullOrBlank()) {
            suggestions.add(
                OptimizationSuggestion("thumbnail", OptimizationSeverity.WARNING, "커스텀 썸네일을 추가하면 CTR이 향상됩니다", "미설정", "커스텀 썸네일 권장")
            )
        } else {
            suggestions.add(
                OptimizationSuggestion("thumbnail", OptimizationSeverity.GOOD, "커스텀 썸네일이 설정되어 있습니다", null, null)
            )
        }
    }

    private fun checkTikTok(req: OptimizationCheckRequest, suggestions: MutableList<OptimizationSuggestion>) {
        // Title checks
        when {
            req.title.length > 150 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.ERROR, "TikTok 제목은 150자 이내여야 합니다", "${req.title.length}자", "150자 이내")
            )
            req.title.length in 1..150 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.GOOD, "제목 길이가 적절합니다", "${req.title.length}자", null)
            )
        }

        // Hashtag checks
        val hashtagCount = req.tags.size
        when {
            hashtagCount < 5 -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "해시태그를 5개 이상 추가하면 노출이 향상됩니다", "${hashtagCount}개", "5-8개 권장")
            )
            hashtagCount > 8 -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "해시태그가 너무 많습니다. 8개 이내로 줄이세요", "${hashtagCount}개", "5-8개 권장")
            )
            else -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.GOOD, "해시태그 수가 적절합니다", "${hashtagCount}개", null)
            )
        }

        // Link check in description
        if (req.description?.contains("http://") == true || req.description?.contains("https://") == true) {
            suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.WARNING, "TikTok 설명에 링크를 넣으면 노출이 감소할 수 있습니다", "링크 포함", "링크 제거 권장")
            )
        }
    }

    private fun checkInstagram(req: OptimizationCheckRequest, suggestions: MutableList<OptimizationSuggestion>) {
        // Caption length (description serves as caption)
        val captionLen = req.description?.length ?: 0
        when {
            captionLen > 2200 -> suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.ERROR, "Instagram 캡션은 2,200자 이내여야 합니다", "${captionLen}자", "2,200자 이내")
            )
            captionLen < 10 -> suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.WARNING, "캡션이 너무 짧습니다", "${captionLen}자", "짧은 후킹 문장 권장")
            )
        }

        // Hashtag limit
        when {
            req.tags.size > 30 -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.ERROR, "Instagram 해시태그는 30개 이내여야 합니다", "${req.tags.size}개", "30개 이내")
            )
            req.tags.size < 5 -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "해시태그를 5개 이상 추가하면 노출이 향상됩니다", "${req.tags.size}개", "10-20개 권장")
            )
            else -> suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.GOOD, "해시태그 수가 적절합니다", "${req.tags.size}개", null)
            )
        }

        // First line hook check
        val firstLine = req.description?.lines()?.firstOrNull()?.trim() ?: ""
        if (firstLine.length < 5) {
            suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.WARNING, "캡션 첫 줄에 눈에 띄는 후킹 문장을 추가하세요", "첫 줄: ${firstLine.take(20)}", "강력한 첫 줄 권장")
            )
        }
    }

    private fun checkGenericShortForm(
        req: OptimizationCheckRequest,
        suggestions: MutableList<OptimizationSuggestion>,
        platformName: String,
        maxTitleLength: Int,
    ) {
        when {
            req.title.length > maxTitleLength -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.ERROR, "$platformName 제목은 ${maxTitleLength}자 이내여야 합니다", "${req.title.length}자", "${maxTitleLength}자 이내")
            )
            req.title.length in 1..maxTitleLength -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.GOOD, "제목 길이가 적절합니다", "${req.title.length}자", null)
            )
        }

        if (req.tags.size < 3) {
            suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "해시태그를 3개 이상 추가하면 노출이 향상됩니다", "${req.tags.size}개", "3-10개 권장")
            )
        }
    }

    private fun checkGenericLongForm(
        req: OptimizationCheckRequest,
        suggestions: MutableList<OptimizationSuggestion>,
        platformName: String,
        maxDescLength: Int,
    ) {
        when {
            req.title.length > 200 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.ERROR, "$platformName 제목은 200자 이내여야 합니다", "${req.title.length}자", "200자 이내")
            )
            req.title.length < 10 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.WARNING, "제목이 너무 짧습니다", "${req.title.length}자", "10-100자 권장")
            )
            else -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.GOOD, "제목 길이가 적절합니다", "${req.title.length}자", null)
            )
        }

        val descLen = req.description?.length ?: 0
        if (descLen > maxDescLength) {
            suggestions.add(
                OptimizationSuggestion("description", OptimizationSeverity.ERROR, "$platformName 설명은 ${maxDescLength}자 이내여야 합니다", "${descLen}자", "${maxDescLength}자 이내")
            )
        }

        if (req.tags.size < 3) {
            suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "태그를 3개 이상 추가하면 검색 노출이 향상됩니다", "${req.tags.size}개", "3-10개 권장")
            )
        }
    }

    /**
     * AI를 활용하여 각 플랫폼별로 콘텐츠를 최적화합니다.
     */
    fun optimizeContent(userId: Long, request: AiOptimizationRequest): AiOptimizationResponse {
        val platforms = request.platforms.ifEmpty { Platform.entries }
        val optimized = platforms.associate { platform ->
            try {
                val content = optimizeForPlatform(userId, platform, request)
                platform to content
            } catch (e: Exception) {
                log.warn("AI 최적화 실패: platform={}, userId={}: {}", platform, userId, e.message)
                platform to AiOptimizedContent(
                    title = request.title,
                    description = request.description,
                    tags = request.tags,
                    reasoning = "AI 최적화 실패: ${e.message}",
                )
            }
        }
        return AiOptimizationResponse(original = request, optimized = optimized)
    }

    private fun optimizeForPlatform(
        userId: Long,
        platform: Platform,
        request: AiOptimizationRequest,
    ): AiOptimizedContent {
        val systemPrompt = buildSystemPrompt(platform)
        val userPrompt = buildUserPrompt(platform, request)

        val response = chatClientResolver.resolve(userId).prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .content()
            ?: throw IllegalStateException("AI 응답이 비어있습니다")

        return parseAiResponse(response)
    }

    private fun buildSystemPrompt(platform: Platform): String {
        return """
            당신은 ${platform.name} 플랫폼의 콘텐츠 최적화 전문가입니다.
            주어진 원본 콘텐츠를 ${platform.name}의 특성과 알고리즘에 맞게 최적화하세요.
            
            ${platform.name} 플랫폼 특성:
            ${getPlatformCharacteristics(platform)}
            
            응답은 다음 JSON 형식으로 반환하세요:
            {
                "title": "최적화된 제목",
                "description": "최적화된 설명 (없으면 null)",
                "tags": ["태그1", "태그2", "태그3"],
                "reasoning": "왜 이렇게 최적화했는지 간단한 설명"
            }
        """.trimIndent()
    }

    private fun getPlatformCharacteristics(platform: Platform): String {
        return when (platform) {
            Platform.YOUTUBE -> "- 제목: 10-70자, 키워드 앞 배치\n- 설명: 200자 이상, 타임스탬프와 링크 포함\n- 태그: 3-15개"
            Platform.TIKTOK -> "- 제목: 짧고 임팩트 있게, 해시태그 중심\n- 설명: 링크 제거, #해시태그 5-8개\n- 트렌디하고 젊은 어조"
            Platform.INSTAGRAM -> "- 캡션: 첫 줄에 후킹 문장, 이모지 활용\n- 해시태그: 10-20개, 다양한 규모 혼합\n- 시각적 중심"
            Platform.NAVER_CLIP -> "- 공개 업로드·분석 API가 없어 현재 지원하지 않음"
            Platform.TWITTER -> "- 제목: 280자 이내, 간결하게\n- 해시태그: 1-2개\n- 실시성과 참여 유도"
            else -> "- 제목: 간결하고 명확하게\n- 태그: 3-10개\n- 플랫폼 특성에 맞는 어조"
        }
    }

    private fun buildUserPrompt(platform: Platform, request: AiOptimizationRequest): String {
        return """
            플랫폼: ${platform.name}
            원본 제목: ${request.title}
            원본 설명: ${request.description ?: "(없음)"}
            원본 태그: ${request.tags.joinToString(", ")}
            
            위 콘텐츠를 ${platform.name}에 최적화해주세요.
        """.trimIndent()
    }

    private fun parseAiResponse(response: String): AiOptimizedContent {
        val json = response.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return objectMapper.readValue(json, AiOptimizedContent::class.java)
    }
}
