package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ugc.shorts.ShortsRenderSpecBuilder.SubtitleLine
import com.ongo.common.exception.BusinessException

/**
 * 클립 편집기의 규칙. 저장 형식은 계획서의 공유 형식을 따른다:
 * `subtitle_json = [{ startMs, endMs, text, words?: [{ startMs, endMs, text }] }]` — **클립 시작 기준 상대 ms**.
 *
 * 단어 시각(`words`)은 "이 글자를 이 순간에 말했다"는 측정값이다. 그래서
 * - 구간을 다듬으면 시각을 같은 양만큼 옮기고, 잘려 나간 단어는 뺀다.
 * - 줄 문구나 시각을 고치면 그 줄의 `words` 를 지운다. 고친 문구와 측정 시각이 어긋난 강조를
 *   그리느니 줄 단위로 보여 주는 편이 정확하다.
 */
object ShortsClipEditPolicy {

    const val MIN_CLIP_MS = ShortsSegmentPolicy.MIN_DURATION_MS
    const val MAX_CLIP_MS = ShortsSegmentPolicy.MAX_DURATION_MS
    const val MAX_TITLE_LENGTH = 300 // ugc_shorts_clips.title VARCHAR(300)
    const val MAX_CAPTION_LENGTH = 2_200 // 인스타그램 캡션 상한. 가장 짧은 대상을 넘지 않게 한다
    const val MAX_SUBTITLE_LINES = 300
    const val MAX_SUBTITLE_TEXT = 200
    /** 다듬기로 잘려 이보다 짧아진 자막 줄은 읽을 수 없으니 버린다. */
    const val MIN_LINE_MS = 200L

    private val mapper = jacksonObjectMapper()

    /** 사용자가 보낸 자막 한 줄. 시각은 (다듬은 뒤) 클립 시작 기준이다. */
    data class LineEdit(val startMs: Long, val endMs: Long, val text: String)

    fun validateRange(startMs: Long, endMs: Long, sourceDurationMs: Long?) {
        if (startMs < 0 || endMs <= startMs) invalid("구간의 시작은 0 이상, 끝은 시작보다 뒤여야 합니다")
        if (sourceDurationMs != null && endMs > sourceDurationMs) invalid("구간이 원본 영상 길이를 넘습니다")
        val length = endMs - startMs
        if (length !in MIN_CLIP_MS..MAX_CLIP_MS) {
            invalid("클립 길이는 ${MIN_CLIP_MS / 1000}~${MAX_CLIP_MS / 1000}초 사이여야 합니다")
        }
    }

    /**
     * 구간을 [oldStartMs] 에서 [newStartMs]~[newEndMs] 로 바꿨을 때 자막을 옮긴다.
     *
     * 새 구간 밖의 줄은 버리고, 경계에 걸린 줄은 잘라 낸다(그 줄의 단어 중 잘린 것은 뺀다).
     * 늘린 구간에는 자막이 없다 — 지어내지 않는다. 화면이 [hasUncoveredSpan] 으로 알린다.
     */
    fun shiftSubtitles(lines: List<SubtitleLine>, oldStartMs: Long, newStartMs: Long, newEndMs: Long): List<SubtitleLine> {
        val delta = oldStartMs - newStartMs
        val length = newEndMs - newStartMs
        return lines.mapNotNull { line ->
            val start = (line.startMs + delta).coerceAtLeast(0)
            val end = (line.endMs + delta).coerceAtMost(length)
            if (end - start < MIN_LINE_MS) return@mapNotNull null
            val words = line.words
                .map { it.copy(startMs = it.startMs + delta, endMs = it.endMs + delta) }
                .filter { it.startMs >= start && it.endMs <= end }
            SubtitleLine(start, end, line.text, words)
        }
    }

    /** 다듬은 구간 중 자막이 하나도 없는 앞·뒤 부분이 1초 이상인가. 늘렸을 때 사용자에게 알린다. */
    fun hasUncoveredSpan(lines: List<SubtitleLine>, clipLengthMs: Long): Boolean {
        if (lines.isEmpty()) return false // 원래 자막이 없던 클립은 알릴 것이 없다
        val head = lines.minOf { it.startMs }
        val tail = clipLengthMs - lines.maxOf { it.endMs }
        return head >= 1_000 || tail >= 1_000
    }

    /**
     * 사용자가 고친 자막 목록을 검증하고 저장할 줄로 바꾼다.
     *
     * 문구·시각이 기존 줄과 **완전히 같은** 줄만 단어 시각을 이어받는다.
     */
    fun applyLineEdits(edits: List<LineEdit>, current: List<SubtitleLine>, clipLengthMs: Long): List<SubtitleLine> {
        if (edits.size > MAX_SUBTITLE_LINES) invalid("자막은 ${MAX_SUBTITLE_LINES}줄까지 저장할 수 있습니다")
        var previousEnd = 0L
        val untouched = current.associateBy { Triple(it.startMs, it.endMs, it.text) }
        return edits.mapIndexed { index, edit ->
            val text = edit.text.trim()
            val number = index + 1
            if (text.isEmpty()) invalid("${number}번째 자막이 비어 있습니다. 지우려면 줄을 삭제해 주세요")
            if (text.length > MAX_SUBTITLE_TEXT) invalid("${number}번째 자막이 ${MAX_SUBTITLE_TEXT}자를 넘습니다")
            if (edit.startMs < 0 || edit.endMs <= edit.startMs || edit.endMs > clipLengthMs) {
                invalid("${number}번째 자막의 시각이 클립 범위를 벗어납니다")
            }
            if (edit.startMs < previousEnd) invalid("${number}번째 자막이 앞 줄과 겹칩니다")
            previousEnd = edit.endMs
            val kept = untouched[Triple(edit.startMs, edit.endMs, text)]
            SubtitleLine(edit.startMs, edit.endMs, text, kept?.words.orEmpty())
        }
    }

    fun validateText(title: String?, caption: String?) {
        if (title != null && title.length > MAX_TITLE_LENGTH) invalid("제목은 ${MAX_TITLE_LENGTH}자까지 쓸 수 있습니다")
        if (caption != null && caption.length > MAX_CAPTION_LENGTH) invalid("설명은 ${MAX_CAPTION_LENGTH}자까지 쓸 수 있습니다")
    }

    /** 공유 형식으로 직렬화한다. 단어가 없으면 `words` 키를 넣지 않는다. */
    fun toJson(lines: List<SubtitleLine>): String {
        val array = mapper.createArrayNode()
        lines.forEach { line ->
            array.addObject().apply {
                put("startMs", line.startMs)
                put("endMs", line.endMs)
                put("text", line.text)
                if (line.words.isNotEmpty()) {
                    putArray("words").apply {
                        line.words.forEach { w ->
                            addObject().apply {
                                put("startMs", w.startMs)
                                put("endMs", w.endMs)
                                put("text", w.text)
                            }
                        }
                    }
                }
            }
        }
        return mapper.writeValueAsString(array)
    }

    private fun invalid(message: String): Nothing = throw BusinessException("SHORTS_CLIP_EDIT_INVALID", message)
}
