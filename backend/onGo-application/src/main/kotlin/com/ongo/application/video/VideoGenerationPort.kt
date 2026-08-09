package com.ongo.application.video

import java.nio.file.Path

/** 서버가 생성한 영상 파일을 반환하는 인코더 경계. 구현체는 임시 파일만 소유한다. */
interface VideoGenerationPort {
    fun generate(request: VideoGenerationSpec): GeneratedVideoFile
}

data class VideoGenerationSpec(
    val prompt: String,
    val orientation: VideoOrientation,
)

enum class VideoOrientation(val width: Int, val height: Int) {
    VERTICAL(1080, 1920),
    HORIZONTAL(1920, 1080),
}

data class GeneratedVideoFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String = "video/mp4",
)
