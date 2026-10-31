package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.dto.PlatformMetaResponse
import com.ongo.application.video.PlatformCaptionRules
import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException

/**
 * 클립별 플랫폼 게시 메타(제목·설명·해시태그). `ugc_shorts_clips.platform_meta` 에 JSON 배열로 저장한다:
 * `[{ platform, title, description, hashtags: [..] }]`.
 *
 * ## 왜 AI 가 플랫폼마다 따로 쓰지 않나
 * 플랫폼 사이 차이는 대부분 **길이와 해시태그 쓰는 법**이다. 그 차이는 게시 흐름이 이미 쓰는 한도 표
 * ([PlatformUploadCapabilities])와 캡션 합성 규칙([PlatformCaptionRules])으로 정확히 맞출 수 있다.
 * 플랫폼마다 AI 를 다시 부르면 실행 한 번에 크레딧이 크게 늘고, 얻는 것은 같은 말의 다른 표현뿐이다.
 * 해시태그만 맥락 컷 응답에 함께 받고(추가 호출 없음), 사용자는 편집기에서 플랫폼별로 고친다.
 *
 * ## 한도는 게시가 거절하는 기준과 같다
 * 게시 흐름은 한도를 넘으면 그 대상을 실패시킨다. 그래서 맞추기([fit])는 그 한도를 **그대로** 쓴다 —
 * 여기서 통과한 값이 게시에서 거절되면 안 된다.
 */
object ShortsPlatformMeta {

    /** 쇼츠 기본 대상. 메타는 이 셋에 대해 미리 만든다. 다른 대상은 게시 때 같은 규칙으로 맞춘다. */
    val DEFAULT_PLATFORMS: List<Platform> = listOf(Platform.YOUTUBE, Platform.TIKTOK, Platform.INSTAGRAM)

    /** 해시태그는 적을수록 안전하다. 많이 달면 무시하거나 노출을 줄이는 플랫폼이 있다. */
    const val MAX_HASHTAGS = 5
    const val MAX_HASHTAG_LENGTH = 30

    /** 유튜브 태그 필드 글자 수 상한(쉼표 포함 근사). 넘기면 API 가 거절한다. */
    private const val YOUTUBE_TAGS_TOTAL_CHARS = 500

    private val mapper = jacksonObjectMapper()

    data class Entry(
        val platform: String,
        val title: String,
        val description: String,
        val hashtags: List<String>,
    ) {
        fun toResponse() = PlatformMetaResponse(platform, title, description, hashtags)
    }

    /** 맥락 컷 직후 기본 메타. 제목·설명은 클립 것을, 해시태그는 AI 제안을 플랫폼 한도에 맞춘다. */
    fun initial(title: String?, caption: String?, hashtags: List<String>): List<Entry> {
        val baseTitle = title?.trim().orEmpty()
        if (baseTitle.isEmpty() && caption.isNullOrBlank() && hashtags.isEmpty()) return emptyList()
        return DEFAULT_PLATFORMS.map { fit(it, baseTitle, caption.orEmpty(), hashtags) }
    }

    /**
     * 플랫폼 한도에 맞춘다. 제목 → 해시태그 → 설명 순으로 지킨다. 설명이 가장 길고 덜 중요하므로
     * 캡션 한도(제목+설명+해시태그를 합친 글)를 넘으면 설명 끝을 줄인다.
     */
    fun fit(platform: Platform, title: String, description: String, hashtags: List<String>): Entry {
        val capability = limitsOf(platform)
        val fittedTitle = title.trim().take(capability.maxTitleLength)
        var tags = normalizeHashtags(hashtags).take(minOf(MAX_HASHTAGS, capability.maxTagCount))
        if (platform == Platform.YOUTUBE) {
            while (tags.isNotEmpty() && tags.sumOf { it.length + 1 } > YOUTUBE_TAGS_TOTAL_CHARS) tags = tags.dropLast(1)
        }
        var desc = description.trim()
        if (capability.maxDescriptionLength > 0) desc = desc.take(capability.maxDescriptionLength)
        capability.maxCaptionLength?.let { limit ->
            val fixed = PlatformCaptionRules.compose(platform, fittedTitle, "", tags).orEmpty().length
            // 설명이 들어가면 구분자("\n\n")가 하나 더 붙는다.
            val room = (limit - fixed - SEPARATOR_LENGTH).coerceAtLeast(0)
            if (desc.length > room) desc = desc.take(room).trimEnd()
            // 그래도 넘치면 (제목·태그만으로 넘침) 태그를 줄인다.
            while (tags.isNotEmpty() && composedLength(platform, fittedTitle, desc, tags) > limit) tags = tags.dropLast(1)
        }
        return Entry(platform.name, fittedTitle, desc, tags)
    }

    /** `#` 제거, 공백 제거, 중복 제거(대소문자 무시), 너무 긴 것 제외. */
    fun normalizeHashtags(raw: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return raw.asSequence()
            .map { it.trim().trimStart('#').replace(WHITESPACE, "") }
            .filter { it.isNotEmpty() && it.length <= MAX_HASHTAG_LENGTH && it.none { c -> c in FORBIDDEN_CHARS } }
            .filter { seen.add(it.lowercase()) }
            .toList()
    }

    /**
     * 편집기가 보낸 플랫폼별 메타를 검증한다. 한도를 넘으면 **조용히 자르지 않고** 거절한다 —
     * 사용자가 쓴 글이 모르게 잘려 나가면 안 된다.
     */
    fun validateEdit(entries: List<Entry>): List<Entry> {
        val seen = mutableSetOf<String>()
        return entries.map { entry ->
            val platform = runCatching { Platform.valueOf(entry.platform.uppercase()) }.getOrNull()
                ?: invalid("지원하지 않는 플랫폼입니다: ${entry.platform}")
            if (!seen.add(platform.name)) invalid("${platform.name} 메타가 두 번 들어 있습니다")
            val capability = limitsOf(platform)
            val title = entry.title.trim()
            val description = entry.description.trim()
            val tags = normalizeHashtags(entry.hashtags)
            if (title.length > capability.maxTitleLength) invalid("${platform.name} 제목은 ${capability.maxTitleLength}자까지입니다")
            if (capability.maxDescriptionLength > 0 && description.length > capability.maxDescriptionLength) {
                invalid("${platform.name} 설명은 ${capability.maxDescriptionLength}자까지입니다")
            }
            if (tags.size > minOf(MAX_HASHTAGS, capability.maxTagCount)) {
                invalid("${platform.name} 해시태그는 ${minOf(MAX_HASHTAGS, capability.maxTagCount)}개까지입니다")
            }
            capability.maxCaptionLength?.let { limit ->
                if (composedLength(platform, title, description, tags) > limit) {
                    invalid("${platform.name} 게시글(제목+설명+해시태그)이 ${limit}자를 넘습니다")
                }
            }
            Entry(platform.name, title, description, tags)
        }
    }

    /**
     * 게시 대상 하나에 쓸 값. 그 플랫폼 메타가 있으면 그것을, 없으면 클립 제목·설명과 가진 해시태그로
     * 같은 규칙에 맞춰 만든다.
     *
     * 해시태그는 클립 메타 → 원본 영상 태그 순으로 찾는다. 둘 다 없으면 빈 목록이다 — 지어내지 않는다.
     */
    fun forTarget(
        platform: Platform,
        metaJson: String?,
        clipTitle: String?,
        clipCaption: String?,
        fallbackTitle: String,
        sourceVideoTags: List<String>,
    ): Entry {
        val entries = parse(metaJson)
        entries.firstOrNull { it.platform == platform.name }?.let { stored ->
            // 저장 뒤에 한도가 바뀌었을 수 있으니 한 번 더 맞춘다. 바뀌지 않았다면 그대로다.
            return fit(platform, stored.title.ifBlank { clipTitle ?: fallbackTitle }, stored.description, stored.hashtags)
        }
        val tags = entries.firstOrNull { it.hashtags.isNotEmpty() }?.hashtags ?: sourceVideoTags
        return fit(platform, clipTitle?.takeIf { it.isNotBlank() } ?: fallbackTitle, clipCaption.orEmpty(), tags)
    }

    fun parse(json: String?): List<Entry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            mapper.readTree(json).mapNotNull { node ->
                val platform = node.path("platform").asText("").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Entry(
                    platform = platform,
                    title = node.path("title").asText(""),
                    description = node.path("description").asText(""),
                    hashtags = node.path("hashtags").map { it.asText("") }.filter { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun toJson(entries: List<Entry>): String? =
        entries.takeIf { it.isNotEmpty() }?.let { mapper.writeValueAsString(it) }

    /** 게시 흐름과 같은 한도. 표에 없는 플랫폼은 해시태그 개수만 제한한다. */
    private data class Limits(
        val maxTitleLength: Int,
        val maxDescriptionLength: Int,
        val maxTagCount: Int,
        val maxCaptionLength: Int?,
    )

    private fun limitsOf(platform: Platform): Limits = PlatformUploadCapabilities.get(platform)
        ?.let { Limits(it.maxTitleLength, it.maxDescriptionLength, it.maxTagCount, it.maxCaptionLength) }
        ?: Limits(Int.MAX_VALUE, 0, MAX_HASHTAGS, null)

    private fun composedLength(platform: Platform, title: String, description: String, tags: List<String>): Int =
        PlatformCaptionRules.compose(platform, title, description, tags).orEmpty().length

    private fun invalid(message: String): Nothing = throw BusinessException("SHORTS_CLIP_EDIT_INVALID", message)

    private const val SEPARATOR_LENGTH = 2
    private val WHITESPACE = Regex("\\s+")
    private val FORBIDDEN_CHARS = setOf('#', ',', '<', '>')
}
