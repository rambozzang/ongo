package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType

data class PlatformUploadCapability(
    val platform: Platform,
    val directVideoUpload: Boolean,
    val cloudVideoUpload: Boolean,
    /** Whether the provider itself supports native scheduling. onGo's durable
     * scheduler remains available even when this is false. */
    val scheduling: Boolean,
    val maxFileSizeBytes: Long,
    val maxTitleLength: Int,
    val maxDescriptionLength: Int,
    val maxTagCount: Int,
    val acceptedExtensions: Set<String>,
    val unavailableReason: String? = null,
    /** Maximum length after fields are composed into the provider caption/text. */
    val maxCaptionLength: Int? = null,
    /** Media types the provider can publish through the durable upload path. */
    val acceptedMediaTypes: Set<MediaType> = setOf(MediaType.VIDEO),
)

object PlatformUploadCapabilities {
    private const val GB = 1024L * 1024 * 1024
    private const val MB = 1024L * 1024
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    private val capabilities = mapOf(
        Platform.YOUTUBE to PlatformUploadCapability(
            Platform.YOUTUBE, true, true, true, 2 * GB, 100, 5_000, 500, VIDEO_EXTENSIONS,
        ),
        Platform.TIKTOK to PlatformUploadCapability(
            Platform.TIKTOK, true, true, false, 2 * GB, 2_000, 0, 30, setOf("mp4", "mov", "webm"),
            maxCaptionLength = 2_000,
        ),
        Platform.TWITTER to PlatformUploadCapability(
            Platform.TWITTER, false, false, false, 512 * MB, 280, 0, 30, setOf("mp4", "mov"),
            "X 동영상 업로드는 현재 OAuth 2.0 계약만으로 확인할 수 없어 연결·텍스트 기능과 분리해 잠시 비활성화했습니다.",
            maxCaptionLength = 280,
        ),
        Platform.INSTAGRAM to PlatformUploadCapability(
            Platform.INSTAGRAM, true, true, false, 500 * MB, 2_200, 0, 30, VIDEO_EXTENSIONS + IMAGE_EXTENSIONS,
            "Instagram은 임시 오브젝트 URL을 통해 Graph API로 업로드합니다.",
            maxCaptionLength = 2_200,
            acceptedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE),
        ),
        Platform.THREADS to PlatformUploadCapability(
            Platform.THREADS, true, true, false, 500 * MB, 500, 0, 30, VIDEO_EXTENSIONS + IMAGE_EXTENSIONS,
            "Threads는 임시 오브젝트 URL을 통해 Graph API로 업로드합니다.",
            maxCaptionLength = 500,
            acceptedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE),
        ),
        Platform.FACEBOOK to PlatformUploadCapability(
            Platform.FACEBOOK, false, true, false, 2 * GB, 255, 5_000, 30, setOf("mp4", "mov"),
            "Facebook은 클라우드 임시 URL을 통해 업로드합니다.",
            maxCaptionLength = 5_000,
        ),
        Platform.PINTEREST to PlatformUploadCapability(
            Platform.PINTEREST, false, true, false, 2 * GB, 100, 800, 0, setOf("mp4", "mov", "m4v"),
            "Pinterest는 연결된 보드와 커버 이미지가 있는 동영상 Pin으로 업로드합니다.",
        ),
        Platform.LINKEDIN to PlatformUploadCapability(
            Platform.LINKEDIN, false, true, false, 500 * MB, 3_000, 3_000, 0, setOf("mp4"),
            "LinkedIn Videos API의 4MB 파트 업로드와 처리 완료 확인을 사용합니다.",
            maxCaptionLength = 3_000,
        ),
        Platform.WORDPRESS to PlatformUploadCapability(
            Platform.WORDPRESS, false, true, false, 2 * GB, 200, 5_000, 100, VIDEO_EXTENSIONS,
            "WordPress.com 사이트의 미디어 허용 형식과 저장 용량 정책을 따릅니다.",
        ),
        Platform.DAILYMOTION to PlatformUploadCapability(
            Platform.DAILYMOTION, false, true, false, 2 * GB, 255, 3_000, 150, setOf("mp4", "mov"),
            "Dailymotion API v2 세션 업로드와 프로필 영상 생성을 사용합니다.",
        ),
        Platform.VIMEO to PlatformUploadCapability(
            Platform.VIMEO, false, true, false, 300 * GB, 128, 5_000, 0, setOf("mp4", "mov", "webm"),
            "Vimeo pull 업로드는 공개 또는 장시간 유효한 presigned 파일 URL을 사용합니다.",
        ),
        Platform.TUMBLR to PlatformUploadCapability(
            Platform.TUMBLR, false, true, false, 500 * MB, 5_000, 5_000, 30, setOf("mp4", "mov"),
            "Tumblr NPF native video multipart 업로드를 사용합니다. 계정별 일일 업로드 제한이 적용됩니다.",
        ),
    )

    fun get(platform: Platform): PlatformUploadCapability? = capabilities[platform]
    fun all(): List<PlatformUploadCapability> = capabilities.values.toList()
}
