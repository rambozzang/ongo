package com.ongo.domain.ugc.shorts

/**
 * 세로 화면을 채우는 방법.
 *
 * - [CROP] 원본에서 9:16 창을 잘라 꽉 채운다. 창 위치는 시간에 따라 움직일 수 있다(화자 추적)
 * - [FIT_BLUR] 원본 전체를 가운데 두고, 남는 위아래를 같은 영상을 흐리게 키워 채운다
 * - [FIT_BARS] 원본 전체를 가운데 두고, 남는 곳을 검은 띠로 채운다
 *
 * 어느 쪽이든 **화면이 늘어나지 않는다.** 예전에는 크롭 상자 비율과 무관하게
 * `scale=1080:1920` 으로 늘였고, 크롭이 없으면 가로 영상을 통째로 세로로 늘였다.
 */
enum class RenderFrameMode { CROP, FIT_BLUR, FIT_BARS }

/**
 * 시간에 따른 창 중심. [atMs] 는 **클립 시작 기준**, 중심은 원본 너비·높이에 대한 비율(0~1)이다.
 */
data class RenderFramePoint(
    val atMs: Long,
    val centerX: Double,
    val centerY: Double,
) {
    init {
        require(atMs >= 0) { "창 이동 시각은 음수일 수 없다" }
        require(centerX in 0.0..1.0 && centerY in 0.0..1.0) { "창 중심은 0~1 비율이어야 한다" }
    }
}

/**
 * 렌더러가 받는 화면 구성. **원본 픽셀 크기를 몰라도 맞게 그려지도록 비율로만 말한다** —
 * 실제 크기는 ffmpeg 가 입력에서 읽는다(`iw`·`ih`). 그래서 좌표가 원본 밖으로 나가거나
 * 비율이 9:16 이 아닌 상자가 만들어질 수 없다.
 *
 * @param zoom 원본 안에 들어가는 가장 큰 9:16 창 대비 높이 비율. 1 이면 가장 큰 창,
 *   0.5 면 그 절반(확대). 화질이 무너지지 않게 [MIN_ZOOM] 아래로는 내려가지 않는다
 * @param path 창 중심의 시간별 위치. 비어 있으면 [centerX]·[centerY] 에 고정한다
 */
data class RenderFraming(
    val mode: RenderFrameMode,
    val centerX: Double = 0.5,
    val centerY: Double = 0.5,
    val zoom: Double = 1.0,
    val path: List<RenderFramePoint> = emptyList(),
) {
    init {
        require(centerX in 0.0..1.0 && centerY in 0.0..1.0) { "창 중심은 0~1 비율이어야 한다" }
        require(zoom in MIN_ZOOM..1.0) { "확대 비율은 $MIN_ZOOM~1 이어야 한다: $zoom" }
        require(path.size <= MAX_PATH_POINTS) { "창 이동 지점은 ${MAX_PATH_POINTS}개까지다" }
        require(path.zipWithNext().all { (a, b) -> a.atMs < b.atMs }) { "창 이동 지점은 시각 순이어야 한다" }
    }

    companion object {
        const val MIN_ZOOM = 0.5
        /** ffmpeg 식 길이를 묶어 둔다 — 지점마다 식이 하나씩 중첩된다. */
        const val MAX_PATH_POINTS = 60

        val FIT_BARS = RenderFraming(RenderFrameMode.FIT_BARS)
    }
}

/**
 * ffmpeg `-vf` 필터 문자열. 서버 렌더러와 내려받는 `render.sh` 가 **같은 함수**를 쓴다 —
 * 둘이 따로 만들면 화면에서 본 결과와 내려받은 스크립트 결과가 달라진다.
 *
 * 순서는 `화면 구성 → 출력 크기 → 자막` 이다. 자막을 먼저 구우면 크롭에 잘린다.
 */
object ShortsFilterGraph {

    /**
     * @param fontsDir 자막 글꼴 폴더(작업 디렉터리 기준 상대 경로). 서버에 한글 글꼴이 없어도
     *   함께 실은 글꼴로 그린다 — 없으면 한글이 네모로 깨진다
     */
    fun build(framing: RenderFraming, width: Int, height: Int, subtitleFile: String?, fontsDir: String? = null): String {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0) { "출력 크기는 양의 짝수여야 한다" }
        val body = when (framing.mode) {
            RenderFrameMode.CROP -> crop(framing, width, height)
            RenderFrameMode.FIT_BLUR -> fitBlur(width, height)
            RenderFrameMode.FIT_BARS -> fitBars(width, height)
        }
        val subtitles = subtitleFile?.let { file -> fontsDir?.let { "ass=filename=$file:fontsdir=$it" } ?: "ass=$file" }
        return subtitles?.let { "$body,$it" } ?: body
    }

    /**
     * 창 크기는 입력 크기에서 계산한다: 원본에 들어가는 가장 큰 출력 비율 창 × 확대 비율.
     * 위치는 중심에서 창 절반을 뺀 뒤 원본 안으로 묶는다(`clip`). 위치 식은 프레임마다 계산되므로
     * `t`(클립 시작 기준 초)로 창을 움직일 수 있다.
     */
    private fun crop(framing: RenderFraming, width: Int, height: Int): String {
        val ratio = "$width/$height"
        val zoom = num(framing.zoom)
        // min(ih, iw·H/W) 는 원본 안에 들어가는 가장 큰 창의 높이다.
        val maxH = "min(ih\\,iw*$height/$width)"
        val h = "trunc($maxH*$zoom/2)*2"
        val w = "trunc($maxH*$zoom*$ratio/2)*2"
        val cx = pathExpr(framing.path.map { it.atMs to it.centerX }, framing.centerX)
        val cy = pathExpr(framing.path.map { it.atMs to it.centerY }, framing.centerY)
        val x = "clip($cx*iw-ow/2\\,0\\,iw-ow)"
        val y = "clip($cy*ih-oh/2\\,0\\,ih-oh)"
        return "crop=w=$w:h=$h:x=$x:y=$y,scale=$width:$height,setsar=1"
    }

    /** 배경은 작게 줄여 흐린 뒤 키운다 — 같은 흐림을 출력 크기에서 하면 몇 배 느리다. */
    private fun fitBlur(width: Int, height: Int): String {
        val bw = width / 4 / 2 * 2
        val bh = height / 4 / 2 * 2
        return "split=2[bg][fg];" +
            "[bg]scale=$bw:$bh:force_original_aspect_ratio=increase,crop=$bw:$bh,boxblur=10:2,scale=$width:$height[bgb];" +
            "[fg]scale=$width:$height:force_original_aspect_ratio=decrease:force_divisible_by=2[fgs];" +
            "[bgb][fgs]overlay=(W-w)/2:(H-h)/2,setsar=1"
    }

    private fun fitBars(width: Int, height: Int): String =
        "scale=$width:$height:force_original_aspect_ratio=decrease:force_divisible_by=2," +
            "pad=$width:$height:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1"

    /**
     * 시각별 값을 이어 붙인 구간 선형 식. 첫 지점 전은 첫 값, 마지막 지점 뒤는 마지막 값이다.
     * 지점 사이를 선형으로 이어야 창이 순간 이동하지 않는다.
     */
    private fun pathExpr(points: List<Pair<Long, Double>>, fixed: Double): String {
        if (points.isEmpty()) return num(fixed)
        if (points.size == 1) return num(points[0].second)
        val sb = StringBuilder()
        var open = 0
        sb.append("if(lt(t\\,${sec(points[0].first)})\\,${num(points[0].second)}\\,")
        open++
        points.zipWithNext().forEach { (a, b) ->
            val (ta, va) = a
            val (tb, vb) = b
            val lerp = "${num(va)}+(${num(vb - va)})*(t-${sec(ta)})/${sec(tb - ta)}"
            sb.append("if(lt(t\\,${sec(tb)})\\,$lerp\\,")
            open++
        }
        sb.append(num(points.last().second))
        repeat(open) { sb.append(")") }
        return sb.toString()
    }

    private fun num(v: Double): String = "%.4f".format(java.util.Locale.ROOT, v)
    private fun sec(ms: Long): String = "%.3f".format(java.util.Locale.ROOT, ms / 1000.0)
}
