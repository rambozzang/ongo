package com.ongo.application.videoseo

import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.common.enums.Platform

/**
 * 영상 메타데이터 SEO **규칙 점수**. 같은 입력이면 늘 같은 점수가 나온다.
 *
 * 예전에는 0~100 점수 네 개를 AI 가 매겼다. 모델은 검색 데이터도 플랫폼 내부 지표도 보지 않으므로
 * 그 숫자는 근거가 없었고, 같은 영상을 두 번 돌리면 다른 점수가 나왔다. 그런데 화면은 그것을
 * 원형 게이지로 "SEO 점수 72/100" 처럼 측정값처럼 보여 줬다.
 *
 * 이제 점수는 **우리가 실제 값으로 확인할 수 있는 항목**만으로 매긴다 — 제목 길이와 플랫폼 한도,
 * 태그가 제목·설명에 실제로 들어 있는지, 태그 수와 중복. 항목마다 통과 여부와 이유를 함께 돌려줘
 * 사용자가 "왜 이 점수인지"를 볼 수 있다. 이 규칙은 플랫폼 알고리즘이 아니라 onGo 의 점검표다.
 *
 * 플랫폼 한도는 게시 검증과 같은 표([PlatformUploadCapabilities])를 쓴다. 게시 대상이 여럿이면
 * 가장 엄격한 한도를 쓴다 — 한 곳이라도 넘으면 그 플랫폼 게시가 거절되기 때문이다.
 */
object SeoRubric {

    data class Check(
        val key: String,
        val label: String,
        val passed: Boolean,
        val points: Int,
        val maxPoints: Int,
        /** 사용자가 읽는 이유. 실제 값(글자 수·개수)을 담는다. */
        val detail: String,
    )

    data class Category(val key: String, val name: String, val checks: List<Check>) {
        val score: Int get() = checks.sumOf { it.points }
        val maxScore: Int get() = checks.sumOf { it.maxPoints }
    }

    data class Result(val categories: List<Category>) {
        val overallScore: Int get() = categories.sumOf { it.score }
        val failedChecks: List<Check> get() = categories.flatMap { it.checks }.filterNot { it.passed }
    }

    const val TITLE_MIN = 10
    const val TITLE_MAX = 60
    const val DESCRIPTION_MIN = 100
    const val DESCRIPTION_HEAD = 150
    const val TAGS_MIN = 3
    const val TAG_MAX_LENGTH = 30
    /** 게시 대상을 모를 때의 태그 개수 상한. */
    const val DEFAULT_TAG_LIMIT = 15

    fun evaluate(
        title: String,
        description: String?,
        tags: List<String>,
        category: String?,
        platforms: List<Platform>,
    ): Result {
        val cleanTitle = title.trim()
        val desc = description?.trim().orEmpty()
        val cleanTags = tags.map { it.trim().trimStart('#') }.filter { it.isNotEmpty() }
        val capabilities = platforms.mapNotNull { PlatformUploadCapabilities.get(it) }
        val titleLimit = capabilities.minOfOrNull { it.maxTitleLength }
        val tagLimit = capabilities.filter { it.maxTagCount > 0 }.minOfOrNull { it.maxTagCount } ?: DEFAULT_TAG_LIMIT

        val titleHasTag = cleanTags.any { contains(cleanTitle, it) }
        val keywords = (cleanTags + words(cleanTitle)).distinctBy { normalize(it) }
        val headHasKeyword = keywords.any { contains(desc.take(DESCRIPTION_HEAD), it) }
        val titleWordsInDesc = words(cleanTitle).any { contains(desc, it) }
        val duplicateTags = cleanTags.size - cleanTags.distinctBy { normalize(it) }.size
        val longTags = cleanTags.count { it.length > TAG_MAX_LENGTH }

        val titleChecks = listOf(
            check(
                "titleWithinLimit", "제목이 플랫폼 한도 안", 10,
                passed = cleanTitle.isNotEmpty() && (titleLimit == null || cleanTitle.length <= titleLimit),
                detail = when {
                    cleanTitle.isEmpty() -> "제목이 비어 있습니다"
                    titleLimit == null -> "${cleanTitle.length}자 (게시 대상이 없어 한도는 보지 않았습니다)"
                    else -> "${cleanTitle.length}자 / 한도 ${titleLimit}자"
                },
            ),
            check(
                "titleLength", "제목 ${TITLE_MIN}~${TITLE_MAX}자", 5,
                passed = cleanTitle.length in TITLE_MIN..TITLE_MAX,
                detail = "${cleanTitle.length}자",
            ),
            check(
                "titleHasTag", "태그 키워드가 제목에 들어 있음", 10,
                passed = titleHasTag,
                detail = if (cleanTags.isEmpty()) "태그가 없습니다" else if (titleHasTag) "포함" else "태그 ${cleanTags.size}개 중 제목에 든 것이 없습니다",
            ),
        )
        val descriptionChecks = listOf(
            check("descriptionPresent", "설명이 있음", 5, desc.isNotEmpty(), if (desc.isEmpty()) "설명이 비어 있습니다" else "${desc.length}자"),
            check("descriptionLength", "설명 ${DESCRIPTION_MIN}자 이상", 10, desc.length >= DESCRIPTION_MIN, "${desc.length}자"),
            check(
                "descriptionHeadKeyword", "설명 앞 ${DESCRIPTION_HEAD}자 안에 제목·태그 키워드", 10,
                passed = headHasKeyword,
                detail = if (headHasKeyword) "포함" else "앞 ${DESCRIPTION_HEAD}자에 제목·태그 키워드가 없습니다",
            ),
        )
        val tagChecks = listOf(
            Check(
                "tagCount", "태그 ${TAGS_MIN}개 이상",
                passed = cleanTags.size >= TAGS_MIN,
                points = when {
                    cleanTags.size >= TAGS_MIN -> 10
                    cleanTags.isNotEmpty() -> 5
                    else -> 0
                },
                maxPoints = 10,
                detail = "${cleanTags.size}개",
            ),
            check("tagWithinLimit", "태그 개수가 플랫폼 한도 안", 5, cleanTags.size <= tagLimit, "${cleanTags.size}개 / 한도 ${tagLimit}개"),
            check("tagNoDuplicate", "중복 태그 없음", 5, duplicateTags == 0, if (duplicateTags == 0) "중복 없음" else "중복 ${duplicateTags}개"),
            check("tagLength", "태그마다 ${TAG_MAX_LENGTH}자 이하", 5, longTags == 0, if (longTags == 0) "모두 ${TAG_MAX_LENGTH}자 이하" else "${longTags}개가 ${TAG_MAX_LENGTH}자 초과"),
        )
        val generalChecks = listOf(
            check("categorySet", "카테고리 지정", 10, !category.isNullOrBlank(), category?.takeIf { it.isNotBlank() } ?: "미분류"),
            check("hasTarget", "게시 대상 플랫폼 있음", 5, platforms.isNotEmpty(), if (platforms.isEmpty()) "없음" else platforms.joinToString(", ") { it.name }),
            check(
                "titleWordsInDescription", "제목 단어가 설명에도 나옴", 10,
                passed = titleWordsInDesc,
                detail = if (titleWordsInDesc) "포함" else "제목 단어가 설명에 없습니다",
            ),
        )
        return Result(
            listOf(
                Category("title", "제목", titleChecks),
                Category("description", "설명", descriptionChecks),
                Category("tags", "태그", tagChecks),
                Category("general", "전반", generalChecks),
            ),
        )
    }

    private fun check(key: String, label: String, maxPoints: Int, passed: Boolean, detail: String) =
        Check(key, label, passed, if (passed) maxPoints else 0, maxPoints, detail)

    /** 두 글자 이상 단어. 한국어 조사까지 자르지는 않는다 — 부분 일치로 본다. */
    private fun words(text: String): List<String> =
        text.split(Regex("[\\s\\p{Punct}·…「」『』【】]+")).map { it.trim() }.filter { it.length >= 2 }

    private fun normalize(text: String) = text.lowercase().replace(Regex("\\s+"), "")

    private fun contains(haystack: String, needle: String): Boolean {
        val n = normalize(needle)
        return n.isNotEmpty() && normalize(haystack).contains(n)
    }
}
