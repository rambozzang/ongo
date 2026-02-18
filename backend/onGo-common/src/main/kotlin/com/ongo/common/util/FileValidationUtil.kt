package com.ongo.common.util

import com.ongo.common.enums.MediaType
import com.ongo.common.exception.FileValidationException

/**
 * 영상/이미지 파일 유효성 검증 유틸리티.
 * 확장자, MIME 타입, 파일 크기를 검증한다.
 */
object FileValidationUtil {

    // --- Video ---

    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "avi", "mkv", "webm")

    private val VIDEO_MIME_TYPES = setOf(
        "video/mp4",
        "video/quicktime",
        "video/x-msvideo",
        "video/x-matroska",
        "video/webm",
    )

    private val VIDEO_EXTENSION_MIME_MAP = mapOf(
        "mp4" to "video/mp4",
        "mov" to "video/quicktime",
        "avi" to "video/x-msvideo",
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
    )

    /** Phase 1 기본 최대 크기: 2GB */
    const val DEFAULT_MAX_FILE_SIZE: Long = 2L * 1024 * 1024 * 1024

    // --- Image ---

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic")

    private val IMAGE_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/heic",
    )

    private val IMAGE_EXTENSION_MIME_MAP = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "heic" to "image/heic",
    )

    /** 이미지 최대 크기: 50MB */
    const val IMAGE_MAX_FILE_SIZE: Long = 50L * 1024 * 1024

    // --- Backward-compatible aliases ---

    private val ALLOWED_EXTENSIONS = VIDEO_EXTENSIONS
    private val ALLOWED_MIME_TYPES = VIDEO_MIME_TYPES
    private val EXTENSION_MIME_MAP = VIDEO_EXTENSION_MIME_MAP

    // --- Video validation (original methods — backward compatible) ---

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

    // --- Image validation ---

    fun validateImageExtension(fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in IMAGE_EXTENSIONS) {
            throw FileValidationException(
                "지원하지 않는 이미지 형식입니다: .$extension (지원: ${IMAGE_EXTENSIONS.joinToString(", ") { ".$it" }})"
            )
        }
    }

    fun validateImageMimeType(mimeType: String) {
        if (mimeType !in IMAGE_MIME_TYPES) {
            throw FileValidationException(
                "지원하지 않는 이미지 MIME 타입입니다: $mimeType"
            )
        }
    }

    fun validateImage(fileName: String, mimeType: String, fileSize: Long, maxSize: Long = IMAGE_MAX_FILE_SIZE) {
        validateImageExtension(fileName)
        validateImageMimeType(mimeType)

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val expectedMime = IMAGE_EXTENSION_MIME_MAP[extension]
        if (expectedMime != null && expectedMime != mimeType) {
            throw FileValidationException(
                "이미지 확장자($extension)와 MIME 타입($mimeType)이 일치하지 않습니다."
            )
        }

        validateFileSize(fileSize, maxSize)
    }

    // --- Media type detection ---

    fun detectMediaType(mimeType: String): MediaType =
        when {
            mimeType in VIDEO_MIME_TYPES -> MediaType.VIDEO
            mimeType in IMAGE_MIME_TYPES -> MediaType.IMAGE
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("image/") -> MediaType.IMAGE
            else -> throw FileValidationException("지원하지 않는 미디어 타입입니다: $mimeType")
        }

    fun validateByMediaType(fileName: String, mimeType: String, fileSize: Long, mediaType: MediaType) {
        when (mediaType) {
            MediaType.VIDEO -> validate(fileName, mimeType, fileSize)
            MediaType.IMAGE -> validateImage(fileName, mimeType, fileSize)
        }
    }
}
