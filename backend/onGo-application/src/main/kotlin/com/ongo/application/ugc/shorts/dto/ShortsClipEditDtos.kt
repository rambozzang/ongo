package com.ongo.application.ugc.shorts.dto

/**
 * 클립 편집 요청. 보낸 항목만 바꾼다(null = 그대로).
 *
 * `subtitles` 의 시각은 **다듬은 뒤** 클립 시작 기준이다. 구간과 자막을 같이 보내면 구간을 먼저
 * 적용하고 그 길이로 자막을 검사한다.
 *
 * `crop` 은 공유 형식 `{x, y, width, height, mode?, keyframes?}` 그대로이며 서버가
 * `ShortsCropPolicy` 로 정규화해 저장한다. 원본 해상도를 모르면 정규화가 틀리므로
 * 화면은 영상 요소의 `videoWidth/videoHeight` 를 `sourceWidth/sourceHeight` 로 함께 보낸다.
 */
data class ClipEditRequest(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val title: String? = null,
    val caption: String? = null,
    val subtitles: List<SubtitleLineEdit>? = null,
    val crop: Map<String, Any?>? = null,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    /** 플랫폼별 게시 메타 전체(보낸 목록으로 바꾼다). 한도를 넘으면 자르지 않고 거절한다. */
    val platformMeta: List<PlatformMetaEdit>? = null,
)

data class PlatformMetaEdit(
    val platform: String,
    val title: String = "",
    val description: String = "",
    val hashtags: List<String> = emptyList(),
)

data class SubtitleLineEdit(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** 편집기가 그리는 데 필요한 클립 원본 값. */
data class ClipEditorResponse(
    val clipId: Long,
    val runId: Long,
    val seq: Int,
    val startMs: Long,
    val endMs: Long,
    val title: String?,
    val caption: String?,
    val status: String,
    /** 클립 시작 기준. `hasWordTiming` 이 true 면 단어 강조로 렌더된다. */
    val subtitles: List<EditorSubtitleLine>,
    /** 정규화된 크롭 JSON. 없으면 null (렌더는 템플릿 배경으로 맞춤). */
    val cropJson: String?,
    /** 원본 길이. 모르면 null — 화면은 끝 경계를 검사하지 못한다는 뜻이다. */
    val sourceDurationMs: Long?,
    val editable: Boolean,
    /** 편집할 수 없는 이유(사람이 읽는 문장). 편집 가능하면 null. */
    val notEditableReason: String?,
    /** 렌더 결과가 이미 있는지. 있으면 화면 편집을 저장하는 순간 그 결과는 낡은 것이 된다. */
    val hasRenderedVideo: Boolean,
    val platformMeta: List<PlatformMetaResponse> = emptyList(),
)

data class EditorSubtitleLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val hasWordTiming: Boolean,
)

/** 편집 저장 결과. */
data class ClipEditResponse(
    val editor: ClipEditorResponse,
    /** 화면을 바꾸는 편집이었고, 이전 렌더 결과를 버렸는지. 다시 렌더해야 한다. */
    val needsRerender: Boolean,
    /** 구간을 늘려 자막이 없는 부분이 생겼는지. 자막은 지어내지 않는다. */
    val uncoveredSubtitleSpan: Boolean,
)

/** 플랫폼 하나의 게시 메타 (S3). */
data class PlatformMetaResponse(
    val platform: String,
    val title: String,
    val description: String,
    val hashtags: List<String>,
)
