package com.ongo.application.videodownload

import com.ongo.domain.videodownload.VideoDownloadProvider
import java.nio.file.Path

/**
 * Downloads a source URL to a local temporary file.
 *
 * The adapter owns the external extractor (yt-dlp in production); the use case
 * only deals with a bounded temporary file and never trusts a URL as a storage
 * location. The returned file is owned by the caller and must be deleted after
 * it has been uploaded or rejected. Do not call this port without arranging
 * equivalent cleanup.
 */
interface VideoSourceDownloader {
    fun download(url: String, provider: VideoDownloadProvider): DownloadedVideo
}

data class DownloadedVideo(
    val path: Path,
    val title: String,
    val originalFilename: String,
    val contentType: String,
    val size: Long,
)
