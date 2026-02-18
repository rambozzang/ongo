package com.ongo.domain.video.entity

import java.time.LocalDateTime

data class VideoMediaInfo(
    val id: Long? = null,
    val videoId: Long,
    // Video stream
    val videoCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val bitrateKbps: Int? = null,
    val durationMs: Long? = null,
    val colorSpace: String? = null,
    val pixelFormat: String? = null,
    val profile: String? = null,
    // Audio stream
    val audioCodec: String? = null,
    val audioBitrateKbps: Int? = null,
    val sampleRate: Int? = null,
    val audioChannels: Int? = null,
    // Container
    val formatName: String? = null,
    val fileSizeBytes: Long? = null,
    // Raw
    val rawJson: String? = null,
    val createdAt: LocalDateTime? = null,
) {
    val isVertical: Boolean
        get() = (height ?: 0) > (width ?: 0)

    val resolutionLabel: String
        get() = when {
            (width ?: 0) >= 3840 -> "4K"
            (width ?: 0) >= 1920 || (height ?: 0) >= 1920 -> "1080p"
            (width ?: 0) >= 1280 || (height ?: 0) >= 1280 -> "720p"
            else -> "${width}x${height}"
        }

    fun isCompatibleWith(spec: PlatformTranscodeSpec): Boolean {
        if (videoCodec == null || width == null || height == null) return false
        val codecMatch = spec.acceptableCodecs.contains(videoCodec)
        val resMatch = width == spec.width && height == spec.height
        val fpsMatch = spec.maxFps == null || (fps ?: 0.0) <= spec.maxFps
        val bitrateMatch = spec.maxBitrateKbps == null || (bitrateKbps ?: 0) <= spec.maxBitrateKbps
        return codecMatch && resMatch && fpsMatch && bitrateMatch
    }

    fun needsTranscoding(spec: PlatformTranscodeSpec): Boolean = !isCompatibleWith(spec)
}

data class PlatformTranscodeSpec(
    val width: Int,
    val height: Int,
    val maxBitrateKbps: Int? = null,
    val maxFps: Double? = null,
    val acceptableCodecs: List<String> = listOf("h264"),
)
