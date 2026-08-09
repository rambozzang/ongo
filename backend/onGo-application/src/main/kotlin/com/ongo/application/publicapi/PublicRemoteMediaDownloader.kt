package com.ongo.application.publicapi

import java.nio.file.Path

/** 외부 URL 미디어를 안전한 서버 임시 파일로 가져오는 애플리케이션 포트. */
interface PublicRemoteMediaDownloader {
    fun download(url: String): PublicDownloadedMedia
}

data class PublicDownloadedMedia(
    val path: Path,
    val filename: String,
    val contentType: String,
    val size: Long,
)
