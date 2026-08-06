package com.ongo.application.videodownload

import com.ongo.common.exception.BusinessException
import com.ongo.domain.videodownload.VideoDownloadProvider
import java.net.URI

/** Validated URL and provider classification used by the importer. */
data class VideoDownloadUrl(
    val original: String,
    val canonical: String,
    val provider: VideoDownloadProvider,
) {
    companion object {
        fun parse(raw: String): VideoDownloadUrl {
            val value = raw.trim()
            if (value.isBlank() || value.length > MAX_URL_LENGTH) {
                throw invalidUrl()
            }

            val uri = try {
                URI(value)
            } catch (_: IllegalArgumentException) {
                throw invalidUrl()
            }
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()?.removeSuffix(".")
            if (scheme != "https" || host.isNullOrBlank() || uri.userInfo != null || uri.path.isNullOrBlank()) {
                throw invalidUrl()
            }
            if (uri.port != -1 && uri.port != 443) {
                throw invalidUrl()
            }

            val provider = when {
                host in YOUTUBE_HOSTS -> VideoDownloadProvider.YOUTUBE
                host in TIKTOK_HOSTS -> VideoDownloadProvider.TIKTOK
                host in INSTAGRAM_HOSTS -> VideoDownloadProvider.INSTAGRAM
                else -> throw BusinessException(
                    "VIDEO_DOWNLOAD_PROVIDER_UNSUPPORTED",
                    "YouTube, TikTok, Instagram URL만 가져올 수 있습니다.",
                )
            }

            // Fragments are client-side only and must not make the same source look
            // like a different download request to the extractor.
            val canonical = URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                null,
            ).toString()
            return VideoDownloadUrl(value, canonical, provider)
        }

        private const val MAX_URL_LENGTH = 2_000
        private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
        private val TIKTOK_HOSTS = setOf("tiktok.com", "www.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")
        private val INSTAGRAM_HOSTS = setOf("instagram.com", "www.instagram.com", "instagr.am")

        private fun invalidUrl() = BusinessException(
            "VIDEO_DOWNLOAD_URL_INVALID",
            "지원하는 동영상 URL 형식이 아닙니다.",
        )
    }
}
