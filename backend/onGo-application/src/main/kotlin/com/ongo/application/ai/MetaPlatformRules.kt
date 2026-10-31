package com.ongo.application.ai

import com.ongo.application.ai.result.MetaGenerationResult
import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.common.enums.Platform

/**
 * AI 메타 생성의 플랫폼 규칙. **게시 검증과 같은 한도표**([PlatformUploadCapabilities])에서 만든다.
 *
 * 예전에는 프롬프트에 YouTube·TikTok·Instagram 세 줄만 손으로 적혀 있었고(TikTok 은 150자로 틀렸다),
 * 해시태그는 모든 플랫폼에 30개였다. 한도를 넘긴 결과는 생성 때가 아니라 **게시 때 거절**됐다.
 * 이제 프롬프트도 같은 표를 보고, 생성 직후 한도 안으로 맞춘다.
 */
object MetaPlatformRules {

    /**
     * 플랫폼별 권장 해시태그 수. 한도가 아니라 **노출에 유리한 개수**다.
     * - YouTube: 해시태그가 60개를 넘으면 전부 무시되고, 제목 위에는 앞의 3개만 보인다
     * - Threads: 게시물당 주제 태그 1개
     * - 나머지: 3~5개가 일반적이다
     */
    private val RECOMMENDED_HASHTAGS = mapOf(
        Platform.YOUTUBE to 8,
        Platform.TIKTOK to 5,
        Platform.INSTAGRAM to 5,
        Platform.THREADS to 1,
        Platform.FACEBOOK to 3,
        Platform.LINKEDIN to 3,
        Platform.PINTEREST to 5,
        Platform.TWITTER to 2,
    )
    private const val DEFAULT_HASHTAGS = 5
    private const val MAX_HASHTAG_LENGTH = 30

    fun hashtagCount(platform: Platform): Int {
        val cap = PlatformUploadCapabilities.get(platform)?.maxTagCount?.takeIf { it > 0 } ?: Int.MAX_VALUE
        return minOf(RECOMMENDED_HASHTAGS[platform] ?: DEFAULT_HASHTAGS, cap)
    }

    fun titleLimit(platform: Platform): Int = PlatformUploadCapabilities.get(platform)?.maxTitleLength?.takeIf { it > 0 } ?: 100

    /** 설명 한도. 설명 칸이 따로 없는 플랫폼(캡션 하나)은 캡션 한도다. */
    fun descriptionLimit(platform: Platform): Int {
        val cap = PlatformUploadCapabilities.get(platform) ?: return 2_000
        return cap.maxDescriptionLength.takeIf { it > 0 } ?: cap.maxCaptionLength ?: cap.maxTitleLength
    }

    /** 프롬프트에 넣을 플랫폼 규칙. 대상 플랫폼만 적는다. */
    fun rulesText(platforms: List<Platform>): String = platforms.distinct().joinToString("\n") { p ->
        val captionOnly = (PlatformUploadCapabilities.get(p)?.maxDescriptionLength ?: 0) == 0
        "- ${p.name}: 제목 ${titleLimit(p)}자 이내, " +
            (if (captionOnly) "캡션(설명) ${descriptionLimit(p)}자 이내(제목과 같은 칸으로 게시될 수 있음)" else "설명 ${descriptionLimit(p)}자 이내") +
            ", 해시태그 ${hashtagCount(p)}개 이내"
    }

    /**
     * 생성 결과를 한도 안으로 맞춘다. AI 가 만든 글이라 잘라도 사용자 글을 훼손하지 않는다.
     * 제목·설명은 어절 경계에서 자르고, 해시태그는 `#`·공백 제거·중복 제거 뒤 개수를 맞춘다.
     * 대상에 없는 플랫폼 결과는 버린다.
     */
    fun fit(result: MetaGenerationResult, platforms: List<Platform>): MetaGenerationResult {
        val targets = platforms.associateBy { it.name }
        return MetaGenerationResult(
            platforms = result.platforms.mapNotNull { meta ->
                val platform = targets[meta.platform.trim().uppercase()] ?: return@mapNotNull null
                meta.copy(
                    platform = platform.name,
                    titleCandidates = meta.titleCandidates.map { cut(it.trim(), titleLimit(platform)) }.filter { it.isNotBlank() }.distinct(),
                    description = cut(meta.description.trim(), descriptionLimit(platform)),
                    hashtags = cleanHashtags(meta.hashtags).take(hashtagCount(platform)),
                )
            },
        )
    }

    fun cleanHashtags(tags: List<String>): List<String> =
        tags.map { it.trim().removePrefix("#").replace(Regex("\\s+"), "") }
            .filter { it.isNotEmpty() && it.length <= MAX_HASHTAG_LENGTH && it.none { c -> c in "#,.!?@" } }
            .distinctBy { it.lowercase() }

    private fun cut(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val head = text.take(limit)
        val space = head.lastIndexOf(' ')
        return (if (space >= limit / 2) head.substring(0, space) else head).trimEnd()
    }
}
