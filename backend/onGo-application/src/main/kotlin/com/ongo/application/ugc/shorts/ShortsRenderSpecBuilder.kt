package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.domain.ugc.shorts.ClipHook
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

    data class CropBox(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    data class SubtitleLine(
        val startMs: Long,
        val endMs: Long,
        val text: String,
    )

    /** render-spec.json 의 메모리 모델. JSON 키 구조는 설계 4장을 따른다. */
    data class RenderSpec(
        val clipSeq: Int,
        val sourceVideoId: Long,
        val sourceFileUrl: String?,
        val startMs: Long,
        val endMs: Long,
        val crop: CropBox?,
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
            spec.crop?.let { crop ->
                putObject("crop").apply {
                    put("x", crop.x)
                    put("y", crop.y)
                    put("width", crop.width)
                    put("height", crop.height)
                }
            }
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
            crop = if (cropNode.isMissingNode || cropNode.isNull) null else CropBox(
                x = cropNode.path("x").asInt(),
                y = cropNode.path("y").asInt(),
                width = cropNode.path("width").asInt(),
                height = cropNode.path("height").asInt(),
            ),
            hookText = root.path("hook").path("text").asText(null),
            hookPosition = root.path("hook").path("position").asText("TOP"),
            subtitles = root.path("subtitles").map { line ->
                SubtitleLine(
                    startMs = line.path("startMs").asLong(),
                    endMs = line.path("endMs").asLong(),
                    text = line.path("text").asText(),
                )
            },
            templateId = root.path("template").path("id").takeIf { it.isNumber }?.asLong(),
            backgroundStyle = root.path("template").path("backgroundStyle").asText(null),
            captionFontFamily = root.path("template").path("captionFontFamily").asText(null),
        )
    }

    /**
     * clip-{seq}.ass 자막 파일. 템플릿의 폰트·색·외곽선을 [V4+ Styles]에 반영하고,
     * 자막 세그먼트를 Dialogue 줄로, 후킹 문구는 클립 전 구간 별도 스타일 줄로 적는다.
     */
    fun buildAss(spec: RenderSpec, template: ShortsTemplate?): String {
        val captionFont = template?.captionFontFamily ?: spec.captionFontFamily ?: "Pretendard"
        val captionSize = template?.captionFontSize ?: 48
        val captionColor = assColor(template?.captionFontColor, "&H00FFFFFF")
        val captionStroke = assColor(template?.captionStrokeColor, "&H00000000")
        val captionAlignment = positionAlignment(template?.captionPosition ?: "BOTTOM")

        val hookFont = template?.hookFontFamily ?: captionFont
        val hookSize = template?.hookFontSize ?: 64
        val hookColor = assColor(template?.hookFontColor, "&H0000FFFF")
        val hookStroke = assColor(template?.hookStrokeColor, "&H00000000")
        val hookAlignment = positionAlignment(spec.hookPosition)

        val sb = StringBuilder()
        sb.appendLine("[Script Info]")
        sb.appendLine("ScriptType: v4.00+")
        sb.appendLine("PlayResX: 1080")
        sb.appendLine("PlayResY: 1920")
        sb.appendLine()
        sb.appendLine("[V4+ Styles]")
        sb.appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        sb.appendLine("Style: Caption,$captionFont,$captionSize,$captionColor,$captionColor,$captionStroke,$captionStroke,-1,0,0,0,100,100,0,0,1,3,0,$captionAlignment,40,40,60,1")
        sb.appendLine("Style: Hook,$hookFont,$hookSize,$hookColor,$hookColor,$hookStroke,$hookStroke,-1,0,0,0,100,100,0,0,1,4,0,$hookAlignment,40,40,80,1")
        sb.appendLine()
        sb.appendLine("[Events]")
        sb.appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        spec.subtitles.forEach { line ->
            sb.appendLine("Dialogue: 0,${assTime(line.startMs)},${assTime(line.endMs)},Caption,,0,0,0,,${assText(line.text)}")
        }
        spec.hookText?.takeIf { it.isNotBlank() }?.let { hook ->
            sb.appendLine("Dialogue: 1,${assTime(0)},${assTime(spec.durationMs)},Hook,,0,0,0,,${assText(hook)}")
        }
        return sb.toString()
    }

    /**
     * render.sh — 크롭 → 스케일 → 자막 번인 순서의 ffmpeg 명령 (설계 4장 형식).
     */
    fun buildRenderScript(spec: RenderSpec): String {
        val cropFilter = spec.crop?.let { "crop=${it.width}:${it.height}:${it.x}:${it.y}," } ?: ""
        return """
            |#!/bin/bash
            |ffmpeg -ss ${seconds(spec.startMs)} -to ${seconds(spec.endMs)} -i "${spec.sourceFileUrl ?: "source.mp4"}" \
            |  -vf "${cropFilter}scale=1080:1920,ass=clip-${spec.clipSeq}.ass" \
            |  -c:v libx264 -preset medium -crf 20 -c:a aac -b:a 128k \
            |  "clip-${spec.clipSeq}.mp4"
            |
        """.trimMargin()
    }

    /** 클립의 subtitle_json 배열 문자열을 파싱한다. */
    fun parseSubtitles(subtitleJson: String?): List<SubtitleLine> {
        if (subtitleJson.isNullOrBlank()) return emptyList()
        return runCatching {
            mapper.readTree(subtitleJson).map { line ->
                SubtitleLine(
                    startMs = line.path("startMs").asLong(),
                    endMs = line.path("endMs").asLong(),
                    text = line.path("text").asText(),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 크롭 JSON 문자열을 CropBox로 파싱한다. */
    fun parseCrop(cropJson: String): CropBox? = runCatching {
        val node = mapper.readTree(cropJson)
        CropBox(
            x = node.path("x").asInt(),
            y = node.path("y").asInt(),
            width = node.path("width").asInt(),
            height = node.path("height").asInt(),
        )
    }.getOrNull()

    /** #RRGGBB → ASS &H00BBGGRR 변환. 형식이 다류면 기본값을 쓴다. */
    private fun assColor(color: String?, fallback: String): String {
        val hex = color?.removePrefix("#") ?: return fallback
        if (hex.length != 6) return fallback
        val r = hex.substring(0, 2)
        val g = hex.substring(2, 4)
        val b = hex.substring(4, 6)
        return "&H00$b$g$r".uppercase()
    }

    /** ASS 정렬: 하단=2, 상단=8. */
    private fun positionAlignment(position: String): Int =
        if (position.equals("TOP", ignoreCase = true)) 8 else 2

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
    private fun seconds(ms: Long): String = "%.3f".format(ms / 1000.0)
}
