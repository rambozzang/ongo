package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.domain.ugc.shorts.ClipHook
import com.ongo.domain.ugc.shorts.RenderFraming
import com.ongo.domain.ugc.shorts.ShortsFilterGraph
import com.ongo.domain.ugc.shorts.ShortsClip
import com.ongo.domain.ugc.shorts.ShortsTemplate
import org.springframework.stereotype.Component

/**
 * 설계 4장의 렌더 산출물 3종(render-spec.json / clip-{seq}.ass / render.sh)을 만든다.
 * 실제 인코딩은 하지 않고 지시서만 생성한다.
 */
@Component
class ShortsRenderSpecBuilder {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * 자막 한 줄. [words] 는 STT 단어 시각(클립 기준 ms)이며 있으면 단어 강조에 쓴다.
     * 사용자가 줄 문구를 고치면 편집기가 그 줄의 [words] 를 비운다(문구와 시각이 어긋나므로).
     */
    data class SubtitleLine(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val words: List<CaptionWord> = emptyList(),
    )

    /** render-spec.json 의 메모리 모델. JSON 키 구조는 설계 4장을 따른다. */
    data class RenderSpec(
        val clipSeq: Int,
        val sourceVideoId: Long,
        val sourceFileUrl: String?,
        val startMs: Long,
        val endMs: Long,
        val crop: CropSpec?,
        val hookText: String?,
        val hookPosition: String,
        val subtitles: List<SubtitleLine>,
        val templateId: Long?,
        val backgroundStyle: String?,
        val captionFontFamily: String?,
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    /** 클립·선택된 후킹·템플릿으로 렌더 스펙을 만든다. */
    fun buildSpec(
        clip: ShortsClip,
        sourceVideoId: Long,
        sourceFileUrl: String?,
        hook: ClipHook?,
        template: ShortsTemplate?,
    ): RenderSpec = RenderSpec(
        clipSeq = clip.seq,
        sourceVideoId = sourceVideoId,
        sourceFileUrl = sourceFileUrl,
        startMs = clip.startMs,
        endMs = clip.endMs,
        crop = clip.cropJson?.let { parseCrop(it) },
        hookText = hook?.text,
        hookPosition = template?.hookPosition ?: "TOP",
        subtitles = parseSubtitles(clip.subtitleJson),
        templateId = template?.id,
        backgroundStyle = template?.backgroundStyle,
        captionFontFamily = template?.captionFontFamily,
    )

    /** 설계 4장 형식의 render-spec.json 문자열로 직렬화한다. */
    fun toJson(spec: RenderSpec): String {
        val root = mapper.createObjectNode()
        root.put("clipSeq", spec.clipSeq)
        root.putObject("source").apply {
            put("videoId", spec.sourceVideoId)
            put("fileUrl", spec.sourceFileUrl)
        }
        root.putObject("cut").apply {
            put("startMs", spec.startMs)
            put("endMs", spec.endMs)
        }
        root.putObject("reframe").apply {
            put("targetWidth", 1080)
            put("targetHeight", 1920)
            spec.crop?.let { crop -> set<JsonNode>("crop", mapper.readTree(ShortsCropPolicy.toJson(crop))) }
        }
        root.putObject("hook").apply {
            put("text", spec.hookText)
            put("position", spec.hookPosition)
        }
        root.putArray("subtitles").apply {
            spec.subtitles.forEach { line ->
                addObject().apply {
                    put("startMs", line.startMs)
                    put("endMs", line.endMs)
                    put("text", line.text)
                    if (line.words.isNotEmpty()) putWords(this, line.words)
                }
            }
        }
        root.putObject("template").apply {
            put("id", spec.templateId)
            put("backgroundStyle", spec.backgroundStyle)
            put("captionFontFamily", spec.captionFontFamily)
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    /** render-spec.json 문자열을 모델로 되돌린다 (번들 재생성용). */
    fun parseSpec(json: String): RenderSpec {
        val root = mapper.readTree(json)
        val cropNode = root.path("reframe").path("crop")
        return RenderSpec(
            clipSeq = root.path("clipSeq").asInt(),
            sourceVideoId = root.path("source").path("videoId").asLong(),
            sourceFileUrl = root.path("source").path("fileUrl").asText(null),
            startMs = root.path("cut").path("startMs").asLong(),
            endMs = root.path("cut").path("endMs").asLong(),
            crop = if (cropNode.isMissingNode || cropNode.isNull) null else ShortsCropPolicy.parse(cropNode.toString()),
            hookText = root.path("hook").path("text").asText(null),
            hookPosition = root.path("hook").path("position").asText("TOP"),
            subtitles = root.path("subtitles").map { readLine(it) },
            templateId = root.path("template").path("id").takeIf { it.isNumber }?.asLong(),
            backgroundStyle = root.path("template").path("backgroundStyle").asText(null),
            captionFontFamily = root.path("template").path("captionFontFamily").asText(null),
        )
    }

    /**
     * clip-{seq}.ass 자막 파일.
     *
     * - 자막은 [KoreanCaptionLayout] 으로 짧은 묶음(최대 두 줄)으로 다시 배치한다
     * - 단어 시각이 있으면 **지금 말하는 단어를 강조**한다(스타일 프리셋: [CaptionStyle])
     * - 후킹 문구는 처음 [HOOK_EMPHASIS_MS] 동안만 크게 띄운다 — 끝까지 남으면 자막과 화면을 가린다
     * - 위치: 위(8)·가운데(5)·아래(2). 아래는 플랫폼 버튼·설명에 가리지 않도록 세이프 영역 위로 올린다
     */
    fun buildAss(spec: RenderSpec, template: ShortsTemplate?): String {
        val style = CaptionStyle.of(template?.extraSpec)
        val highlight = assColor(CaptionStyle.highlightColorOf(template?.extraSpec), "&H0000E1FF")
        val captionFont = template?.captionFontFamily ?: spec.captionFontFamily ?: DEFAULT_FONT
        val captionSize = template?.captionFontSize ?: style.fontSize
        val captionColor = assColor(template?.captionFontColor, "&H00FFFFFF")
        val captionStroke = assColor(template?.captionStrokeColor, "&H00000000")
        val captionPosition = template?.captionPosition ?: "BOTTOM"
        val captionMarginV = marginFor(captionPosition, template, CAPTION_BOTTOM_MARGIN)

        val hookFont = template?.hookFontFamily ?: captionFont
        val hookSize = template?.hookFontSize ?: 80
        val hookColor = assColor(template?.hookFontColor, "&H0000FFFF")
        val hookStroke = assColor(template?.hookStrokeColor, "&H00000000")
        val hookMarginV = marginFor(spec.hookPosition, template, HOOK_TOP_MARGIN)

        val boxed = style == CaptionStyle.BOX
        val sb = StringBuilder()
        sb.appendLine("[Script Info]")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("PlayResX: 1080")
        sb.appendLine("PlayResY: 1920")
        sb.appendLine("WrapStyle: 2")
        sb.appendLine()
        sb.appendLine("[V4+ Styles]")
        sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        sb.appendLine(
            "Style: Caption,$captionFont,$captionSize,$captionColor,$captionColor," +
                (if (boxed) "&H70000000,&H70000000" else "$captionStroke,$captionStroke") +
                ",-1,0,0,0,100,100,0,0,${if (boxed) 3 else 1},${if (boxed) 14 else style.outline},${if (boxed) 0 else 2}," +
                "${positionAlignment(captionPosition)},60,60,$captionMarginV,1",
        )
        sb.appendLine("Style: Hook,$hookFont,$hookSize,$hookColor,$hookColor,$hookStroke,$hookStroke,-1,0,0,0,100,100,0,0,1,6,2,${positionAlignment(spec.hookPosition)},60,60,$hookMarginV,1")
        sb.appendLine()
        sb.appendLine("[Events]")
        sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        captionCards(spec).forEach { card ->
            if (!card.timed || card.words.size < 2) {
                sb.appendLine(dialogue(0, card.startMs, card.endMs, "Caption", cardText(card, null, style, highlight)))
            } else {
                // 단어마다 한 줄: 그 단어가 시작할 때부터 다음 단어가 시작할 때까지 그 단어를 강조한다.
                val words = card.words
                words.forEachIndexed { i, word ->
                    val from = if (i == 0) card.startMs else word.startMs
                    val to = words.getOrNull(i + 1)?.startMs ?: card.endMs
                    if (to > from) sb.appendLine(dialogue(0, from, to, "Caption", cardText(card, word, style, highlight)))
                }
            }
        }
        spec.hookText?.takeIf { it.isNotBlank() }?.let { hook ->
            val end = minOf(HOOK_EMPHASIS_MS, spec.durationMs)
            // 살짝 커지며 나타나고 흐려지며 사라진다. 첫 1초 안에 시선을 잡는 용도다.
            val text = "{\\fad(120,250)\\fscx85\\fscy85\\t(0,160,\\fscx100\\fscy100)}${assText(hook)}"
            sb.appendLine(dialogue(1, 0, end, "Hook", text))
        }
        return sb.toString()
    }

    /** 자막 줄들을 화면 묶음으로 다시 배치한다. 단어 시각이 있는 줄과 없는 줄이 섞여도 각각 처리한다. */
    fun captionCards(spec: RenderSpec): List<CaptionCard> {
        val duration = spec.durationMs
        return spec.subtitles.flatMap { line ->
            if (line.words.isNotEmpty()) {
                KoreanCaptionLayout.fromWords(line.words, duration)
            } else if (line.text.contains('\n')) {
                // 사용자가 직접 줄을 나눈 자막은 그대로 존중한다.
                val lines = line.text.split(Regex("\r?\n")).filter { it.isNotBlank() }
                    .map { listOf(CaptionWord(line.startMs, line.endMs, it.trim())) }
                listOf(CaptionCard(line.startMs, line.endMs.coerceAtMost(duration), lines, timed = false))
                    .filter { it.endMs > it.startMs && it.lines.isNotEmpty() }
            } else {
                KoreanCaptionLayout.fromLines(listOf(line.startMs..line.endMs to line.text), duration)
            }
        }
    }

    private fun cardText(card: CaptionCard, active: CaptionWord?, style: CaptionStyle, highlight: String): String =
        card.lines.joinToString("\\N") { line ->
            line.joinToString(" ") { word ->
                val text = assText(word.text)
                if (word === active) "{${style.activeTags(highlight)}}$text{\\r}" else text
            }
        }

    private fun dialogue(layer: Int, startMs: Long, endMs: Long, style: String, text: String): String =
        "Dialogue: $layer,${assTime(startMs)},${assTime(endMs)},$style,,0,0,0,,$text"

    /** 위·아래 위치일 때 세이프 영역을 지킨다. 가운데는 여백이 의미 없다. */
    private fun marginFor(position: String, template: ShortsTemplate?, default: Int): Int = when (position.uppercase()) {
        "TOP" -> maxOf(default, template?.safeAreaTop ?: 0)
        "CENTER" -> 0
        else -> maxOf(default, template?.safeAreaBottom ?: 0)
    }

    /**
     * render.sh — 서버 렌더러와 **같은 필터**([ShortsFilterGraph])로 만든다. 크롭이 없어도
     * 화면을 늘이지 않고 템플릿 배경 스타일로 맞춘다.
     */
    fun buildRenderScript(spec: RenderSpec): String {
        val filters = ShortsFilterGraph.build(
            framing = framingOf(spec),
            width = ShortsCropPolicy.TARGET_WIDTH,
            height = ShortsCropPolicy.TARGET_HEIGHT,
            subtitleFile = "clip-${spec.clipSeq}.ass",
        )
        return """
            |#!/bin/bash
            |ffmpeg -ss ${seconds(spec.startMs)} -to ${seconds(spec.endMs)} -i "${spec.sourceFileUrl ?: "source.mp4"}" \
            |  -vf '$filters' \
            |  -c:v libx264 -preset medium -crf 20 -c:a aac -b:a 128k \
            |  "clip-${spec.clipSeq}.mp4"
            |
        """.trimMargin()
    }

    /** 렌더에 쓸 화면 구성. 정규화는 [ShortsCropPolicy] 만 한다. */
    fun framingOf(spec: RenderSpec): RenderFraming = ShortsCropPolicy.toFraming(spec.crop, spec.backgroundStyle)

    /** 클립의 subtitle_json 배열 문자열을 파싱한다. */
    fun parseSubtitles(subtitleJson: String?): List<SubtitleLine> {
        if (subtitleJson.isNullOrBlank()) return emptyList()
        return runCatching { mapper.readTree(subtitleJson).map { readLine(it) } }.getOrDefault(emptyList())
    }

    /** 크롭 JSON 문자열을 읽는다. 깨졌으면 null. */
    fun parseCrop(cropJson: String): CropSpec? = ShortsCropPolicy.parse(cropJson)

    /** #RRGGBB → ASS &H00BBGGRR 변환. 형식이 다류면 기본값을 쓴다. */
    private fun assColor(color: String?, fallback: String): String {
        val hex = color?.removePrefix("#") ?: return fallback
        if (hex.length != 6) return fallback
        val r = hex.substring(0, 2)
        val g = hex.substring(2, 4)
        val b = hex.substring(4, 6)
        return "&H00$b$g$r".uppercase()
    }

    /** ASS 정렬: 하단=2, 가운데=5, 상단=8. */
    private fun positionAlignment(position: String): Int = when (position.uppercase()) {
        "TOP" -> 8
        "CENTER" -> 5
        else -> 2
    }

    private fun readLine(node: JsonNode): SubtitleLine {
        val start = node.path("startMs").asLong()
        val end = node.path("endMs").asLong()
        val words = node.path("words").takeIf { it.isArray }?.mapNotNull { w ->
            val text = w.path("text").asText("")
            if (!w.path("startMs").isNumber || !w.path("endMs").isNumber || text.isBlank()) null
            else CaptionWord(w.path("startMs").asLong(), w.path("endMs").asLong(), text)
        }.orEmpty()
        return SubtitleLine(start, end, node.path("text").asText(), words)
    }

    private fun putWords(line: com.fasterxml.jackson.databind.node.ObjectNode, words: List<CaptionWord>) {
        line.putArray("words").apply {
            words.forEach { w ->
                addObject().apply {
                    put("startMs", w.startMs)
                    put("endMs", w.endMs)
                    put("text", w.text)
                }
            }
        }
    }

    /** ASS 텍스트 이스케이프: 줄바꿈은 \N, 중괄호는 제거. */
    private fun assText(text: String): String =
        text.replace("{", "").replace("}", "").replace("\r\n", "\\N").replace("\n", "\\N")

    /** ms → ASS 시간 형식 h:mm:ss.cc (센티초). */
    private fun assTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3_600_000
        val m = (total / 60_000) % 60
        val s = (total / 1_000) % 60
        val cs = (total % 1_000) / 10
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }

    /** ms → ffmpeg -ss/-to 용 초 문자열. */
    private fun seconds(ms: Long): String = "%.3f".format(java.util.Locale.ROOT, ms / 1000.0)

    companion object {
        /** 서버 렌더러가 함께 싣는 글꼴(SIL OFL). 서버에 한글 글꼴이 없어도 글자가 네모로 깨지지 않는다. */
        const val DEFAULT_FONT = "Pretendard"
        const val HOOK_EMPHASIS_MS = 3_500L
        /** 쇼츠·릴스·틱톡은 아래 약 20% 를 설명·버튼이 덮는다. 자막을 그 위로 올린다. */
        const val CAPTION_BOTTOM_MARGIN = 420
        const val HOOK_TOP_MARGIN = 220
    }
}

/**
 * 자막 스타일 프리셋. 템플릿 `extraSpec` 의 `captionStyle` 로 고른다(없으면 [POP]).
 * - [CLEAN] 흰 글씨·검은 외곽선, 말하는 단어만 강조색
 * - [POP] 굵고 큰 글씨, 말하는 단어가 강조색으로 살짝 커진다(요즘 쇼츠에서 가장 흔하다)
 * - [BOX] 반투명 검은 상자 위 글씨(예능 자막), 말하는 단어 강조색
 */
enum class CaptionStyle(val fontSize: Int, val outline: Int) {
    CLEAN(70, 5),
    POP(84, 7),
    BOX(66, 0);

    fun activeTags(color: String): String = when (this) {
        POP -> "\\c$color&\\fscx112\\fscy112"
        CLEAN, BOX -> "\\c$color&"
    }

    companion object {
        private val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        fun of(extraSpec: String?): CaptionStyle {
            val raw = extraSpec?.let { runCatching { mapper.readTree(it).path("captionStyle").asText(null) }.getOrNull() }
            return entries.firstOrNull { it.name == raw?.uppercase() } ?: POP
        }

        fun highlightColorOf(extraSpec: String?): String? = extraSpec?.let {
            runCatching { mapper.readTree(it).path("highlightColor").asText(null) }.getOrNull()
        }?.takeIf { HEX_COLOR.matches(it) }

        /**
         * 템플릿 `extraSpec` 에 스타일·강조색을 적는다. 다른 키는 보존한다. null 은 "바꾸지 않음",
         * 강조색 빈 문자열은 "기본색으로 되돌림"이다. 알 수 없는 값은 조용히 무시하지 않고 거절한다.
         */
        fun writeTo(extraSpec: String?, style: String?, highlightColor: String?): String? {
            if (style == null && highlightColor == null) return extraSpec
            val root = extraSpec?.let { runCatching { mapper.readTree(it) }.getOrNull() }
                ?.takeIf { it.isObject } as? com.fasterxml.jackson.databind.node.ObjectNode
                ?: mapper.createObjectNode()
            style?.let { raw ->
                val parsed = entries.firstOrNull { it.name == raw.uppercase() }
                    ?: throw com.ongo.common.exception.BusinessException("SHORTS_TEMPLATE_INVALID", "알 수 없는 자막 스타일입니다: $raw")
                root.put("captionStyle", parsed.name)
            }
            highlightColor?.let { color ->
                when {
                    color.isBlank() -> root.remove("highlightColor")
                    HEX_COLOR.matches(color) -> root.put("highlightColor", color.uppercase())
                    else -> throw com.ongo.common.exception.BusinessException("SHORTS_TEMPLATE_INVALID", "강조색은 #RRGGBB 형식이어야 합니다.")
                }
            }
            return mapper.writeValueAsString(root)
        }
    }
}
