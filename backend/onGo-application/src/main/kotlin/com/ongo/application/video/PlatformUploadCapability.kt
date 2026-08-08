package com.ongo.application.video

import com.ongo.common.enums.Platform

data class PlatformUploadCapability(
    val platform: Platform,
    val directVideoUpload: Boolean,
    val cloudVideoUpload: Boolean,
    val scheduling: Boolean,
    val maxFileSizeBytes: Long,
    val maxTitleLength: Int,
    val maxDescriptionLength: Int,
    val maxTagCount: Int,
    val acceptedExtensions: Set<String>,
    val unavailableReason: String? = null,
)

object PlatformUploadCapabilities {
    private const val GB = 1024L * 1024 * 1024
    private const val MB = 1024L * 1024
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")

    private val capabilities = mapOf(
        Platform.YOUTUBE to PlatformUploadCapability(
            Platform.YOUTUBE, true, true, true, 2 * GB, 100, 5_000, 500, VIDEO_EXTENSIONS,
        ),
        Platform.TIKTOK to PlatformUploadCapability(
            Platform.TIKTOK, true, true, false, 2 * GB, 2_200, 0, 30, setOf("mp4", "mov", "webm"),
        ),
        Platform.NAVER_CLIP to PlatformUploadCapability(
            Platform.NAVER_CLIP, true, true, true, 2 * GB, 100, 1_000, 30, setOf("mp4", "mov"),
        ),
        Platform.TWITTER to PlatformUploadCapability(
            Platform.TWITTER, true, true, false, 512 * MB, 280, 0, 30, setOf("mp4", "mov"),
        ),
        Platform.INSTAGRAM to PlatformUploadCapability(
            Platform.INSTAGRAM, true, true, false, 500 * MB, 2_200, 0, 30, setOf("mp4", "mov"),
            "Instagram은 임시 오브젝트 URL을 통해 Graph API로 업로드합니다.",
        ),
        Platform.THREADS to PlatformUploadCapability(
            Platform.THREADS, true, true, false, 500 * MB, 500, 0, 30, setOf("mp4", "mov"),
            "Threads는 임시 오브젝트 URL을 통해 Graph API로 업로드합니다.",
        ),
        Platform.FACEBOOK to PlatformUploadCapability(
            Platform.FACEBOOK, false, true, false, 2 * GB, 255, 5_000, 30, setOf("mp4", "mov"),
            "Facebook은 클라우드 임시 URL을 통해 업로드합니다.",
        ),
        Platform.PINTEREST to PlatformUploadCapability(
            Platform.PINTEREST, false, true, false, 2 * GB, 100, 800, 0, setOf("mp4", "mov", "m4v"),
            "Pinterest는 연결된 보드와 커버 이미지가 있는 동영상 Pin으로 업로드합니다.",
        ),
        Platform.LINKEDIN to PlatformUploadCapability(
            Platform.LINKEDIN, false, true, false, 500 * MB, 3_000, 3_000, 0, setOf("mp4"),
            "LinkedIn Videos API의 4MB 파트 업로드와 처리 완료 확인을 사용합니다.",
        ),
    )

    fun get(platform: Platform): PlatformUploadCapability? = capabilities[platform]
    fun all(): List<PlatformUploadCapability> = capabilities.values.toList()
}
