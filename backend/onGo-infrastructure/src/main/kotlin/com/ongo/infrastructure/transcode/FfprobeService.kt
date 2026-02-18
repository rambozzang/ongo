package com.ongo.infrastructure.transcode

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.video.MediaProbeService
import com.ongo.domain.video.entity.VideoMediaInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FfprobeService(
    private val objectMapper: ObjectMapper,
) : MediaProbeService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun probe(filePath: String, videoId: Long): VideoMediaInfo {
        log.info("FFprobe 분석 시작: videoId={}, path={}", videoId, filePath)

        val command = listOf(
            "ffprobe",
            "-v", "quiet",
            "-print_format", "json",
            "-show_format",
            "-show_streams",
            filePath,
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            log.error("FFprobe 실패 (exit={}): {}", exitCode, output.takeLast(500))
            throw RuntimeException("FFprobe 분석 실패 (exit code: $exitCode)")
        }

        val json = objectMapper.readTree(output)
        return parseProbeResult(json, videoId, output)
    }

    private fun parseProbeResult(json: JsonNode, videoId: Long, rawOutput: String): VideoMediaInfo {
        val streams = json.get("streams") ?: objectMapper.createArrayNode()
        val format = json.get("format")

        val videoStream = streams.firstOrNull { it.get("codec_type")?.asText() == "video" }
        val audioStream = streams.firstOrNull { it.get("codec_type")?.asText() == "audio" }

        val fps = videoStream?.let { parseFps(it) }
        val videoBitrate = videoStream?.get("bit_rate")?.asText()?.toLongOrNull()?.let { (it / 1000).toInt() }
            ?: format?.get("bit_rate")?.asText()?.toLongOrNull()?.let { (it / 1000).toInt() }
        val durationMs = format?.get("duration")?.asText()?.toDoubleOrNull()?.let { (it * 1000).toLong() }
            ?: videoStream?.get("duration")?.asText()?.toDoubleOrNull()?.let { (it * 1000).toLong() }

        return VideoMediaInfo(
            videoId = videoId,
            videoCodec = videoStream?.get("codec_name")?.asText(),
            width = videoStream?.get("width")?.asInt(),
            height = videoStream?.get("height")?.asInt(),
            fps = fps,
            bitrateKbps = videoBitrate,
            durationMs = durationMs,
            colorSpace = videoStream?.get("color_space")?.asText(),
            pixelFormat = videoStream?.get("pix_fmt")?.asText(),
            profile = videoStream?.get("profile")?.asText(),
            audioCodec = audioStream?.get("codec_name")?.asText(),
            audioBitrateKbps = audioStream?.get("bit_rate")?.asText()?.toLongOrNull()?.let { (it / 1000).toInt() },
            sampleRate = audioStream?.get("sample_rate")?.asText()?.toIntOrNull(),
            audioChannels = audioStream?.get("channels")?.asInt(),
            formatName = format?.get("format_name")?.asText(),
            fileSizeBytes = format?.get("size")?.asText()?.toLongOrNull(),
            rawJson = rawOutput,
        )
    }

    private fun parseFps(videoStream: JsonNode): Double? {
        val rFrameRate = videoStream.get("r_frame_rate")?.asText() ?: return null
        val parts = rFrameRate.split("/")
        if (parts.size != 2) return rFrameRate.toDoubleOrNull()
        val num = parts[0].toDoubleOrNull() ?: return null
        val den = parts[1].toDoubleOrNull() ?: return null
        return if (den > 0) num / den else null
    }
}
