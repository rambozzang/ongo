package com.ongo.application.ugc.shorts

import com.ongo.domain.ugc.shorts.FrameSheet
import com.ongo.domain.ugc.shorts.RenderFrameMode

/** 비전 모델 응답. 칸 번호는 1부터, 왼쪽 위에서 오른쪽으로, 위 줄부터. 좌표는 칸 안 비율(0~1). */
data class SpeakerTrackResult(val tiles: List<Tile> = emptyList()) {
    data class Tile(val index: Int = 0, val found: Boolean = false, val x: Double? = null, val y: Double? = null)
}

/**
 * 장면 격자의 인물 위치 → 시간에 따라 움직이는 9:16 크롭.
 *
 * - 인물을 찾은 칸이 [MIN_FOUND_RATIO] 미만이면 적용하지 않는다(대충 맞춘 결과로 사람을 자르느니 안 하는 게 낫다)
 * - 위치가 바뀔 때는 **직전 위치에 머물다가 [MOVE_MS] 동안 옮긴다** — 구간 내내 천천히 미끄러지면 멀미가 난다
 * - 정규화(9:16·원본 안·짝수·지점 정리)는 [ShortsCropPolicy] 가 한다
 */
object ShortsSpeakerTracking {

    const val MIN_FOUND_RATIO = 0.5
    const val MOVE_MS = 350L

    /** 크롭 위치가 이 비율보다 덜 바뀌면 같은 자리로 본다. */
    private const val SAME_SPOT = 0.03

    data class Track(val crop: CropSpec, val foundTiles: Int, val totalTiles: Int)

    fun prompt(sheet: FrameSheet): String =
        """
        이 이미지는 영상 한 구간에서 고르게 뽑은 장면 ${sheet.tileTimesMs.size}개를 ${sheet.columns}열 ${sheet.rows}행 격자로 붙인 것입니다.
        칸 번호는 왼쪽 위가 1이고 오른쪽으로, 그 다음 아래 줄로 이어집니다.
        각 칸에서 **지금 말하고 있는 사람**(알 수 없으면 화면의 주인공)의 얼굴 중심 위치를 칸 안 비율로 알려 주세요.
        x 는 칸 왼쪽 0 ~ 오른쪽 1, y 는 칸 위 0 ~ 아래 1 입니다. 사람이 없거나 확실하지 않으면 found 를 false 로 두세요.
        추측해서 채우지 마세요.
        응답은 {"tiles":[{"index":1,"found":true,"x":0.42,"y":0.35}, ...]} 형식의 JSON 으로만 하세요.
        """.trimIndent()

    /** @return 적용할 크롭. 인물을 충분히 찾지 못했으면 null. */
    fun toTrack(sheet: FrameSheet, result: SpeakerTrackResult): Track? {
        val total = sheet.tileTimesMs.size
        val points = result.tiles
            .filter { it.found && it.index in 1..total }
            .mapNotNull { t ->
                val x = t.x?.takeIf { it in 0.0..1.0 } ?: return@mapNotNull null
                val y = t.y?.takeIf { it in 0.0..1.0 } ?: return@mapNotNull null
                Triple(sheet.tileTimesMs[t.index - 1], x, y)
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
        if (total == 0 || points.size < total * MIN_FOUND_RATIO) return null

        // 얼굴은 화면 위쪽에 있다. 세로 창의 중심을 얼굴보다 약간 아래로 둬야 머리가 잘리지 않는다.
        fun cx(x: Double) = (x * sheet.sourceWidth).toInt()
        fun cy(y: Double) = (minOf(1.0, y + FACE_TO_CENTER) * sheet.sourceHeight).toInt()

        val keyframes = buildList {
            points.forEachIndexed { i, (at, x, y) ->
                val prev = points.getOrNull(i - 1)
                if (prev != null && moved(prev.second, prev.third, x, y) && at - MOVE_MS > prev.first) {
                    // 직전 위치에 머물렀다가 짧게 옮긴다.
                    add(CropKeyframe(at - MOVE_MS, cx(prev.second), cy(prev.third), 0, 0))
                }
                add(CropKeyframe(at, cx(x), cy(y), 0, 0))
            }
        }
        val first = points.first()
        val crop = ShortsCropPolicy.normalize(
            CropSpec(
                x = cx(first.second), y = cy(first.third), width = 0, height = 0,
                mode = RenderFrameMode.CROP,
                sourceWidth = sheet.sourceWidth, sourceHeight = sheet.sourceHeight,
                keyframes = keyframes,
            ),
        )
        return Track(crop, points.size, total)
    }

    private fun moved(x1: Double, y1: Double, x2: Double, y2: Double) =
        kotlin.math.abs(x1 - x2) >= SAME_SPOT || kotlin.math.abs(y1 - y2) >= SAME_SPOT

    /** 얼굴 중심 → 창 중심 세로 보정(원본 높이 비율). */
    private const val FACE_TO_CENTER = 0.12
}
