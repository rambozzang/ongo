package com.ongo.application.video

import com.ongo.application.video.dto.*
import com.ongo.common.enums.Platform
import org.springframework.stereotype.Service

@Service
class CrossPlatformOptimizationUseCase {

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
            Platform.NAVER_CLIP -> checkNaverClip(request, suggestions)
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

    private fun checkNaverClip(req: OptimizationCheckRequest, suggestions: MutableList<OptimizationSuggestion>) {
        // Title checks
        when {
            req.title.length > 100 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.ERROR, "Naver Clip 제목은 100자 이내여야 합니다", "${req.title.length}자", "100자 이내")
            )
            req.title.length in 1..100 -> suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.GOOD, "제목 길이가 적절합니다", "${req.title.length}자", null)
            )
        }

        // Korean keyword check
        val hasKorean = req.title.any { it in '\uAC00'..'\uD7A3' } ||
            req.tags.any { tag -> tag.any { it in '\uAC00'..'\uD7A3' } }
        if (!hasKorean) {
            suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.WARNING, "네이버 클립에서는 한국어 키워드가 필수입니다", "한국어 없음", "한국어 키워드 추가 권장")
            )
        } else {
            suggestions.add(
                OptimizationSuggestion("title", OptimizationSeverity.GOOD, "한국어 키워드가 포함되어 있습니다", null, null)
            )
        }

        // Tags check
        if (req.tags.isEmpty()) {
            suggestions.add(
                OptimizationSuggestion("tags", OptimizationSeverity.WARNING, "태그를 추가하면 네이버 검색 노출이 향상됩니다", "0개", "5개 이상 권장")
            )
        }
    }
}
