package com.ongo.application.video

import com.ongo.application.video.dto.*
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 규칙 기반 최적화 검사([checkOptimization])는 그대로 제공하고, 요금이 정해지지 않은
 * AI 최적화([optimizeContent])는 막는다.
 *
 * `ChatClientResolver`/`ObjectMapper` 의존을 **생성자에서 제거했다.** 남겨 두면 다음 사람이
 * 가격 결정 없이 다시 배선할 수 있다.
 */
@Service
class CrossPlatformOptimizationUseCase {

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
     * **현재 비활성이다. 호출하면 항상 실패한다.**
     *
     * ## 왜 껐는가
     *
     * 이 메서드는 요금이 정해지지 않은 채 LLM 을 부르고 있었다. 생성자에 [CreditService]
     * 도 `AiRateLimiter` 도 없었고, `request.platforms` 가 비면 `Platform.entries` 전체로
     * 확장돼 **요청 한 건에 순차 13 회** 모델을 태웠다. `VIDEO_CREATE` 권한만 있으면
     * 누구나 무제한으로 0 크레딧에 그 비용을 발생시킬 수 있었다.
     *
     * 게다가 플랫폼별 실패를 잡아 **원본 title/description/tags 를 그대로 담아** 돌려줬다.
     * 호출자에게는 "최적화됨"과 "실패해서 원본 그대로"가 같은 성공 응답이었다.
     *
     * ## 왜 요금을 붙여서 살리지 않았는가
     *
     * 기존 [AiFeature] 중 이 작업과 값을 빌려올 만큼 동등한 것이 없다.
     *
     * - `META_GENERATION(5)` — 입력이 스크립트다. 신규 생성이지 기존 메타 최적화가 아니다.
     * - `CONTENT_REPURPOSE(10)` — 영상을 클립 재활용용으로 분석한다. 출력이 다르다.
     * - `META_REWRITE(3)` — **작업은 같다.** 기존 메타 → 플랫폼 맞춤 제안 + reasoning 이고
     *   출력 형태도 사실상 같다. 다만 그쪽은 전 플랫폼을 **한 프롬프트, LLM 1 회**로
     *   처리한다. 여기 구조에 3 크레딧을 붙이면 1 회분 가격으로 13 회를 태우게 된다.
     *
     * 없는 가격을 지어내는 대신 막았다. 되살리려면 둘 중 하나가 먼저 정해져야 한다.
     *
     * 1. 전용 [AiFeature] 항목과 크레딧 단가를 신설한다(제품 결정).
     * 2. `META_REWRITE` 처럼 **LLM 1 회로 전 플랫폼을 처리하도록 재작성**해 그 요금과의
     *    동등성을 근거로 만든다. 이 경우 프롬프트·파싱을 실제 모델로 검증해야 한다.
     *
     * ## 지금 잃는 것은 없다
     *
     * 프론트엔드는 이 엔드포인트를 부르지 않는다(`videos/optimize` 검색 결과 0 건).
     * 운영 코드 호출자도 없다. 같은 일을 하는 [com.ongo.application.metarewrite.MetaRewriteUseCase]
     * 가 이미 `META_REWRITE` 로 과금되며 화면에 연결돼 있다.
     *
     * LLM 을 부르지 않는 [checkOptimization] 은 그대로 살아 있다 — 그쪽은 규칙 기반이고
     * 프론트가 실제로 쓴다.
     */
    fun optimizeContent(userId: Long, request: AiOptimizationRequest): AiOptimizationResponse {
        log.warn(
            "AI 크로스 플랫폼 최적화는 요금 미정으로 비활성 상태다. userId={} platforms={}",
            userId, request.platforms.size,
        )
        throw BusinessException(
            "FEATURE_NOT_AVAILABLE",
            "AI 크로스 플랫폼 최적화는 현재 제공하지 않습니다. 영상 목록의 '메타데이터 리라이트'를 사용해 주세요.",
        )
    }
}
