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

    /**
     * 추출기를 지금 쓸 수 있는지 확인한다.
     *
     * 바이너리는 배포 전제라 JVM 밖에 있다. 즉 **코드가 멀쩡해도 호스트에 없으면 동작하지
     * 않는다.** 그걸 사용자가 임포트를 눌러본 뒤에야 알게 하지 않으려고 미리 묻는다.
     *
     * 실패를 예외로 던지지 않는다. "쓸 수 없다"는 정상적인 답이지 오류가 아니다.
     */
    fun checkAvailability(): DownloaderAvailability
}

/**
 * @param reason 사용자에게 보여줄 수 있는 문구. **경로나 스택트레이스를 담지 않는다.**
 *   내부 구조를 노출하고, 사용자가 할 수 있는 일도 없다.
 */
data class DownloaderAvailability(
    val available: Boolean,
    val reason: String? = null,
)

data class DownloadedVideo(
    val path: Path,
    val title: String,
    val originalFilename: String,
    val contentType: String,
    val size: Long,
)
