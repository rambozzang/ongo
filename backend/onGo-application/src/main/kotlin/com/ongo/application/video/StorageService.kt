package com.ongo.application.video

import java.io.InputStream

interface StorageService {
    /**
     * 업로드용 서명 URL. 선언 크기를 함께 넘겨 어댑터가 강제할 수 있으면 강제하게 한다.
     *
     * S3/R2 는 content-length 를 서명에 포함할 수 있어 신고치와 다른 크기의 PUT 이 거부되지만,
     * MinIO 는 presigned URL 에 길이를 묶는 API 가 없다. 그래서 이 값은 "가능하면 강제"이고,
     * 최종 방어선은 confirm 단계의 실제 메타데이터 대조다.
     */
    fun generateUploadUrl(videoId: Long, filename: String, contentType: String, fileSize: Long): String

    /** 업로드된 오브젝트의 실제 바이트 수. 없으면 null — 신고치를 믿지 않기 위한 확인 경로다. */
    fun getUploadedSize(videoId: Long): Long?

    /** 업로드된 오브젝트의 **정확한 키**. 탈퇴 정리가 추측 없이 지울 수 있는 유일한 근거다. */
    fun getUploadedKey(videoId: Long): String?
    fun getTusEndpoint(videoId: Long): String
    /** Refresh a durable URL, using the stored URL to resolve legacy object keys. */
    fun getFileUrl(videoId: Long, storedFileUrl: String? = null): String
    /** Copy a durable video object to the storage prefix of another video row. */
    fun copyVideoFile(sourceVideoId: Long, targetVideoId: Long, storedFileUrl: String? = null): String
    fun deleteFile(videoId: Long)
    fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
}
