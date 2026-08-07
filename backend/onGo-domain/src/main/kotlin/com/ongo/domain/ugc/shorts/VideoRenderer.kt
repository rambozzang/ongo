package com.ongo.domain.ugc.shorts

import java.nio.file.Path

/**
 * 클립 하나를 실제 영상 파일로 만든다.
 *
 * 지금까지 파이프라인은 **편집 지시서만** 만들었다. `render-spec.json` / `clip-N.ass` /
 * `render.sh` 를 zip 으로 내려주고 사용자가 자기 PC 에서 돌렸다. 크롭 좌표·자막·구간처럼
 * 어려운 판단은 이미 다 계산돼 있었는데 실행할 층이 없었다.
 *
 * 이 포트가 그 층이다.
 *
 * ## 왜 셸 스크립트를 실행하지 않는가
 *
 * `ShortsRenderSpecBuilder.buildRenderScript` 는 `sourceFileUrl` 을 셸 문자열에 끼워 넣는다.
 * 그걸 그대로 `bash` 로 돌리면 URL 에 들어간 문자가 명령으로 해석될 수 있다.
 * 구현체는 **인자 배열로 프로세스를 직접 실행**해 그 통로를 아예 없앤다.
 * 스크립트는 사용자가 손으로 돌릴 때를 위한 산출물로 남는다.
 */
interface VideoRenderer {

    /**
     * 렌더해서 **임시 파일**을 돌려준다.
     *
     * 반환된 파일의 수명은 호출자가 책임진다. 스토리지에 올렸든 거부했든 반드시 지워야 한다.
     * 인코딩 산출물은 원본만큼 커서 남기면 디스크가 찬다.
     */
    fun render(request: ClipRenderRequest): RenderedClip

    /**
     * 인코더를 지금 쓸 수 있는지 확인한다.
     *
     * ffmpeg 는 배포 전제라 JVM 밖에 있다. 없으면 렌더를 눌러본 뒤에야 알게 되므로
     * 화면이 미리 물어볼 수 있게 한다. "쓸 수 없다"는 예외가 아니라 정상적인 답이다.
     */
    fun checkAvailability(): RendererAvailability
}

/**
 * @param sourceUrl 원본 영상 위치. 구현체는 이 값을 **셸에 넘기지 않는다**
 * @param subtitleAss ASS 자막 본문. 없으면 자막을 굽지 않는다
 */
data class ClipRenderRequest(
    val sourceUrl: String,
    val startMs: Long,
    val endMs: Long,
    val crop: RenderCropBox? = null,
    val subtitleAss: String? = null,
    val outputWidth: Int = DEFAULT_WIDTH,
    val outputHeight: Int = DEFAULT_HEIGHT,
) {
    init {
        require(endMs > startMs) { "클립 끝 시각이 시작 시각보다 커야 한다: start=$startMs end=$endMs" }
        require(outputWidth > 0 && outputHeight > 0) { "출력 해상도는 양수여야 한다" }
    }

    val durationMs: Long get() = endMs - startMs

    companion object {
        /** 쇼츠 기본 해상도. 세로 9:16. */
        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 1920
    }
}

data class RenderCropBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "크롭 크기는 양수여야 한다" }
        require(x >= 0 && y >= 0) { "크롭 좌표는 음수일 수 없다" }
    }
}

data class RenderedClip(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String = "video/mp4",
)

/**
 * @param reason 사용자에게 그대로 보여줄 수 있어야 한다. 경로·예외 메시지를 담지 않는다.
 *   내부 구조가 노출되고, 사용자가 그걸 보고 할 수 있는 일도 없다.
 */
data class RendererAvailability(
    val available: Boolean,
    val reason: String? = null,
)
