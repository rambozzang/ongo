package com.ongo.application.ugc.shorts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.exception.BusinessException
import com.ongo.domain.ugc.shorts.RenderFrameMode
import com.ongo.domain.ugc.shorts.RenderFramePoint
import com.ongo.domain.ugc.shorts.RenderFraming
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** `shorts_clips.crop_json` 의 창 이동 지점. [atMs] 는 클립 시작 기준이다. */
data class CropKeyframe(val atMs: Long, val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * `shorts_clips.crop_json` 의 메모리 모델. 좌표는 원본 픽셀이다.
 * [sourceWidth]·[sourceHeight] 가 없으면 좌표의 기준을 알 수 없어 [ShortsCropPolicy.NOMINAL_WIDTH]
 * × [ShortsCropPolicy.NOMINAL_HEIGHT] 원본으로 본다(예전 LLM 추측 좌표).
 */
data class CropSpec(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val mode: RenderFrameMode = RenderFrameMode.CROP,
    val sourceWidth: Int? = null,
    val sourceHeight: Int? = null,
    val keyframes: List<CropKeyframe> = emptyList(),
)

/**
 * 크롭의 **유일한** 검증·정규화 지점. REFRAME 단계, 편집기 저장, 렌더가 모두 이것을 쓴다.
 *
 * 예전 문제:
 * - 영상을 보지 않은 LLM 이 준 상자를 그대로 `crop → scale=1080:1920` 했다. 상자가 9:16 이 아니면
 *   화면이 늘어났고, 원본 밖 좌표는 ffmpeg 오류가 됐다.
 * - 크롭이 없으면 가로 영상을 통째로 세로로 늘였다.
 *
 * 규칙:
 * 1. 상자는 항상 **출력 비율(9:16)** 이다. 원래 상자 중심을 지키고, 원래 상자를 다 담는 쪽으로 키운다
 * 2. 원본 안에 들어가는 가장 큰 9:16 창보다 크지 않고, 그 절반보다 작지 않다(지나친 확대는 화질을 무너뜨린다)
 * 3. 원본 밖으로 나가지 않는다. 크기는 짝수(libx264 요구)
 * 4. 창 이동 지점은 시각 순, 같은 시각은 마지막 것만, 크기는 본 상자와 같게(창이 커졌다 작아지면 어지럽다)
 */
object ShortsCropPolicy {

    const val TARGET_WIDTH = 1080
    const val TARGET_HEIGHT = 1920
    const val NOMINAL_WIDTH = 1920
    const val NOMINAL_HEIGHT = 1080
    private const val MAX_SOURCE_SIDE = 16_384

    /** 창 중심이 이 비율보다 덜 움직이면 지점으로 남기지 않는다 — 잔떨림은 화면을 흔들기만 한다. */
    private const val DEAD_ZONE = 0.02

    private val mapper = jacksonObjectMapper()

    /** 저장된 JSON 을 읽는다. 형식이 깨졌으면 null — 렌더는 크롭 없이(맞춤) 진행한다. */
    fun parse(json: String?): CropSpec? {
        if (json.isNullOrBlank()) return null
        return runCatching { fromNode(mapper.readTree(json)) }.getOrNull()
    }

    /**
     * 편집기·단계 출력용. 깨진 입력은 **조용히 고치지 않고 거절한다** — 사용자가 고른 화면과
     * 다른 결과가 나오면 안 된다. 통과하면 정규화된 JSON 을 돌려준다(이것을 저장한다).
     */
    fun normalizeJson(json: String, sourceWidth: Int? = null, sourceHeight: Int? = null): String {
        val node = runCatching { mapper.readTree(json) }.getOrNull()
            ?: throw invalid("크롭 정보를 읽을 수 없습니다.")
        val spec = fromNode(node) ?: throw invalid("크롭 정보에 x, y, width, height 가 모두 있어야 합니다.")
        return toJson(normalize(spec, sourceWidth, sourceHeight))
    }

    fun normalize(spec: CropSpec, sourceWidth: Int? = null, sourceHeight: Int? = null): CropSpec {
        val sw = spec.sourceWidth ?: sourceWidth ?: NOMINAL_WIDTH
        val sh = spec.sourceHeight ?: sourceHeight ?: NOMINAL_HEIGHT
        if (sw !in 2..MAX_SOURCE_SIDE || sh !in 2..MAX_SOURCE_SIDE) throw invalid("원본 크기가 올바르지 않습니다.")

        val (w, h) = windowSize(spec.width, spec.height, sw, sh)
        val (x, y) = place(spec.x + spec.width / 2.0, spec.y + spec.height / 2.0, w, h, sw, sh)

        val keyframes = spec.keyframes
            .filter { it.atMs >= 0 }
            .sortedBy { it.atMs }
            .associateBy { it.atMs }.values // 같은 시각은 마지막 것
            .map { k ->
                val (kx, ky) = place(k.x + k.width / 2.0, k.y + k.height / 2.0, w, h, sw, sh)
                CropKeyframe(k.atMs, kx, ky, w, h)
            }
            .let { thin(it, sw, sh) }
            .let { downsample(it, RenderFraming.MAX_PATH_POINTS) }

        return CropSpec(x, y, w, h, spec.mode, sw, sh, keyframes)
    }

    fun toJson(spec: CropSpec): String {
        val root = mapper.createObjectNode()
        root.put("x", spec.x)
        root.put("y", spec.y)
        root.put("width", spec.width)
        root.put("height", spec.height)
        root.put("mode", spec.mode.name)
        spec.sourceWidth?.let { root.put("sourceWidth", it) }
        spec.sourceHeight?.let { root.put("sourceHeight", it) }
        if (spec.keyframes.isNotEmpty()) {
            root.putArray("keyframes").apply {
                spec.keyframes.forEach { k ->
                    addObject().apply {
                        put("atMs", k.atMs)
                        put("x", k.x)
                        put("y", k.y)
                        put("width", k.width)
                        put("height", k.height)
                    }
                }
            }
        }
        return mapper.writeValueAsString(root)
    }

    /**
     * 렌더러에 넘길 화면 구성. 크롭이 없거나 맞춤 모드면 템플릿 배경 스타일을 따른다
     * (`BLURRED`/`BLUR` → 흐린 배경, 그 밖 → 검은 띠). **어느 경우에도 늘이지 않는다.**
     */
    fun toFraming(spec: CropSpec?, backgroundStyle: String?): RenderFraming {
        val fallback = fitModeOf(backgroundStyle)
        if (spec == null) return RenderFraming(fallback)
        if (spec.mode == RenderFrameMode.FIT_BLUR || spec.mode == RenderFrameMode.FIT_BARS) return RenderFraming(spec.mode)

        val n = normalize(spec)
        val sw = n.sourceWidth!!.toDouble()
        val sh = n.sourceHeight!!.toDouble()
        val maxH = maxWindowHeight(n.sourceWidth, n.sourceHeight)
        fun cx(x: Int, w: Int) = ((x + w / 2.0) / sw).coerceIn(0.0, 1.0)
        fun cy(y: Int, h: Int) = ((y + h / 2.0) / sh).coerceIn(0.0, 1.0)
        return RenderFraming(
            mode = RenderFrameMode.CROP,
            centerX = cx(n.x, n.width),
            centerY = cy(n.y, n.height),
            zoom = (n.height / maxH).coerceIn(RenderFraming.MIN_ZOOM, 1.0),
            path = n.keyframes.map { RenderFramePoint(it.atMs, cx(it.x, it.width), cy(it.y, it.height)) },
        )
    }

    fun fitModeOf(backgroundStyle: String?): RenderFrameMode = when (backgroundStyle?.uppercase()) {
        "BLURRED", "BLUR" -> RenderFrameMode.FIT_BLUR
        else -> RenderFrameMode.FIT_BARS
    }

    /* ── 내부 ─────────────────────────────────────────────────────────── */

    private fun fromNode(node: JsonNode): CropSpec? {
        if (!node.isObject) return null
        val fields = listOf("x", "y", "width", "height")
        if (fields.any { !node.path(it).isNumber }) return null
        val mode = node.path("mode").takeIf { it.isTextual }?.asText()?.let { raw ->
            RenderFrameMode.entries.firstOrNull { it.name == raw.uppercase() } ?: throw invalid("알 수 없는 화면 구성입니다: $raw")
        } ?: RenderFrameMode.CROP
        val keyframes = node.path("keyframes").takeIf { it.isArray }?.mapNotNull { k ->
            if (!k.path("atMs").isNumber || fields.any { !k.path(it).isNumber }) null
            else CropKeyframe(k.path("atMs").asLong(), k.path("x").asInt(), k.path("y").asInt(), k.path("width").asInt(), k.path("height").asInt())
        }.orEmpty()
        return CropSpec(
            x = node.path("x").asInt(),
            y = node.path("y").asInt(),
            width = node.path("width").asInt(),
            height = node.path("height").asInt(),
            mode = mode,
            sourceWidth = node.path("sourceWidth").takeIf { it.isNumber }?.asInt(),
            sourceHeight = node.path("sourceHeight").takeIf { it.isNumber }?.asInt(),
            keyframes = keyframes,
        )
    }

    /** 원본 안에 들어가는 가장 큰 9:16 창의 높이. */
    private fun maxWindowHeight(sw: Int, sh: Int): Double =
        minOf(sh.toDouble(), sw * TARGET_HEIGHT.toDouble() / TARGET_WIDTH)

    /** 원래 상자를 다 담는 9:16 창. 상자가 없거나 0 이면 가장 큰 창. */
    private fun windowSize(boxW: Int, boxH: Int, sw: Int, sh: Int): Pair<Int, Int> {
        val maxH = maxWindowHeight(sw, sh)
        val wanted = if (boxW <= 0 || boxH <= 0) maxH else maxOf(boxH.toDouble(), boxW * TARGET_HEIGHT.toDouble() / TARGET_WIDTH)
        val h = even(wanted.coerceIn(maxH * RenderFraming.MIN_ZOOM, maxH), maxH)
        val w = even(h * TARGET_WIDTH.toDouble() / TARGET_HEIGHT, sw.toDouble())
        return w to h
    }

    /** 중심에서 창 절반을 빼고 원본 안으로 묶는다. */
    private fun place(centerX: Double, centerY: Double, w: Int, h: Int, sw: Int, sh: Int): Pair<Int, Int> {
        val x = (centerX - w / 2.0).roundToInt().coerceIn(0, sw - w)
        val y = (centerY - h / 2.0).roundToInt().coerceIn(0, sh - h)
        return x to y
    }

    /** 짝수로 내림하되 상한을 넘지 않는다. 최소 2. */
    private fun even(value: Double, max: Double): Int {
        val capped = minOf(value, max)
        return maxOf(2, (capped.toInt() / 2) * 2)
    }

    /**
     * 앞뒤 지점과 모두 거의 같은 위치인 지점은 버린다(첫·마지막은 남긴다). 잔떨림은 화면을 흔들기만 한다.
     * 앞과 같고 뒤와 다른 지점은 **머무름**(그 자리에 있다가 옮겨 가기)이라 남긴다.
     */
    private fun thin(points: List<CropKeyframe>, sw: Int, sh: Int): List<CropKeyframe> {
        if (points.size <= 2) return points
        fun near(a: CropKeyframe, b: CropKeyframe) =
            abs(a.x - b.x).toDouble() / sw < DEAD_ZONE && abs(a.y - b.y).toDouble() / sh < DEAD_ZONE
        val kept = mutableListOf(points.first())
        points.indices.drop(1).dropLast(1).forEach { i ->
            val p = points[i]
            if (!(near(p, kept.last()) && near(p, points[i + 1]))) kept += p
        }
        kept += points.last()
        return kept
    }

    /** 지점이 너무 많으면 고르게 골라낸다(첫·마지막 포함). */
    private fun downsample(points: List<CropKeyframe>, max: Int): List<CropKeyframe> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return (0 until max).map { points[(it * step).roundToLong().toInt().coerceAtMost(points.size - 1)] }.distinctBy { it.atMs }
    }

    private fun invalid(message: String) = BusinessException("SHORTS_CROP_INVALID", message)
}
