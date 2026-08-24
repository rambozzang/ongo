package com.ongo.infrastructure.render

import com.ongo.application.ai.audio.AudioPart
import com.ongo.application.ai.audio.AudioPreparationException
import com.ongo.application.ai.audio.PreparedAudio
import com.ongo.application.ai.audio.TranscriptionAudioPort
import com.ongo.infrastructure.runtime.RuntimeExecutableResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToLong

/**
 * ffmpeg 로 오디오만 뽑아 조각으로 나눈다.
 *
 * ## 한 번의 호출로 추출과 분할을 같이 한다
 *
 * `-vn` 으로 영상 트랙을 버리고 `-f segment` 로 자른다. 추출한 뒤 다시 자르면 원본을
 * 두 번 읽어야 하는데, 원본은 원격 URL 이라 그 비용이 그대로 네트워크 비용이 된다.
 *
 * ## 오프셋을 추정하지 않는다
 *
 * `-segment_time N` 은 정확히 N 초에서 자르지 않는다. 그 지점 **이후 첫 프레임**에서
 * 자르므로 조각마다 조금씩 어긋나고, 그 오차가 뒤로 갈수록 쌓인다. 조각 번호 × N 으로
 * 오프셋을 계산하면 자막이 뒤로 밀린다.
 *
 * 그래서 `-segment_list` 로 인코더가 **실제로 자른 시각**을 받아 그 값을 쓴다.
 *
 * ## 셸을 거치지 않는다
 *
 * `sourceUrl` 은 외부에서 온 값이다. [ProcessBuilder] 에 인자 배열을 넘겨 셸이 메타문자를
 * 해석할 통로 자체를 없앤다. [FfmpegVideoRenderer] 와 같은 이유다.
 */
@Component
class FfmpegTranscriptionAudioAdapter(
    @param:Value("\${shorts.render.ffmpeg-path:ffmpeg}")
    private val executable: String,
    /**
     * 길이 측정용 실행 파일. ffmpeg 배포에 같이 들어 있으므로 기본값은 `ffprobe` 이고,
     * 운영 호스트는 `application-prod.yml` 이 절대 경로를 준다. PATH 에 없을 수 있어
     * [RuntimeExecutableResolver] 가 표준 설치 경로도 확인한다.
     */
    @param:Value("\${shorts.transcribe.ffprobe-path:ffprobe}")
    private val probeExecutable: String,
    @param:Value("\${shorts.transcribe.part-seconds:600}")
    private val partSeconds: Long,
    @param:Value("\${shorts.transcribe.prepare-timeout-seconds:1800}")
    private val timeoutSeconds: Long,
    @param:Value("\${shorts.transcribe.probe-timeout-seconds:30}")
    private val probeTimeoutSeconds: Long,
    @param:Value("\${shorts.transcribe.audio-bitrate:64k}")
    private val audioBitrate: String,
) : TranscriptionAudioPort {

    /**
     * 설정값이 그대로 실행 가능한 경로가 아닐 수 있다(운영 호스트는 PATH 에 ffmpeg 이 없다).
     * 가용성 판정과 실제 실행이 **같은** 실행 파일을 봐야 "쓸 수 있다더니 실패"가 없다.
     */
    private val resolvedExecutable = RuntimeExecutableResolver.resolve(executable)
    private val resolvedProbeExecutable = RuntimeExecutableResolver.resolve(probeExecutable)

    private val log = LoggerFactory.getLogger(javaClass)
    private val cachedAvailability = AtomicReference<Pair<Instant, Boolean>?>(null)

    init {
        require(partSeconds > 0) { "shorts.transcribe.part-seconds must be positive" }
    }

    override fun isAvailable(): Boolean {
        cachedAvailability.get()?.let { (at, value) ->
            if (Duration.between(at, Instant.now()) < AVAILABILITY_TTL) return value
        }
        val result = probe()
        cachedAvailability.set(Instant.now() to result)
        return result
    }

    private fun probe(): Boolean =
        try {
            val dir = Files.createTempDirectory("ongo-stt-probe-")
            try {
                runProcess(listOf(resolvedExecutable, "-version"), dir, PROBE_TIMEOUT_SECONDS)
                true
            } finally {
                deleteRecursively(dir)
            }
        } catch (e: Exception) {
            log.warn("전사용 인코더를 사용할 수 없다. executable={} 원인={}", resolvedExecutable, e.message)
            false
        }

    /**
     * ffprobe 로 재생 길이를 **읽는다**.
     *
     * `-v error` 로 배너를 지우고 `-show_entries format=duration` + `noprint_wrappers`
     * 로 숫자 한 줄만 받는다. 파싱할 것이 적을수록 오독할 여지도 적다.
     *
     * 실패는 전부 예외다. 못 읽었는데 0 을 돌려주면 호출자는 "0초짜리라 상한 이하"로
     * 판단해 통과시킨다. 프로브가 없다는 사실이 원본이 짧다는 증거가 될 수는 없다.
     */
    override fun probeDurationMs(sourceUrl: String): Long {
        val dir = Files.createTempDirectory("ongo-stt-duration-")
        val output = try {
            runProcess(
                listOf(
                    resolvedProbeExecutable,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    sourceUrl,
                ),
                dir,
                probeTimeoutSeconds,
            )
        } catch (e: AudioPreparationException) {
            throw e
        } catch (e: Exception) {
            // 실행 파일 자체가 없으면 ProcessBuilder 가 IOException 을 던진다.
            log.warn("길이 프로브를 실행할 수 없다. executable={} 원인={}", resolvedProbeExecutable, e.message)
            throw AudioPreparationException("원본 길이를 확인할 수 없습니다", e)
        } finally {
            deleteRecursively(dir)
        }

        /*
         * 라이브 스트림이나 컨테이너 헤더가 깨진 파일은 "N/A" 를 준다. 이걸 0 으로 읽으면
         * 상한 검사를 그대로 통과한다 — 정확히 우리가 막으려던 경우다.
         */
        val seconds = output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotEmpty() }
            ?.toDoubleOrNull()
        if (seconds == null || !seconds.isFinite() || seconds <= 0.0) {
            log.warn("길이 프로브 출력을 길이로 읽을 수 없다. output={}", output.take(200))
            throw AudioPreparationException("원본 길이를 확인할 수 없습니다")
        }
        return (seconds * 1000).roundToLong()
    }

    override fun prepare(sourceUrl: String): PreparedAudio {
        val workDir = Files.createTempDirectory("ongo-stt-audio-")
        try {
            val listFile = workDir.resolve(SEGMENT_LIST_NAME)
            runProcess(buildCommand(sourceUrl), workDir, timeoutSeconds)

            val parts = readSegmentList(workDir, listFile)
            if (parts.isEmpty()) {
                // 인코더가 0 으로 끝나고도 조각을 못 만드는 경우가 있다(오디오 트랙이 없는 원본 등).
                // 빈 결과를 성공으로 넘기면 자막 없는 영상이 조용히 만들어진다.
                throw AudioPreparationException("원본에서 오디오를 찾지 못했습니다")
            }
            return TempDirPreparedAudio(workDir, parts)
        } catch (e: Exception) {
            // 실패 경로에서는 우리가 만든 것을 우리가 치운다. 호출자는 close 할 대상을 못 받는다.
            deleteRecursively(workDir)
            throw if (e is AudioPreparationException) e
            else AudioPreparationException("오디오 추출에 실패했습니다", e)
        }
    }

    /**
     * 모노 16kHz 는 음성 인식이 실제로 쓰는 해상도다. 스테레오·48kHz 를 그대로 보내면
     * 인식 품질은 그대로인데 전송량만 몇 배가 된다.
     */
    private fun buildCommand(sourceUrl: String): List<String> = listOf(
        resolvedExecutable,
        "-nostdin",
        "-y",
        "-i", sourceUrl,
        "-vn",                       // 영상 트랙 제거. 전사에는 쓰이지 않는다
        "-ac", "1",
        "-ar", "16000",
        "-c:a", "libmp3lame",
        "-b:a", audioBitrate,
        "-f", "segment",
        "-segment_time", partSeconds.toString(),
        "-segment_list", SEGMENT_LIST_NAME,
        "-segment_list_type", "csv",
        PART_PATTERN,
    )

    /**
     * `part-0000.mp3,0.000000,600.024000` 형식. 두 번째 열이 실제 시작 시각(초)이다.
     * 형식이 어긋난 줄은 조용히 버리지 않고 실패시킨다 — 오프셋이 틀리면 자막이 전부 밀린다.
     */
    private fun readSegmentList(workDir: Path, listFile: Path): List<AudioPart> {
        if (!Files.exists(listFile)) return emptyList()

        return Files.readAllLines(listFile, StandardCharsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { line ->
                val columns = line.split(',')
                if (columns.size < 2) {
                    throw AudioPreparationException("인코더가 알 수 없는 조각 목록을 만들었습니다")
                }
                val startSeconds = columns[1].trim().toDoubleOrNull()
                    ?: throw AudioPreparationException("인코더가 알 수 없는 조각 시작 시각을 만들었습니다")
                val partPath = workDir.resolve(columns[0].trim())
                if (!Files.isRegularFile(partPath)) {
                    throw AudioPreparationException("인코더가 조각 파일을 만들지 않았습니다")
                }
                AudioPart(
                    resource = FileSystemResource(partPath),
                    offsetMs = (startSeconds * 1000).roundToLong(),
                )
            }
    }

    /** @return 표준 출력 + 표준 에러. 길이 프로브는 이 값을 파싱해야 하므로 버리지 않는다. */
    private fun runProcess(command: List<String>, directory: Path, timeout: Long): String {
        // 셸을 거치지 않는다. 인자 배열이라 메타문자가 해석될 통로가 없다.
        val process = ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start()

        val captured = StringBuilder()
        val reader = Thread.ofVirtual().start {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { r ->
                val buffer = CharArray(8_192)
                while (true) {
                    val read = r.read(buffer)
                    if (read == -1) break
                    if (captured.length < MAX_OUTPUT) {
                        captured.append(buffer, 0, read.coerceAtMost(MAX_OUTPUT - captured.length))
                    }
                }
            }
        }

        if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor(5, TimeUnit.SECONDS)
            reader.join(1_000)
            throw AudioPreparationException("오디오 추출 시간이 초과되었습니다")
        }
        reader.join(1_000)

        if (process.exitValue() != 0) {
            // 출력에 서명이 붙은 원본 URL 이 섞이므로 예외 메시지에 넣지 않는다.
            log.warn("전사용 인코더 실패. exit={} output={}", process.exitValue(), captured.toString().takeLast(500))
            throw AudioPreparationException("오디오 추출에 실패했습니다")
        }
        return captured.toString()
    }

    private fun deleteRecursively(dir: Path) {
        runCatching {
            if (!Files.exists(dir)) return
            Files.walk(dir).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.delete(it) } }
            }
        }
    }

    /** 조각은 임시 디렉터리 통째로 정리한다. 조각 파일만 지우면 목록 파일이 남는다. */
    private inner class TempDirPreparedAudio(
        private val workDir: Path,
        override val parts: List<AudioPart>,
    ) : PreparedAudio {
        override fun close() = deleteRecursively(workDir)
    }

    companion object {
        private val AVAILABILITY_TTL: Duration = Duration.ofMinutes(5)
        private const val PROBE_TIMEOUT_SECONDS = 5L
        private const val MAX_OUTPUT = 16_384
        private const val SEGMENT_LIST_NAME = "parts.csv"
        private const val PART_PATTERN = "part-%05d.mp3"
    }
}
