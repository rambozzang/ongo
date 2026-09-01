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

    /**
     * 이 영상에 딸린 **게시 이미지 객체를 모두** 지운다.
     *
     * [deleteFile] 은 `videos/{videoId}/` 만 본다. 이미지는 `content/{videoId}/` 라 그
     * 호출로는 닿지 않아, 행만 사라지고 객체는 버킷에 영구히 남아 있었다.
     *
     * 접두사로 지우는 것이 여기서는 안전하다 — 접두사가 **지금 지우는 행의 기본키**로만
     * 만들어져 다른 사용자의 객체가 그 아래 올 수 없다. 저장된 URL 을 되짚어 키를 추측하는
     * 것과는 다른 이야기다.
     */
    fun deleteContentImages(videoId: Long)

    /** 키를 알고 있는 객체 하나를 지운다. 업로드가 실패했을 때 되돌리는 용도다. */
    fun deleteFileByKey(key: String)
    fun uploadFile(key: String, inputStream: InputStream, contentType: String, size: Long): String
}
