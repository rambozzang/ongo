package com.ongo.application.video

import com.ongo.common.enums.Platform
import com.ongo.common.enums.MediaType
import com.ongo.common.util.FileValidationUtil

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
    /** Whether this deployment has the provider credentials needed to use it. */
    val configurationAvailable: Boolean = true,
    /** Safe, user-facing explanation when deployment configuration is missing. */
    val configurationUnavailableReason: String? = null,
)

object PlatformUploadCapabilities {
    private const val GB = 1024L * 1024 * 1024
    private const val MB = 1024L * 1024
    private const val DECIMAL_GB = 1_000_000_000L
    private const val DECIMAL_MB = 1_000_000L
    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /** 플랫폼이 더 큰 파일을 받아도 우리 직접 업로드 상한(10GiB)을 넘기지 않는다. */
    private fun providerLimit(bytes: Long): Long = minOf(bytes, FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES)

    /*
     * 아래 한도는 각 플랫폼 공개 문서를 **기억에 근거해** 2026-09-23 에 적은 값이다 — 외부 조회는 하지 않았다.
     * 계정·게시 흐름에 따라 달라지는 값은 보수적으로 두었고, 판매 전 공식 문서 확인이 필요하다
     * (docs/work/upload-10gb-codex-report.md "확인 필요").
     */
    private val capabilities = mapOf(
        // YouTube Data API v3 Videos: insert — 최대 256GB.
        Platform.YOUTUBE to PlatformUploadCapability(
            Platform.YOUTUBE, true, true, true, providerLimit(256 * DECIMAL_GB), 100, 5_000, 500, VIDEO_EXTENSIONS,
        ),
        // TikTok Content Posting API(Direct Post) — 최대 4GB.
        Platform.TIKTOK to PlatformUploadCapability(
            Platform.TIKTOK, true, true, false, providerLimit(4 * DECIMAL_GB), 2_000, 0, 30, setOf("mp4", "mov", "webm"),
            maxCaptionLength = 2_000,
        ),
        Platform.TWITTER to PlatformUploadCapability(
            Platform.TWITTER, false, false, false, providerLimit(512 * MB), 280, 0, 30, setOf("mp4", "mov"),
            "X 동영상 업로드는 현재 OAuth 2.0 계약만으로 확인할 수 없어 연결·텍스트 기능과 분리해 잠시 비활성화했습니다.",
            maxCaptionLength = 280,
        ),
        /*
         * 네이버는 Clip 업로드 공개 API를 제공하지 않는다. X 와 달리 **일시적인 비활성화가
         * 아니라** 우리가 만들 수 없는 기능이므로 이유 문구도 그렇게 적는다.
         *
         * 이 항목을 map 에서 빼 두면 게시 게이트는 "지원하지 않는 플랫폼입니다" 라는 이유
         * 없는 예외만 내고, 남아 있는 과거 업로드 행은 capability 조회가 null 이라
         * StreamWriter 를 못 고른 채 NaverClipClient.uploadVideo() 로 흘러 개발자용
         * 마이그레이션 문구를 고객 실패 사유로 남겼다. 명시적으로 등재해 두 경로가 같은
         * 문장을 쓰게 한다.
         */
        Platform.NAVER_CLIP to PlatformUploadCapability(
            Platform.NAVER_CLIP, false, false, false, providerLimit(2 * GB), 100, 0, 0, VIDEO_EXTENSIONS,
            "Naver Clip은 공개 업로드 API를 제공하지 않아 onGo에서 업로드할 수 없습니다.",
        ),
        // Instagram Graph API Reels 게시 사양 — 영상당 최대 1GB.
        Platform.INSTAGRAM to PlatformUploadCapability(
            Platform.INSTAGRAM, true, true, false, providerLimit(DECIMAL_GB), 2_200, 0, 30, VIDEO_EXTENSIONS + IMAGE_EXTENSIONS,
            "Instagram은 임시 오브젝트 URL을 통해 Graph API로 업로드합니다.",
            maxCaptionLength = 2_200,
            acceptedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE),
        ),
        // Threads API 는 게시 흐름마다 한도가 달라, 정확한 흐름을 확인할 때까지 500MB 로 보수적으로 둔다.
        Platform.THREADS to PlatformUploadCapability(
            Platform.THREADS, true, true, false, providerLimit(500 * DECIMAL_MB), 500, 0, 30, VIDEO_EXTENSIONS + IMAGE_EXTENSIONS,
            "Threads는 임시 오브젝트 URL을 통해 Graph API로 업로드합니다.",
            maxCaptionLength = 500,
            acceptedMediaTypes = setOf(MediaType.VIDEO, MediaType.IMAGE),
        ),
        // Facebook Graph API 영상 업로드 — 문서상 최대 10GB. 실제 배포 경로는 확인 필요.
        Platform.FACEBOOK to PlatformUploadCapability(
            Platform.FACEBOOK, false, true, false, providerLimit(10 * DECIMAL_GB), 255, 5_000, 30, setOf("mp4", "mov"),
            "Facebook은 클라우드 임시 URL을 통해 업로드합니다.",
            maxCaptionLength = 5_000,
        ),
        // Pinterest API 동영상 Pin 사양 — 최대 2GB.
        Platform.PINTEREST to PlatformUploadCapability(
            Platform.PINTEREST, false, true, false, providerLimit(2 * DECIMAL_GB), 100, 800, 0, setOf("mp4", "mov", "m4v"),
            "Pinterest는 연결된 보드와 커버 이미지가 있는 동영상 Pin으로 업로드합니다.",
        ),
        // LinkedIn Videos API — 최대 5GB.
        Platform.LINKEDIN to PlatformUploadCapability(
            Platform.LINKEDIN, false, true, false, providerLimit(5 * DECIMAL_GB), 3_000, 3_000, 0, setOf("mp4"),
            "LinkedIn Videos API의 4MB 파트 업로드와 처리 완료 확인을 사용합니다.",
            maxCaptionLength = 3_000,
        ),
        // WordPress REST Media API 는 사이트·PHP 설정을 따른다. 100MB 는 보수적인 고정 상한이다.
        Platform.WORDPRESS to PlatformUploadCapability(
            Platform.WORDPRESS, false, true, false, providerLimit(100 * DECIMAL_MB), 200, 5_000, 100, VIDEO_EXTENSIONS,
            "WordPress.com 사이트의 미디어 허용 형식과 저장 용량 정책을 따릅니다.",
        ),
        // Dailymotion 업로드 세션 — 공식 한도 확인 전까지 보수적으로 2GiB.
        Platform.DAILYMOTION to PlatformUploadCapability(
            Platform.DAILYMOTION, false, true, false, providerLimit(2 * GB), 255, 3_000, 150, setOf("mp4", "mov"),
            "Dailymotion API v2 세션 업로드와 프로필 영상 생성을 사용합니다.",
        ),
        // Vimeo 는 요금제마다 용량이 달라, 요금제별 판단이 생기기 전까지 500MB 로 보수적으로 둔다.
        Platform.VIMEO to PlatformUploadCapability(
            Platform.VIMEO, false, true, false, providerLimit(500 * DECIMAL_MB), 128, 5_000, 0, setOf("mp4", "mov", "webm"),
            "Vimeo pull 업로드는 공개 또는 장시간 유효한 presigned 파일 URL을 사용합니다.",
        ),
        // Tumblr 동영상 게시 — 최대 100MB.
        Platform.TUMBLR to PlatformUploadCapability(
            Platform.TUMBLR, false, true, false, providerLimit(100 * DECIMAL_MB), 5_000, 5_000, 30, setOf("mp4", "mov"),
            "Tumblr NPF native video multipart 업로드를 사용합니다. 계정별 일일 업로드 제한이 적용됩니다.",
        ),
    )

    fun get(platform: Platform): PlatformUploadCapability? = capabilities[platform]
    fun all(): List<PlatformUploadCapability> = capabilities.values.toList()

    /**
     * 이 배포가 실제로 게시를 **시도할 수 있는** 플랫폼인지.
     *
     * 게시 경로가 하나도 없으면(직접 업로드도, 클라우드 URL 업로드도 아니면) 외부 API 를
     * 부를 방법 자체가 없다. 그런데도 호출하면 플랫폼 클라이언트의 미구현 분기로 들어가
     * 내부 문구가 사용자에게 나간다. 신규 게시는 `PublishVideoUseCase` 가 이미 막지만,
     * 그 게이트가 생기기 전에 만들어진 업로드 행은 스케줄러·리스너가 그대로 집어 든다.
     * 그 두 경로가 같은 판정을 쓰게 하려고 여기에 둔다.
     */
    fun canPublish(platform: Platform): Boolean =
        get(platform)?.let { it.directVideoUpload || it.cloudVideoUpload } == true

    /**
     * 게시할 수 없는 이유. **사용자에게 그대로 보여줄 수 있는 문장이어야 한다.**
     *
     * 등재된 플랫폼은 자신의 [PlatformUploadCapability.unavailableReason] 을 쓰고,
     * 등재되지 않은 플랫폼만 일반 문구로 떨어진다.
     */
    fun unsupportedReason(platform: Platform): String =
        get(platform)?.unavailableReason
            ?: "${platform.name} 게시는 현재 지원하지 않습니다."
}
