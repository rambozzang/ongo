package com.ongo.application.video

import java.nio.file.Path

interface VideoTranscodeService {

    data class ResizeSpec(
        val aspectRatio: String,
        val width: Int,
        val height: Int,
    )

    companion object {
        val RESIZE_SPECS = mapOf(
            "9:16" to ResizeSpec("9:16", 1080, 1920),
            "1:1" to ResizeSpec("1:1", 1080, 1080),
            "4:5" to ResizeSpec("4:5", 1080, 1350),
            "16:9" to ResizeSpec("16:9", 1920, 1080),
        )
    }

    fun resize(inputPath: String, outputPath: String, spec: ResizeSpec): Path
}
