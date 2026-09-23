package com.ongo.common.util

import com.ongo.common.enums.MediaType
import com.ongo.common.exception.FileValidationException
import java.io.InputStream

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

    /**
     * **브라우저가 R2 로 직접 올리는** 영상의 최대 크기: 10GiB.
     *
     * presigned PUT·멀티파트 업로드, URL 가져오기, 쇼츠 원본이 이 값을 쓴다. 요청 본문이 우리 서버를
     * 지나지 않으므로 서버 메모리·nginx 한도와 무관하다. 3시간 1080p 라이브(4~8GB)를 받기 위한 값이다.
     * 실제로 올릴 수 있는 양은 요금제 저장공간(`StorageQuotaUseCase`)이 따로 정한다.
     */
    const val VIDEO_DIRECT_UPLOAD_MAX_BYTES: Long = 10L * 1024 * 1024 * 1024

    /**
     * **presigned PUT 한 번**으로 올리는 최대 크기: 5GiB. S3·R2 의 단일 PutObject 상한이다.
     *
     * 멀티파트를 지원하지 않는 저장소로 폴백할 때 이 경로를 탄다. 10GiB 를 허용하면 저장소가 업로드
     * 도중에 거절해, 사용자는 몇십 분을 기다린 뒤 실패를 본다.
     */
    const val SINGLE_PUT_MAX_BYTES: Long = 5L * 1024 * 1024 * 1024

    /**
     * **요청 본문이 우리 서버를 지나는** 업로드의 최대 크기: 2GiB (유지).
     *
     * 즉시 스트림 게시, 에셋 업로드, 공개 API 미디어가 여기에 해당한다. nginx `client_max_body_size`·
     * 서블릿 multipart 한도와 묶여 있어 직접 업로드 한도와 함께 올리면 안 된다.
     */
    const val SERVER_PROXIED_MAX_BYTES: Long = 2L * 1024 * 1024 * 1024

    /** 크기를 명시하지 않은 검증의 기본값. 더 엄한 쪽(서버 경유)으로 둔다 — 넓히는 것은 호출부가 명시한다. */
    const val DEFAULT_MAX_FILE_SIZE: Long = SERVER_PROXIED_MAX_BYTES

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
            throw FileValidationException(
                "파일 크기(${humanSize(fileSize)})가 최대 허용 크기(${humanSize(maxSize)})를 초과합니다."
            )
        }
    }

    /** 1GiB 이상은 GB 로 보인다. "10240MB" 는 사용자가 한도를 가늠하기 어렵다. */
    private fun humanSize(bytes: Long): String {
        val gib = 1024L * 1024 * 1024
        return if (bytes >= gib) {
            val gb = bytes.toDouble() / gib
            if (gb % 1.0 == 0.0) "${gb.toLong()}GB" else "%.1fGB".format(gb)
        } else {
            "${bytes / (1024 * 1024)}MB"
        }
    }

    fun validate(fileName: String, mimeType: String, fileSize: Long, maxSize: Long = DEFAULT_MAX_FILE_SIZE) {
        validateExtensionAndMimeType(fileName, mimeType)
        validateFileSize(fileSize, maxSize)
    }

    /**
     * Multipart의 content-type은 클라이언트가 보낼 수 있는 값이므로 신뢰하지 않는다.
     * 컨테이너의 고정 시그니처까지 확인해 `evil.exe`를 `video/mp4`로 위장한 요청을
     * 업로드 경로에서 차단한다. 스트림은 호출자가 다시 열 수 있는 Multipart 스트림을
     * 넘겨야 하며, 이 함수가 소비한 스트림은 재사용하지 않는다.
     */
    fun validateVideoContent(inputStream: InputStream, mimeType: String) {
        val header = inputStream.buffered().use { it.readNBytes(16) }
        val valid = when (mimeType) {
            "video/mp4", "video/quicktime" -> hasFtyp(header)
            "video/x-msvideo" -> hasPrefix(header, byteArrayOf(0x52, 0x49, 0x46, 0x46)) &&
                hasPrefix(header, byteArrayOf(0x41, 0x56, 0x49, 0x20), 8)
            "video/x-matroska", "video/webm" -> hasPrefix(header, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
            else -> false
        }
        if (!valid) {
            throw FileValidationException("파일 내용이 선언된 영상 형식($mimeType)과 일치하지 않습니다.")
        }
    }

    /** 에셋 업로드용 알려진 파일 시그니처 검사. 텍스트 JSON은 구조의 첫 문자를 확인한다. */
    fun validateAssetContent(inputStream: InputStream, mimeType: String) {
        val header = inputStream.buffered().use { it.readNBytes(32) }
        val valid = when {
            mimeType == "application/json" -> header.dropWhile { it.toInt().toChar().isWhitespace() || it == 0xEF.toByte() || it == 0xBB.toByte() || it == 0xBF.toByte() }
                .firstOrNull()?.toInt()?.toChar() in setOf('{', '[')
            mimeType == "application/pdf" -> hasPrefix(header, "%PDF-".encodeToByteArray())
            mimeType == "application/zip" -> hasPrefix(header, byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            mimeType == "application/msword" -> hasPrefix(
                header,
                byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()),
            )
            mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                hasPrefix(header, byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            mimeType.startsWith("video/") -> runCatching { validateVideoHeader(header, mimeType) }.getOrDefault(false)
            mimeType == "image/jpeg" -> hasPrefix(header, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            mimeType == "image/png" -> hasPrefix(header, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            mimeType == "image/gif" -> hasPrefix(header, "GIF8".encodeToByteArray())
            mimeType == "image/webp" -> hasPrefix(header, "RIFF".encodeToByteArray()) && hasPrefix(header, "WEBP".encodeToByteArray(), 8)
            mimeType == "image/heic" || mimeType == "image/avif" -> hasFtyp(header)
            mimeType == "image/bmp" -> hasPrefix(header, "BM".encodeToByteArray())
            mimeType == "image/svg+xml" -> header.decodeToString().trimStart('\uFEFF', ' ', '\t', '\r', '\n').let {
                it.startsWith("<svg", ignoreCase = true) || it.startsWith("<?xml", ignoreCase = true) && it.contains("<svg", ignoreCase = true)
            }
            mimeType == "audio/mpeg" -> hasPrefix(header, "ID3".encodeToByteArray()) || (header.size >= 2 && header[0].toInt() and 0xFF == 0xFF && header[1].toInt() and 0xE0 == 0xE0)
            mimeType == "audio/wav" -> hasPrefix(header, "RIFF".encodeToByteArray()) && hasPrefix(header, "WAVE".encodeToByteArray(), 8)
            mimeType == "audio/ogg" -> hasPrefix(header, "OggS".encodeToByteArray())
            mimeType == "audio/webm" -> hasPrefix(header, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
            mimeType == "audio/mp4" -> hasFtyp(header)
            else -> false
        }
        if (!valid) {
            throw FileValidationException("파일 내용이 선언된 에셋 형식($mimeType)과 일치하지 않습니다.")
        }
    }

    private fun validateVideoHeader(header: ByteArray, mimeType: String): Boolean = when (mimeType) {
        "video/mp4", "video/quicktime" -> hasFtyp(header)
        "video/x-msvideo" -> hasPrefix(header, byteArrayOf(0x52, 0x49, 0x46, 0x46)) && hasPrefix(header, byteArrayOf(0x41, 0x56, 0x49, 0x20), 8)
        "video/x-matroska", "video/webm" -> hasPrefix(header, byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()))
        else -> false
    }

    private fun hasFtyp(header: ByteArray): Boolean = hasPrefix(header, "ftyp".encodeToByteArray(), 4)

    private fun hasPrefix(bytes: ByteArray, prefix: ByteArray, offset: Int = 0): Boolean =
        offset >= 0 && bytes.size >= offset + prefix.size && prefix.indices.all { bytes[offset + it] == prefix[it] }

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
