package com.ongo.infrastructure.render

import com.ongo.domain.ugc.shorts.ClipRenderRequest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 렌더가 **실제 파일을 만들었을 때만** 성공이라고 답하는지 고정한다.
 *
 * ## 왜 이 계약이 중요한가
 *
 * 렌더는 파이프라인의 마지막 단계다. 앞 단계(전사·후킹·자막)에서 이미 크레딧을 쓴 뒤에
 * 도달하므로, 여기서의 거짓 성공은 **결과물 없이 돈만 쓴 상태**를 만든다. 반대로 실패를
 * 실패라고 정확히 말해야 오케스트레이터가 그 단계분을 환불한다.
 *
 * ffmpeg 는 종료 코드 0 으로 끝나고도 산출물을 만들지 않는 경우가 있다. 그래서 "종료 코드"
 * 만으로 판단하지 않고 **파일 존재와 크기**까지 본다.
 */
class FfmpegVideoRendererTest {

    private val request = ClipRenderRequest(
        sourceUrl = "https://example.com/source.mp4",
        startMs = 0,
        endMs = 5_000,
    )

    private fun renderer(executable: String) =
        FfmpegVideoRenderer(executable = executable, timeoutSeconds = 30, crf = 20, preset = "ultrafast")

    /** 인코더 흉내를 내는 실행 파일. 작업 디렉터리에서 실행되므로 상대 경로로 산출물을 만든다. */
    private fun fakeEncoder(body: String): String {
        val dir = Files.createTempDirectory("ongo-fake-ffmpeg-")
        val script = dir.resolve("encoder.sh")
        Files.writeString(script, "#!/bin/sh\n$body\n")
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"))
        return script.toAbsolutePath().toString()
    }

    private fun readableSize(path: Path): Long = Files.size(path)

    /* ── 성공은 실제 산출물이 있을 때만 ───────────────────────────────── */

    @Test
    @DisplayName("산출물이 만들어지면 그 파일과 크기를 돌려준다")
    fun returnsTheRenderedFile() {
        val encoder = fakeEncoder("printf 'rendered-bytes' > clip.mp4")

        val rendered = renderer(encoder).render(request)

        assertTrue(Files.exists(rendered.path), "산출물이 없다: ${rendered.path}")
        assertEquals(readableSize(rendered.path), rendered.sizeBytes)
        assertEquals("video/mp4", rendered.contentType)
        // 호출자가 업로드해야 하므로 성공 경로에서는 파일을 지우지 않는다.
        assertTrue(rendered.sizeBytes > 0)
    }

    /**
     * **종료 코드 0 이어도 파일이 없으면 실패다.**
     *
     * 이것을 성공으로 넘기면 오케스트레이터가 단계를 COMPLETED 로 적고 크레딧을 환불하지
     * 않는다. 사용자에게는 "완료" 라고 표시되지만 내려받을 것이 없다.
     */
    @Test
    @DisplayName("종료 코드 0 이어도 산출물이 없으면 실패로 처리한다")
    fun exitZeroWithoutOutputIsAFailure() {
        val encoder = fakeEncoder("exit 0")

        val error = assertThrows<IllegalStateException> { renderer(encoder).render(request) }

        assertTrue(error.message!!.contains("만들지 않았습니다"), error.message)
    }

    /** 빈 파일도 결과가 아니다 — 크기를 보지 않으면 0바이트 mp4 를 성공으로 돌려준다. */
    @Test
    @DisplayName("산출물이 0바이트면 실패로 처리한다")
    fun emptyOutputIsAFailure() {
        val encoder = fakeEncoder(": > clip.mp4")

        val error = assertThrows<IllegalStateException> { renderer(encoder).render(request) }

        assertTrue(error.message!!.contains("만들지 않았습니다"), error.message)
    }

    @Test
    @DisplayName("인코더가 실패하면 실패로 처리한다")
    fun nonZeroExitIsAFailure() {
        val encoder = fakeEncoder("printf 'partial' > clip.mp4\nexit 1")

        val error = assertThrows<IllegalStateException> { renderer(encoder).render(request) }

        assertTrue(error.message!!.contains("실패"), error.message)
    }

    /** 실패 사유에 서버 경로나 원본 URL 이 섞이면 안 된다 — 사용자가 그대로 보는 문장이다. */
    @Test
    @DisplayName("실패 사유에 경로·원본 URL 을 담지 않는다")
    fun failureReasonLeaksNoInternals() {
        val encoder = fakeEncoder("echo /var/secret/path\nexit 1")

        val message = assertThrows<IllegalStateException> { renderer(encoder).render(request) }.message.orEmpty()

        assertFalse(message.contains("/"), message)
        assertFalse(message.contains("example.com"), message)
    }

    /* ── 가용성과 실제 실행이 어긋나지 않는다 ─────────────────────────── */

    /**
     * **화면은 이 응답으로 렌더 버튼을 노출한다.**
     *
     * 확인과 실행이 서로 다른 실행 파일을 보면 "쓸 수 있다"고 답한 뒤 렌더가 실행 파일을 못
     * 찾아 실패한다. 앞 단계 크레딧을 이미 쓴 사용자가 마지막에 그 사실을 알게 된다.
     * 확인이 통과한 실행 파일로는 렌더도 반드시 시작될 수 있어야 한다.
     */
    @Test
    @DisplayName("가용하다고 답한 인코더로는 렌더도 실행된다")
    fun availabilityAgreesWithRendering() {
        val encoder = fakeEncoder("printf 'rendered-bytes' > clip.mp4")
        val subject = renderer(encoder)

        assertTrue(subject.checkAvailability().available)
        assertTrue(Files.exists(subject.render(request).path))
    }

    /**
     * 반대 방향도 어긋나면 안 된다. 없는 인코더를 "쓸 수 있다"고 답하면 화면이 버튼을 열고,
     * 사용자는 눌러본 뒤에야 실패를 본다.
     */
    @Test
    @DisplayName("없는 인코더는 가용하지 않다고 답하고 렌더도 실패한다")
    fun missingEncoderIsReportedUnavailableAndFailsToRender() {
        val subject = renderer("/nonexistent/ongo-not-an-encoder")

        val availability = subject.checkAvailability()

        assertFalse(availability.available)
        assertTrue(availability.reason!!.isNotBlank())
        // 사용자에게 보여줄 문장이므로 경로가 들어가면 안 된다.
        assertFalse(availability.reason!!.contains("nonexistent"), availability.reason)
        assertThrows<Exception> { subject.render(request) }
    }

    /* ── 뒷정리 ──────────────────────────────────────────────────────── */

    /** 실패한 렌더의 작업 디렉터리를 남기면 디스크가 찬다. 산출물은 원본만큼 크다. */
    @Test
    @DisplayName("실패하면 작업 디렉터리를 남기지 않는다")
    fun failedRenderCleansUpItsWorkDirectory() {
        val encoder = fakeEncoder("printf 'partial' > clip.mp4\nexit 1")
        val before = tempDirCount()

        assertThrows<IllegalStateException> { renderer(encoder).render(request) }

        assertEquals(before, tempDirCount(), "실패한 렌더의 작업 디렉터리가 남았다")
    }

    private fun tempDirCount(): Int {
        val tmp = Path.of(System.getProperty("java.io.tmpdir"))
        return Files.list(tmp).use { stream ->
            stream.filter { it.fileName.toString().startsWith("ongo-shorts-render-") }.count().toInt()
        }
    }
}
