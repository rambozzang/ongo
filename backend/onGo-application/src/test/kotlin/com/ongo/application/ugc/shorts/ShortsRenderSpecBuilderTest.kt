package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.domain.ugc.shorts.ShortsTemplate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ShortsRenderSpecBuilder — 렌더 산출물 3종(render-spec.json / clip-{seq}.ass / render.sh) 생성 검증.
 */
class ShortsRenderSpecBuilderTest {

    private val builder = ShortsRenderSpecBuilder()
    private val mapper = jacksonObjectMapper()

    private fun spec(
        crop: ShortsRenderSpecBuilder.CropBox? = ShortsRenderSpecBuilder.CropBox(10, 20, 1080, 1920),
        hookText: String? = "후킹 문구",
        hookPosition: String = "TOP",
    ) = ShortsRenderSpecBuilder.RenderSpec(
        clipSeq = 1,
        sourceVideoId = 5,
        sourceFileUrl = "https://cdn.example.com/source.mp4",
        startMs = 1500,
        endMs = 16500,
        crop = crop,
        hookText = hookText,
        hookPosition = hookPosition,
        subtitles = listOf(
            ShortsRenderSpecBuilder.SubtitleLine(0, 1500, "자막1"),
            ShortsRenderSpecBuilder.SubtitleLine(1500, 3000, "자막2"),
        ),
        templateId = 7,
        backgroundStyle = "BLACK_BARS",
        captionFontFamily = "Pretendard",
    )

    private fun template() = ShortsTemplate(
        id = 7,
        workspaceId = 10,
        name = "기본 템플릿",
        hookPosition = "TOP",
        hookFontSize = 70,
        hookFontColor = "#00FF00",
        captionFontFamily = "Noto Sans KR",
        captionFontSize = 52,
        captionFontColor = "#FF8800",
        captionPosition = "BOTTOM",
        createdBy = 1,
    )

    // ---- render-spec.json ----

    @Test
    fun `render-spec json은 설계 4장의 키 구조를 따른다`() {
        val root = mapper.readTree(builder.toJson(spec()))

        assertEquals(1, root.path("clipSeq").asInt())
        assertEquals(5, root.path("source").path("videoId").asLong())
        assertEquals("https://cdn.example.com/source.mp4", root.path("source").path("fileUrl").asText())
        assertEquals(1500, root.path("cut").path("startMs").asLong())
        assertEquals(16500, root.path("cut").path("endMs").asLong())
        assertEquals(1080, root.path("reframe").path("targetWidth").asInt())
        assertEquals(1920, root.path("reframe").path("targetHeight").asInt())
        assertEquals(10, root.path("reframe").path("crop").path("x").asInt())
        assertEquals(20, root.path("reframe").path("crop").path("y").asInt())
        assertEquals(1080, root.path("reframe").path("crop").path("width").asInt())
        assertEquals(1920, root.path("reframe").path("crop").path("height").asInt())
        assertEquals("후킹 문구", root.path("hook").path("text").asText())
        assertEquals("TOP", root.path("hook").path("position").asText())
        assertEquals(2, root.path("subtitles").size())
        assertEquals(0, root.path("subtitles")[0].path("startMs").asLong())
        assertEquals(1500, root.path("subtitles")[0].path("endMs").asLong())
        assertEquals("자막1", root.path("subtitles")[0].path("text").asText())
        assertEquals(7, root.path("template").path("id").asLong())
        assertEquals("BLACK_BARS", root.path("template").path("backgroundStyle").asText())
        assertEquals("Pretendard", root.path("template").path("captionFontFamily").asText())
    }

    @Test
    fun `크롭이 없으면 reframe에 crop 키를 넣지 않는다`() {
        val root = mapper.readTree(builder.toJson(spec(crop = null)))

        assertFalse(root.path("reframe").has("crop"))
        // 리사이즈 목표 해상도는 여전히 남는다
        assertEquals(1080, root.path("reframe").path("targetWidth").asInt())
    }

    @Test
    fun `toJson과 parseSpec은 왕복 변환에서 값을 보존한다`() {
        val original = spec()
        val restored = builder.parseSpec(builder.toJson(original))

        assertEquals(original.clipSeq, restored.clipSeq)
        assertEquals(original.sourceVideoId, restored.sourceVideoId)
        assertEquals(original.sourceFileUrl, restored.sourceFileUrl)
        assertEquals(original.startMs, restored.startMs)
        assertEquals(original.endMs, restored.endMs)
        assertEquals(original.crop, restored.crop)
        assertEquals(original.hookText, restored.hookText)
        assertEquals(original.hookPosition, restored.hookPosition)
        assertEquals(original.subtitles, restored.subtitles)
        assertEquals(original.templateId, restored.templateId)
        assertEquals(original.backgroundStyle, restored.backgroundStyle)
        assertEquals(original.captionFontFamily, restored.captionFontFamily)
    }

    // ---- clip-{seq}.ass ----

    @Test
    fun `ass 자막은 1080x1920 해상도의 v4 스크립트 헤더를 갖는다`() {
        val ass = builder.buildAss(spec(), template())

        assertTrue(ass.contains("[Script Info]"))
        assertTrue(ass.contains("ScriptType: v4.00+"))
        assertTrue(ass.contains("PlayResX: 1080"))
        assertTrue(ass.contains("PlayResY: 1920"))
        assertTrue(ass.contains("[V4+ Styles]"))
        assertTrue(ass.contains("[Events]"))
    }

    @Test
    fun `ass 스타일 줄에 템플릿의 폰트와 크기와 색이 반영된다`() {
        val ass = builder.buildAss(spec(), template())
        val captionStyle = ass.lines().single { it.startsWith("Style: Caption,") }
        val hookStyle = ass.lines().single { it.startsWith("Style: Hook,") }

        // #FF8800 → &H00BBGGRR = &H000088FF
        assertTrue(captionStyle.startsWith("Style: Caption,Noto Sans KR,52,&H000088FF"), captionStyle)
        // 자막 위치 BOTTOM → 정렬 2
        assertTrue(captionStyle.split(",").let { it[it.size - 5] == "2" }, captionStyle)
        // #00FF00 → &H0000FF00, 후킹 위치 TOP → 정렬 8
        assertTrue(hookStyle.contains("70,&H0000FF00"), hookStyle)
        assertTrue(hookStyle.split(",").let { it[it.size - 5] == "8" }, hookStyle)
    }

    @Test
    fun `ass 이벤트 줄은 자막 세그먼트를 센티초 시간으로 적는다`() {
        val ass = builder.buildAss(spec(), template())

        assertTrue(ass.contains("Dialogue: 0,0:00:00.00,0:00:01.50,Caption,,0,0,0,,자막1"))
        assertTrue(ass.contains("Dialogue: 0,0:00:01.50,0:00:03.00,Caption,,0,0,0,,자막2"))
    }

    @Test
    fun `후킹 문구는 클립 전 구간을 덮는 별도 레이어 줄로 적는다`() {
        val ass = builder.buildAss(spec(), template())

        // 클립 길이 16500 - 1500 = 15000ms → 0:00:15.00
        assertTrue(ass.contains("Dialogue: 1,0:00:00.00,0:00:15.00,Hook,,0,0,0,,후킹 문구"))
    }

    @Test
    fun `후킹 문구가 없거나 공백이면 Hook 이벤트 줄을 만들지 않는다`() {
        val noHook = builder.buildAss(spec(hookText = null), template())
        val blankHook = builder.buildAss(spec(hookText = "  "), template())

        assertFalse(noHook.lines().any { it.contains(",Hook,") })
        assertFalse(blankHook.lines().any { it.contains(",Hook,") })
    }

    @Test
    fun `ass 텍스트는 중괄호를 제거하고 줄바꿈을 N으로 이스케이프한다`() {
        val tricky = spec().copy(
            subtitles = listOf(ShortsRenderSpecBuilder.SubtitleLine(0, 1000, "{강조} 첫줄\n둘째줄")),
        )
        val ass = builder.buildAss(tricky, template())

        assertTrue(ass.contains("강조 첫줄\\N둘째줄"), ass)
        assertFalse(ass.contains("{강조}"))
    }

    // ---- render.sh ----

    @Test
    fun `render sh는 크롭-스케일-자막번인 순서의 ffmpeg 명령을 만든다`() {
        val script = builder.buildRenderScript(spec())

        assertTrue(script.startsWith("#!/bin/bash"), script)
        assertTrue(script.contains("ffmpeg -ss 1.500 -to 16.500 -i \"https://cdn.example.com/source.mp4\""), script)
        assertTrue(script.contains("-vf \"crop=1080:1920:10:20,scale=1080:1920,ass=clip-1.ass\""), script)
        assertTrue(script.contains("-c:v libx264 -preset medium -crf 20 -c:a aac -b:a 128k"), script)
        assertTrue(script.contains("\"clip-1.mp4\""), script)
    }

    @Test
    fun `render sh는 크롭이 없으면 스케일부터 시작한다`() {
        val script = builder.buildRenderScript(spec(crop = null))

        assertTrue(script.contains("-vf \"scale=1080:1920,ass=clip-1.ass\""), script)
        assertFalse(script.contains("crop="))
    }

    @Test
    fun `render sh는 원본 URL이 없으면 source mp4를 입력으로 쓴다`() {
        val script = builder.buildRenderScript(spec().copy(sourceFileUrl = null))

        assertTrue(script.contains("-i \"source.mp4\""), script)
    }

    // ---- 파싱 헬퍼 ----

    @Test
    fun `parseSubtitles는 자막 배열 JSON을 파싱한다`() {
        val lines = builder.parseSubtitles("""[{"startMs":0,"endMs":900,"text":"안녕"},{"startMs":900,"endMs":1800,"text":"세상"}]""")

        assertEquals(
            listOf(
                ShortsRenderSpecBuilder.SubtitleLine(0, 900, "안녕"),
                ShortsRenderSpecBuilder.SubtitleLine(900, 1800, "세상"),
            ),
            lines,
        )
    }

    @Test
    fun `parseSubtitles는 null과 공백과 깨진 JSON을 빈 목록으로 처리한다`() {
        assertEquals(emptyList(), builder.parseSubtitles(null))
        assertEquals(emptyList(), builder.parseSubtitles("  "))
        assertEquals(emptyList(), builder.parseSubtitles("{not-json"))
    }

    @Test
    fun `parseCrop은 유효한 크롭 JSON만 CropBox로 파싱한다`() {
        assertEquals(
            ShortsRenderSpecBuilder.CropBox(1, 2, 3, 4),
            builder.parseCrop("""{"x":1,"y":2,"width":3,"height":4}"""),
        )
        assertNull(builder.parseCrop("{not-json"))
    }
}
