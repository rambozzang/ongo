package com.ongo.common.util

import com.ongo.common.exception.FileValidationException

/**
 * 영상 파일 유효성 검증 유틸리티.
 * 확장자, MIME 타입, 파일 크기를 검증한다.
 */
object FileValidationUtil {

    private val ALLOWED_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")

    private val ALLOWED_MIME_TYPES = setOf(
        "video/mp4",
        "video/quicktime",
        "video/x-msvideo",
        "video/x-matroska",
        "video/webm",
    )

    private val EXTENSION_MIME_MAP = mapOf(
        "mp4" to "video/mp4",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
    )

    /** Phase 1 기본 최대 크기: 2GB */
    const val DEFAULT_MAX_FILE_SIZE: Long = 2L * 1024 * 1024 * 1024

    fun validateExtension(fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            throw FileValidationException(
                "지원하지 않는 파일 형식입니다: .$extension (지원: ${ALLOWED_EXTENSIONS.joinToString(", ") { ".$it" }})"
            )
        }
    }

    fun validateMimeType(mimeType: String) {
        if (mimeType !in ALLOWED_MIME_TYPES) {
            throw FileValidationException(
                "지원하지 않는 MIME 타입입니다: $mimeType"
            )
        }
    }

    fun validateExtensionAndMimeType(fileName: String, mimeType: String) {
        validateExtension(fileName)
        validateMimeType(mimeType)

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val expectedMime = EXTENSION_MIME_MAP[extension]
        if (expectedMime != null && expectedMime != mimeType) {
            throw FileValidationException(
                "파일 확장자($extension)와 MIME 타입($mimeType)이 일치하지 않습니다."
            )
        }
    }

    fun validateFileSize(fileSize: Long, maxSize: Long = DEFAULT_MAX_FILE_SIZE) {
        if (fileSize <= 0) {
            throw FileValidationException("파일 크기가 유효하지 않습니다.")
        }
        if (fileSize > maxSize) {
            val maxSizeMB = maxSize / (1024 * 1024)
            val fileSizeMB = fileSize / (1024 * 1024)
            throw FileValidationException(
                "파일 크기(${fileSizeMB}MB)가 최대 허용 크기(${maxSizeMB}MB)를 초과합니다."
            )
        }
    }

    fun validate(fileName: String, mimeType: String, fileSize: Long, maxSize: Long = DEFAULT_MAX_FILE_SIZE) {
        validateExtensionAndMimeType(fileName, mimeType)
        validateFileSize(fileSize, maxSize)
    }
}
