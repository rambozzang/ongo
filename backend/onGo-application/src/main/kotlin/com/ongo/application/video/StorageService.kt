package com.ongo.application.video

import com.ongo.common.exception.BusinessException
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

    // ── 멀티파트 업로드 ──────────────────────────────────────────────────
    //
    // 단일 PUT 은 끊기면 처음부터 다시 올려야 한다. 멀티파트는 실패한 조각만 다시 보낸다.
    // 어댑터가 지원하지 않으면(MinIO) [supportsMultipartUpload] 가 false 이고, 호출부는
    // 기존 단일 PUT 으로 폴백한다. 기본 구현이 "미지원" 인 이유다.

    fun supportsMultipartUpload(): Boolean = false

    /** 오브젝트 키를 서버가 정하고 업로드 세션을 연다. */
    fun startMultipartUpload(videoId: Long, filename: String, contentType: String): MultipartUploadSession =
        throw UnsupportedOperationException("이 스토리지는 멀티파트 업로드를 지원하지 않습니다.")

    /**
     * 조각별 서명 URL. [partSizes] 는 조각 번호 → **정확한 바이트 수** 이며 서명에 들어간다.
     * [objectKey] 가 이 영상의 경로 아래가 아니면 거부해야 한다 — 클라이언트가 보낸 값이다.
     */
    fun presignUploadParts(
        videoId: Long,
        objectKey: String,
        uploadId: String,
        partSizes: Map<Int, Long>,
    ): Map<Int, String> = throw UnsupportedOperationException("이 스토리지는 멀티파트 업로드를 지원하지 않습니다.")

    /**
     * 업로드를 완료한다. 조각 ETag 는 **스토리지에서 직접 읽는다** — 브라우저가 ETag 를 읽으려면
     * 버킷 CORS 에 ExposeHeaders 설정이 필요한데, 그 운영 설정에 기대지 않기 위해서다.
     *
     * 올라온 조각이 [expectedSizes] 와 번호·크기가 모두 맞아야 완료한다. 빠진 조각이 있으면
     * [IncompleteMultipartUploadException] 을 던지고 세션은 그대로 둔다 — 클라이언트가 빠진
     * 조각만 다시 보낼 수 있어야 한다.
     */
    fun completeMultipartUpload(
        videoId: Long,
        objectKey: String,
        uploadId: String,
        expectedSizes: Map<Int, Long>,
    ): Unit = throw UnsupportedOperationException("이 스토리지는 멀티파트 업로드를 지원하지 않습니다.")

    fun abortMultipartUpload(videoId: Long, objectKey: String, uploadId: String): Unit =
        throw UnsupportedOperationException("이 스토리지는 멀티파트 업로드를 지원하지 않습니다.")
}

data class MultipartUploadSession(val objectKey: String, val uploadId: String)

/**
 * 완료를 요청했는데 아직 올라오지 않았거나 크기가 틀린 조각이 있다. 다시 보내면 된다.
 *
 * [BusinessException] 인 이유는 `error` 필드에 안정된 코드(`MULTIPART_INCOMPLETE`)를 싣기
 * 위해서다. 프런트가 번역된 문구를 해석하지 않고 이 코드로 "세션은 살아 있으니 다시
 * 시도하라" 를 판단한다.
 */
class IncompleteMultipartUploadException(
    val missingParts: List<Int>,
) : BusinessException(
    code = CODE,
    message = "아직 올라오지 않았거나 크기가 맞지 않는 조각이 있습니다: ${missingParts.take(20)}" +
        if (missingParts.size > 20) " 외 ${missingParts.size - 20}개" else "",
) {
    companion object {
        const val CODE = "MULTIPART_INCOMPLETE"
    }
}
