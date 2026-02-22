package com.ongo.infrastructure.transcode

import com.ongo.application.video.VideoTranscodeService
import com.ongo.application.video.VideoTranscodeService.ResizeSpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class FfmpegResizeService : VideoTranscodeService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun resize(inputPath: String, outputPath: String, spec: ResizeSpec): Path {
        log.info("FFmpeg 리사이즈 시작: input={}, ratio={}, target={}x{}", inputPath, spec.aspectRatio, spec.width, spec.height)

        val vf = "crop=min(iw\\,ih*${spec.width}/${spec.height}):min(ih\\,iw*${spec.height}/${spec.width}),scale=${spec.width}:${spec.height}"

        val command = listOf(
            "ffmpeg", "-y",
            "-i", inputPath,
            "-vf", vf,
            "-c:v", "libx264",
            "-preset", "medium",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            outputPath,
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            log.error("FFmpeg 리사이즈 실패 (exit={}): {}", exitCode, output.takeLast(500))
            throw RuntimeException("FFmpeg 리사이즈 실패 (exit code: $exitCode)")
        }

        val outputFile = Path.of(outputPath)
        log.info("FFmpeg 리사이즈 완료: output={}, size={}bytes", outputPath, Files.size(outputFile))
        return outputFile
    }
}
