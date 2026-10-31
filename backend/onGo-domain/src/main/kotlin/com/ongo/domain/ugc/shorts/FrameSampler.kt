package com.ongo.domain.ugc.shorts

/**
 * 클립 구간에서 고르게 뽑은 장면들을 **한 장의 격자 이미지**로 만든다(화자 추적용).
 *
 * 장면마다 이미지를 따로 보내면 AI 입력이 장면 수만큼 늘어난다. 격자 한 장이면 한 번의 호출로
 * 시간에 따른 인물 위치를 모두 물을 수 있다.
 */
interface FrameSampler {

    /**
     * @param startMs 원본 기준 클립 시작
     * @param endMs 원본 기준 클립 끝
     * @throws IllegalStateException 원본을 읽지 못했거나 이미지를 만들지 못했을 때. 메시지는 사용자에게 보여도 된다
     */
    fun contactSheet(sourceUrl: String, startMs: Long, endMs: Long): FrameSheet
}

/**
 * @param tileTimesMs 칸 순서(왼쪽 위부터 오른쪽으로, 위 줄부터)대로 그 장면의 **클립 기준** 시각
 * @param sourceWidth 원본 영상 너비(픽셀). 크롭 좌표의 기준이다
 */
class FrameSheet(
    val image: ByteArray,
    val mimeType: String,
    val columns: Int,
    val rows: Int,
    val tileTimesMs: List<Long>,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    init {
        require(columns > 0 && rows > 0) { "격자 크기는 양수여야 한다" }
        require(tileTimesMs.size <= columns * rows) { "장면 수가 칸 수보다 많다" }
        require(sourceWidth > 0 && sourceHeight > 0) { "원본 크기는 양수여야 한다" }
    }
}
