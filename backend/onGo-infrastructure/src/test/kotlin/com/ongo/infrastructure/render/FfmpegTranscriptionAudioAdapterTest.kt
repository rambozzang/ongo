package com.ongo.infrastructure.render

import com.ongo.application.ai.audio.AudioPreparationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 어댑터가 **프로세스를 실제로 띄워** 결과를 읽는지 본다.
 *
 * ## 왜 가짜 실행 파일인가
 *
 * 실제 ffmpeg 을 요구하면 이 테스트는 개발 기계와 CI 설치 상태에 따라 켜졌다 꺼졌다 한다.
 * 그렇다고 private 헬퍼만 들여다보면 "프로세스를 띄워 파일을 만들고 그 목록을 읽는다"는
 * 정작 중요한 부분이 검증되지 않는다.
 *
 * 그래서 ffmpeg 이 하는 일(조각 파일 생성 + `-segment_list` CSV 기록)을 흉내 내는 결정적인
 * 스크립트를 임시 디렉터리에 만들어 실행 파일로 지정한다. 어댑터는 이것이 진짜 인코더인지
 * 모르며, 인자 배열을 넘기고 작업 디렉터리에서 산출물을 읽는 경로는 운영과 동일하다.
 *
 * POSIX 셸 스크립트라 Windows 에서는 건너뛴다.
 */
@DisabledOnOs(OS.WINDOWS)
class FfmpegTranscriptionAudioAdapterTest {

    private lateinit var fixtureDir: Path

    @BeforeEach
    fun setUp() {
        fixtureDir = Files.createTempDirectory("ongo-stt-fixture-")
    }

    @AfterEach
    fun tearDown() {
        Files.walk(fixtureDir).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
        }
    }

    private fun adapter(
        executable: String = "/nonexistent/ongo-test-ffmpeg",
        probeExecutable: String = "/nonexistent/ongo-test-ffprobe",
        partSeconds: Long = 600,
    ) = FfmpegTranscriptionAudioAdapter(
        executable = executable,
        probeExecutable = probeExecutable,
        partSeconds = partSeconds,
        timeoutSeconds = 5,
        probeTimeoutSeconds = 5,
        audioBitrate = "64k",
    )

    /** 임시 디렉터리에 실행 가능한 POSIX 스크립트를 만든다. */
    private fun script(name: String, body: String): String {
        val path = fixtureDir.resolve(name)
        Files.writeString(path, "#!/bin/sh\n$body\n", StandardCharsets.UTF_8)
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"))
        return path.toString()
    }

    /**
     * ffmpeg 의 관측 가능한 계약만 흉내 낸다: 조각 파일을 만들고 CSV 목록을 남긴다.
     *
     * 오프셋은 조각 번호 × 600 이 **아닌** 값을 쓴다. 어댑터가 CSV 를 읽지 않고 번호로
     * 계산하면 이 값이 나올 수 없다.
     */
    private fun fakeFfmpeg() = script(
        "fake-ffmpeg",
        """
        printf 'p0' > part-00000.mp3
        printf 'p1' > part-00001.mp3
        printf 'p2' > part-00002.mp3
        printf 'part-00000.mp3,0.000000,600.024000\n' > parts.csv
        printf 'part-00001.mp3,600.024000,1200.048000\n' >> parts.csv
        printf 'part-00002.mp3,1200.048000,1512.500000\n' >> parts.csv
        exit 0
        """.trimIndent(),
    )

    // ---- 정상 경로: 프로세스를 띄워 여러 조각을 만들고 CSV 오프셋을 그대로 읽는다 ----

    @Test
    fun `조각 파일을 만들고 CSV 시작 시각을 오프셋으로 읽는다`() {
        val prepared = adapter(executable = fakeFfmpeg()).prepare("https://storage.test/source.mp4")

        prepared.use {
            assertEquals(3, it.parts.size)
            // 600 × n 이 아니라 CSV 에 적힌 실제 절단 시각이어야 한다.
            assertEquals(listOf(0L, 600_024L, 1_200_048L), it.parts.map { part -> part.offsetMs })

            // 조각은 프로세스가 만든 실제 로컬 파일이어야 한다.
            it.parts.forEachIndexed { index, part ->
                assertTrue(part.resource.exists(), "조각 $index 파일이 없다")
                assertEquals("p$index", part.resource.inputStream.use { s -> s.readBytes().decodeToString() })
            }
        }
    }

    /* 임시 디렉터리를 안 지우면 원본만 한 오디오가 디스크에 계속 쌓인다. */
    @Test
    fun `close 하면 조각과 임시 디렉터리가 사라진다`() {
        val prepared = adapter(executable = fakeFfmpeg()).prepare("https://storage.test/source.mp4")
        val partPaths = prepared.parts.map { it.resource.file.toPath() }
        val workDir = partPaths.first().parent
        assertTrue(Files.isDirectory(workDir))

        prepared.close()

        assertFalse(Files.exists(workDir), "임시 디렉터리가 남았다")
        partPaths.forEach { assertFalse(Files.exists(it), "조각이 남았다: $it") }
    }

    @Test
    fun `조각이 하나도 안 나오면 성공으로 넘기지 않는다`() {
        // exit 0 인데 산출물이 없는 경우. 빈 결과를 통과시키면 자막 없는 영상이 조용히 만들어진다.
        val silent = script("silent-ffmpeg", "exit 0")

        assertFailsWith<AudioPreparationException> {
            adapter(executable = silent).prepare("https://storage.test/source.mp4")
        }
    }

    @Test
    fun `CSV 시작 시각이 숫자가 아니면 실패시킨다`() {
        // 오프셋을 0 으로 눙치면 자막이 전부 영상 앞머리로 몰린다.
        val broken = script(
            "broken-ffmpeg",
            """
            printf 'p0' > part-00000.mp3
            printf 'part-00000.mp3,N/A,600.000000\n' > parts.csv
            exit 0
            """.trimIndent(),
        )

        assertFailsWith<AudioPreparationException> {
            adapter(executable = broken).prepare("https://storage.test/source.mp4")
        }
    }

    // ---- 길이 프로브 ----

    @Test
    fun `프로브 출력의 초를 밀리초 길이로 읽는다`() {
        val probe = script("fake-ffprobe", "echo 1512.500000")

        assertEquals(1_512_500L, adapter(probeExecutable = probe).probeDurationMs("https://storage.test/s.mp4"))
    }

    /*
     * 라이브 스트림이나 헤더가 깨진 파일은 "N/A" 를 준다. 이걸 0 으로 읽으면 길이 상한
     * 검사를 그대로 통과한다 — 정확히 막으려던 경우다.
     */
    @Test
    fun `프로브가 N-A 를 주면 길이 0 이 아니라 실패로 처리한다`() {
        val probe = script("na-ffprobe", "echo N/A")

        assertFailsWith<AudioPreparationException> {
            adapter(probeExecutable = probe).probeDurationMs("https://storage.test/s.mp4")
        }
    }

    @Test
    fun `프로브가 아무것도 출력하지 않으면 실패로 처리한다`() {
        val probe = script("empty-ffprobe", "exit 0")

        assertFailsWith<AudioPreparationException> {
            adapter(probeExecutable = probe).probeDurationMs("https://storage.test/s.mp4")
        }
    }

    @Test
    fun `프로브 실행 파일이 없으면 길이를 안다고 하지 않는다`() {
        assertFailsWith<AudioPreparationException> {
            adapter().probeDurationMs("https://storage.test/s.mp4")
        }
    }

    @Test
    fun `프로브가 0 이하를 주면 실패로 처리한다`() {
        val probe = script("zero-ffprobe", "echo 0.000000")

        assertFailsWith<AudioPreparationException> {
            adapter(probeExecutable = probe).probeDurationMs("https://storage.test/s.mp4")
        }
    }

    @Test
    fun `길이 프로브는 임시 디렉터리를 남기지 않는다`() {
        val before = sttTempDirs()
        val probe = script("fake-ffprobe2", "echo 12.000000")

        adapter(probeExecutable = probe).probeDurationMs("https://storage.test/s.mp4")

        assertEquals(before, sttTempDirs())
    }

    // ---- 실패 경로 ----

    @Test
    fun `인코더가 없으면 사용 가능하다고 말하지 않는다`() {
        assertFalse(adapter().isAvailable())
    }

    /*
     * 예외를 삼키고 빈 목록을 돌려주면 자막 없는 영상이 조용히 만들어진다.
     * 실패는 실패로 나와야 유스케이스가 모델 호출을 건너뛴다.
     */
    @Test
    fun `추출에 실패하면 AudioPreparationException 을 던진다`() {
        assertFailsWith<AudioPreparationException> {
            adapter().prepare("https://storage.test/source.mp4")
        }
    }

    /* 실패 경로에서는 호출자가 close 할 대상을 못 받는다. 우리가 치우지 않으면 아무도 안 치운다. */
    @Test
    fun `추출에 실패하면 임시 디렉터리를 남기지 않는다`() {
        val before = sttTempDirs()

        assertFailsWith<AudioPreparationException> {
            adapter().prepare("https://storage.test/source.mp4")
        }

        assertEquals(before, sttTempDirs())
    }

    @Test
    fun `조각 길이가 0 이하면 생성 시점에 거부한다`() {
        // 0 이면 ffmpeg 가 무한히 조각을 만들거나 즉시 실패한다. 설정 실수를 기동 때 잡는다.
        assertFailsWith<IllegalArgumentException> { adapter(partSeconds = 0) }
    }

    private fun sttTempDirs(): Set<String> {
        val tmp = Path.of(System.getProperty("java.io.tmpdir"))
        if (!Files.isDirectory(tmp)) return emptySet()
        return Files.list(tmp).use { stream ->
            stream.filter { it.name.startsWith("ongo-stt-") }.map { it.name }.toList().toSet()
        }
    }
}
