package com.ongo.infrastructure.upload

import com.ongo.common.annotation.CurrentUser
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.domain.video.VideoRepository
import com.ongo.infrastructure.external.storage.StorageClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.scheduling.annotation.Scheduled
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Tag(name = "파일 업로드", description = "Tus 프로토콜 기반 대용량 파일 이어받기 업로드")
@RestController
@RequestMapping("/api/v1/videos/upload/tus")
class TusUploadController(
    private val tusConfig: TusUploadConfig,
    private val uploadVideoUseCase: UploadVideoUseCase,
    private val videoRepository: VideoRepository,
    private val storageClient: StorageClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 진행 중인 업로드 오프셋 추적
    private val uploadOffsets = ConcurrentHashMap<Long, Long>()
    private val uploadMetadata = ConcurrentHashMap<Long, UploadMeta>()

    data class UploadMeta(val filename: String, val contentType: String, val fileSize: Long, val createdAt: LocalDateTime = LocalDateTime.now())

    @Operation(
        summary = "Tus 서버 옵션 조회",
        description = "Tus 프로토콜 지원 버전, 확장 기능(creation, termination), 최대 파일 크기 등의 서버 설정 정보를 반환합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "서버 옵션 반환 성공 (응답 헤더에 포함)")
    )
    @RequestMapping(value = ["/{videoId}"], method = [RequestMethod.OPTIONS])
    fun options(response: HttpServletResponse) {
        response.setHeader("Tus-Resumable", "1.0.0")
        response.setHeader("Tus-Version", "1.0.0")
        response.setHeader("Tus-Extension", "creation,termination")
        response.setHeader("Tus-Max-Size", tusConfig.maxFileSize.toString())
        response.status = HttpStatus.NO_CONTENT.value()
    }

    @Operation(
        summary = "업로드 세션 생성",
        description = "새로운 업로드 세션을 생성하고 업로드 URL을 반환합니다. Upload-Length 헤더로 파일 크기를, Upload-Metadata 헤더로 파일명과 타입을 전달해야 합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "업로드 세션 생성 성공 (Location 헤더에 업로드 URL 포함)"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 헤더 누락)"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @PostMapping("/{videoId}")
    fun createUpload(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "영상 ID") @PathVariable videoId: Long,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // 소유권 검증
        val video = videoRepository.findById(videoId)
        if (video == null || video.userId != userId) {
            response.status = HttpStatus.FORBIDDEN.value()
            return
        }

        val uploadLength = request.getHeader("Upload-Length")?.toLongOrNull() ?: 0L

        // 파일 크기 제한 검증
        if (uploadLength <= 0 || uploadLength > tusConfig.maxFileSize) {
            response.status = HttpStatus.BAD_REQUEST.value()
            return
        }

        val metadataHeader = request.getHeader("Upload-Metadata") ?: ""
        val meta = parseTusMetadata(metadataHeader)

        val filename = meta["filename"] ?: "unknown"
        val contentType = meta["filetype"] ?: "video/mp4"

        // 파일명 sanitize — UUID 기반으로 안전한 이름 사용
        val safeFilename = sanitizeFilename(filename)

        uploadOffsets[videoId] = 0L
        uploadMetadata[videoId] = UploadMeta(safeFilename, contentType, uploadLength)

        // 임시 파일 디렉토리 생성
        val tusDir = File(tusConfig.storagePath)
        if (!tusDir.exists()) tusDir.mkdirs()

        // 빈 임시 파일 생성
        File(tusDir, "$videoId.part").createNewFile()

        response.setHeader("Tus-Resumable", "1.0.0")
        response.setHeader("Upload-Offset", "0")
        response.setHeader("Location", "/api/v1/videos/upload/tus/$videoId")
        response.status = HttpStatus.CREATED.value()

        log.info("Tus 업로드 생성: videoId={}, filename={}, size={}", videoId, safeFilename, uploadLength)
    }

    @Operation(
        summary = "업로드 진행 상태 확인",
        description = "현재 업로드 오프셋(진행률)을 반환합니다. 클라이언트가 이어받기 시 현재 위치를 확인하는 데 사용됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "업로드 상태 반환 성공 (Upload-Offset 헤더에 포함)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "업로드 세션을 찾을 수 없음")
    )
    @RequestMapping(value = ["/{videoId}"], method = [RequestMethod.HEAD])
    fun headUpload(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "영상 ID") @PathVariable videoId: Long,
        response: HttpServletResponse,
    ) {
        val offset = uploadOffsets[videoId] ?: 0L
        val meta = uploadMetadata[videoId]

        response.setHeader("Tus-Resumable", "1.0.0")
        response.setHeader("Upload-Offset", offset.toString())
        if (meta != null) {
            response.setHeader("Upload-Length", meta.fileSize.toString())
        }
        response.status = HttpStatus.OK.value()
    }

    @Operation(
        summary = "파일 청크 업로드",
        description = "파일의 일부분을 업로드합니다. Upload-Offset 헤더로 시작 위치를 지정하며, Content-Type은 application/offset+octet-stream이어야 합니다. 모든 청크가 전송되면 자동으로 업로드 완료 처리됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "청크 업로드 성공 (Upload-Offset 헤더에 새 오프셋 포함)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "409", description = "오프셋 불일치 (클라이언트/서버 오프셋 불일치)"),
        ApiResponse(responseCode = "415", description = "지원하지 않는 Content-Type")
    )
    @PatchMapping("/{videoId}")
    fun patchUpload(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "영상 ID") @PathVariable videoId: Long,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // 소유권 검증
        val video = videoRepository.findById(videoId)
        if (video == null || video.userId != userId) {
            response.status = HttpStatus.FORBIDDEN.value()
            return
        }

        val contentType = request.getHeader("Content-Type")
        if (contentType != "application/offset+octet-stream") {
            response.status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
            return
        }

        val clientOffset = request.getHeader("Upload-Offset")?.toLongOrNull() ?: 0L
        val serverOffset = uploadOffsets[videoId] ?: 0L
        val meta = uploadMetadata[videoId]

        if (clientOffset != serverOffset) {
            response.status = HttpStatus.CONFLICT.value()
            return
        }

        val tusFile = File(tusConfig.storagePath, "$videoId.part")
        val bytesRead: Long

        RandomAccessFile(tusFile, "rw").use { raf ->
            raf.seek(serverOffset)
            val buffer = ByteArray(8192)
            var totalRead = 0L
            request.inputStream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    // 파일 크기 제한 검증: 선언 크기 초과 시 즉시 중단
                    if (meta != null && (serverOffset + totalRead + read) > meta.fileSize) {
                        log.warn("파일 크기 초과 시도: videoId={}, declaredSize={}, actualSize={}", videoId, meta.fileSize, serverOffset + totalRead + read)
                        response.status = HttpStatus.REQUEST_ENTITY_TOO_LARGE.value()
                        return
                    }
                    raf.write(buffer, 0, read)
                    totalRead += read
                }
            }
            bytesRead = totalRead
        }

        val newOffset = serverOffset + bytesRead
        uploadOffsets[videoId] = newOffset

        response.setHeader("Tus-Resumable", "1.0.0")
        response.setHeader("Upload-Offset", newOffset.toString())
        response.status = HttpStatus.NO_CONTENT.value()

        // 업로드 완료 체크
        if (meta != null && newOffset >= meta.fileSize) {
            completeUpload(videoId, tusFile, meta)
        }
    }

    @Operation(
        summary = "업로드 취소",
        description = "진행 중인 업로드를 취소하고 임시 파일을 삭제합니다. 업로드 세션이 정리됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "업로드 취소 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "업로드 세션을 찾을 수 없음")
    )
    @DeleteMapping("/{videoId}")
    fun deleteUpload(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "영상 ID") @PathVariable videoId: Long,
        response: HttpServletResponse,
    ) {
        uploadOffsets.remove(videoId)
        uploadMetadata.remove(videoId)
        Files.deleteIfExists(File(tusConfig.storagePath, "$videoId.part").toPath())

        response.setHeader("Tus-Resumable", "1.0.0")
        response.status = HttpStatus.NO_CONTENT.value()

        log.info("Tus 업로드 취소: videoId={}", videoId)
    }

    private fun completeUpload(videoId: Long, file: File, meta: UploadMeta) {
        log.info("Tus 업로드 완료 처리 시작: videoId={}, filename={}", videoId, meta.filename)

        try {
            // SHA-256 해시 계산
            val contentHash = calculateSHA256(file)

            // 스토리지에 파일 업로드
            val objectName = "videos/$videoId/${meta.filename}"
            file.inputStream().use { input ->
                storageClient.uploadFile(objectName, input, meta.contentType, meta.fileSize)
            }

            // 파일 URL 생성
            val fileUrl = storageClient.getFileUrl(objectName)

            // 업로드 완료 처리
            uploadVideoUseCase.completeUpload(videoId, fileUrl, contentHash)

            log.info("Tus 업로드 완료: videoId={}, fileUrl={}", videoId, fileUrl)
        } catch (e: Exception) {
            log.error("Tus 업로드 완료 처리 실패: videoId={}", videoId, e)
        } finally {
            // 임시 파일 정리
            Files.deleteIfExists(file.toPath())
            uploadOffsets.remove(videoId)
            uploadMetadata.remove(videoId)
        }
    }

    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeFilename(filename: String): String {
        // 확장자 추출
        val extension = filename.substringAfterLast('.', "").lowercase().let { ext ->
            if (ext.matches(Regex("[a-z0-9]{1,10}"))) ".$ext" else ""
        }
        // UUID 기반 파일명으로 path traversal 방지
        return "${UUID.randomUUID()}$extension"
    }

    /**
     * 매시간 고아 임시 파일 정리 — 설정된 만료 시간(기본 24시간) 초과 .part 파일 삭제
     */
    @Scheduled(cron = "0 0 * * * *")
    fun cleanupOrphanedUploads() {
        val tusDir = File(tusConfig.storagePath)
        if (!tusDir.exists()) return

        val expirationMs = tusConfig.expirationHours * 3600L * 1000L
        val now = System.currentTimeMillis()
        var cleaned = 0

        tusDir.listFiles { file -> file.name.endsWith(".part") }?.forEach { file ->
            if (now - file.lastModified() > expirationMs) {
                val videoId = file.nameWithoutExtension.toLongOrNull()
                Files.deleteIfExists(file.toPath())
                if (videoId != null) {
                    uploadOffsets.remove(videoId)
                    uploadMetadata.remove(videoId)
                }
                cleaned++
            }
        }

        if (cleaned > 0) {
            log.info("고아 Tus 임시 파일 정리 완료: {}개", cleaned)
        }
    }

    private fun parseTusMetadata(header: String): Map<String, String> {
        if (header.isBlank()) return emptyMap()
        return header.split(",").associate { pair ->
            val parts = pair.trim().split(" ", limit = 2)
            val key = parts[0]
            val value = if (parts.size > 1) {
                String(java.util.Base64.getDecoder().decode(parts[1]))
            } else ""
            key to value
        }
    }
}
