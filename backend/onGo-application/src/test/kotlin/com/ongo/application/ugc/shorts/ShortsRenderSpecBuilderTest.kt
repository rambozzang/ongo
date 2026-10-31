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
        crop: CropSpec? = CropSpec(10, 20, 1080, 1920),
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

    /** 예전에는 클립 끝까지 떠 있어 자막·화면을 가렸다. 첫 몇 초에 시선을 잡고 사라진다. */
    @Test
    fun `후킹 문구는 처음 몇 초만 별도 레이어로 크게 띄운다`() {
        val ass = builder.buildAss(spec(), template())
        val hook = ass.lines().single { it.contains(",Hook,,") }

        assertTrue(hook.startsWith("Dialogue: 1,0:00:00.00,0:00:03.50,Hook,,0,0,0,,"), hook)
        assertTrue(hook.endsWith("후킹 문구"), hook)
        assertTrue(hook.contains("\\fad("), hook)

        val short = builder.buildAss(spec().copy(endMs = 3_500), template())
        assertTrue(short.contains("Dialogue: 1,0:00:00.00,0:00:02.00,Hook"), "클립보다 길게 띄우면 안 된다: $short")
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

    /**
     * 예전에는 `crop=상자,scale=1080:1920` 이라 상자가 9:16 이 아니면 화면이 늘어났다.
     * 이제 창 크기는 입력 크기에서 9:16 으로 계산하고, 위치는 원본 안으로 묶는다.
     */
    @Test
    fun `render sh는 9대16 창 크롭-스케일-자막번인 순서의 ffmpeg 명령을 만든다`() {
        val script = builder.buildRenderScript(spec())

        assertTrue(script.startsWith("#!/bin/bash"), script)
        assertTrue(script.contains("ffmpeg -ss 1.500 -to 16.500 -i \"https://cdn.example.com/source.mp4\""), script)
        val vf = script.substringAfter("-vf '").substringBefore("'")
        assertTrue(vf.startsWith("crop=w=trunc(min(ih\\,iw*1920/1080)"), vf)
        assertTrue(vf.contains("clip("), "원본 밖으로 나가지 않게 위치를 묶어야 한다: $vf")
        assertTrue(vf.indexOf("crop=") < vf.indexOf("scale=1080:1920") && vf.indexOf("scale=1080:1920") < vf.indexOf("ass=clip-1.ass"), vf)
        assertTrue(script.contains("-c:v libx264 -preset medium -crf 20 -c:a aac -b:a 128k"), script)
        assertTrue(script.contains("\"clip-1.mp4\""), script)
    }

    /** 크롭이 없으면 가로 영상을 통째로 세로로 늘였다. 이제 늘이지 않고 템플릿 배경으로 맞춘다. */
    @Test
    fun `render sh는 크롭이 없으면 늘이지 않고 템플릿 배경 스타일로 맞춘다`() {
        val bars = builder.buildRenderScript(spec(crop = null))
        assertTrue(bars.contains("force_original_aspect_ratio=decrease"), bars)
        assertTrue(bars.contains("pad=1080:1920"), bars)
        assertFalse(bars.contains("crop="), bars)

        val blur = builder.buildRenderScript(spec(crop = null).copy(backgroundStyle = "BLURRED"))
        assertTrue(blur.contains("boxblur"), blur)
        assertTrue(blur.contains("overlay=(W-w)/2:(H-h)/2"), blur)
        assertTrue(blur.contains("ass=clip-1.ass"), blur)
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
    fun `parseCrop은 유효한 크롭 JSON만 CropSpec으로 파싱한다`() {
        assertEquals(
            CropSpec(1, 2, 3, 4),
            builder.parseCrop("""{"x":1,"y":2,"width":3,"height":4}"""),
        )
        assertNull(builder.parseCrop("{not-json"))
    }

    // ---- 단어 강조 자막 ----

    private fun wordSpec() = spec().copy(
        subtitles = listOf(
            ShortsRenderSpecBuilder.SubtitleLine(
                0, 1500, "구독과 좋아요",
                words = listOf(CaptionWord(0, 600, "구독과"), CaptionWord(600, 1500, "좋아요")),
            ),
        ),
    )

    @Test
    fun `단어 시각이 있으면 말하는 단어마다 그 단어를 강조한 이벤트를 만든다`() {
        val ass = builder.buildAss(wordSpec(), template())
        val captions = ass.lines().filter { it.contains(",Caption,,") }

        assertEquals(2, captions.size, ass)
        assertTrue(captions[0].startsWith("Dialogue: 0,0:00:00.00,0:00:00.60,Caption"), captions[0])
        assertTrue(captions[0].endsWith("{\\c&H0000E1FF&\\fscx112\\fscy112}구독과{\\r} 좋아요"), captions[0])
        assertTrue(captions[1].startsWith("Dialogue: 0,0:00:00.60,0:00:01.50,Caption"), captions[1])
        assertTrue(captions[1].endsWith("구독과 {\\c&H0000E1FF&\\fscx112\\fscy112}좋아요{\\r}"), captions[1])
    }

    @Test
    fun `스타일 프리셋과 강조색은 템플릿 extraSpec 으로 고른다`() {
        val box = template().copy(extraSpec = """{"captionStyle":"box","highlightColor":"#00FF00"}""")
        val ass = builder.buildAss(wordSpec(), box)
        val style = ass.lines().single { it.startsWith("Style: Caption,") }.split(",")

        assertEquals("3", style[15], "상자 스타일은 BorderStyle 3: $style")
        assertTrue(ass.contains("{\\c&H0000FF00&}구독과{\\r}"), ass)

        val clean = builder.buildAss(wordSpec(), template().copy(extraSpec = """{"captionStyle":"CLEAN"}"""))
        assertFalse(clean.contains("fscx112"), clean)
    }

    @Test
    fun `사용자 템플릿에 프리셋이 없으면 강조 팝을 쓰고 깨진 extraSpec 도 기본으로 처리한다`() {
        assertEquals(CaptionStyle.POP, CaptionStyle.of(null))
        assertEquals(CaptionStyle.POP, CaptionStyle.of("{not-json"))
        assertEquals(CaptionStyle.CLEAN, CaptionStyle.of("""{"captionStyle":"clean"}"""))
    }

    @Test
    fun `가운데 위치는 정렬 5 이고 아래 자막은 플랫폼 버튼 영역 위로 올린다`() {
        val center = builder.buildAss(spec(), template().copy(captionPosition = "CENTER"))
            .lines().single { it.startsWith("Style: Caption,") }.split(",")
        assertEquals("5", center[18])

        val bottom = builder.buildAss(spec(), template().copy(safeAreaBottom = 600))
            .lines().single { it.startsWith("Style: Caption,") }.split(",")
        assertEquals("2", bottom[18])
        assertEquals("600", bottom[21], "세이프 영역이 기본 여백보다 크면 세이프 영역을 따른다")

        val defaultBottom = builder.buildAss(spec(), template())
            .lines().single { it.startsWith("Style: Caption,") }.split(",")
        assertEquals(ShortsRenderSpecBuilder.CAPTION_BOTTOM_MARGIN.toString(), defaultBottom[21])
    }

    @Test
    fun `긴 문장 줄은 짧은 묶음으로 나눠 여러 이벤트로 띄운다`() {
        val long = spec().copy(
            subtitles = listOf(ShortsRenderSpecBuilder.SubtitleLine(0, 4000, "오늘은 제가 직접 만든 김치찌개 레시피를 알려드릴게요")),
        )
        val captions = builder.buildAss(long, template()).lines().filter { it.contains(",Caption,,") }

        assertTrue(captions.size >= 2, "한 화면에 문장 전체를 띄우면 안 된다: $captions")
        assertTrue(captions.none { it.contains("\\c&") }, "추정 시각으로는 단어를 강조하지 않는다")
    }

    @Test
    fun `단어 시각은 render-spec 왕복과 subtitle_json 파싱에서 보존된다`() {
        val original = wordSpec()
        assertEquals(original.subtitles, builder.parseSpec(builder.toJson(original)).subtitles)

        val parsed = builder.parseSubtitles(
            """[{"startMs":0,"endMs":900,"text":"안녕","words":[{"startMs":0,"endMs":900,"text":"안녕"},{"startMs":"x","endMs":1,"text":"깨짐"}]}]""",
        )
        assertEquals(listOf(CaptionWord(0, 900, "안녕")), parsed.single().words)
    }
}
