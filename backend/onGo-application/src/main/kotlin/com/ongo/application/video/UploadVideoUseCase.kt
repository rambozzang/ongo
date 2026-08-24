package com.ongo.application.video

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.FileValidationUtil
import com.ongo.application.storage.StorageQuotaUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UploadVideoUseCase(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
    private val userWriteGuard: UserWriteGuard,
    private val storageQuotaUseCase: StorageQuotaUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun createVideo(
        userId: Long,
        title: String,
        description: String? = null,
        tags: List<String> = emptyList(),
        category: String? = null,
        thumbnailUrl: String? = null,
        mediaType: MediaType = MediaType.VIDEO,
    ): Video {
        userWriteGuard.requireWritable(userId)
        val video = videoRepository.save(
            Video(
                userId = userId,
                title = title,
                description = description,
                tags = tags,
                category = category,
                thumbnailUrls = if (thumbnailUrl != null) listOf(thumbnailUrl) else emptyList(),
                mediaType = mediaType,
                status = UploadStatus.DRAFT,
            )
        )
        return video
    }

    @Transactional
    fun initiatePresignedUpload(
        userId: Long,
        filename: String,
        contentType: String,
        fileSize: Long,
    ): PresignedUploadResult {
        userWriteGuard.requireWritable(userId)
        FileValidationUtil.validate(filename, contentType, fileSize)
        // 신고 크기 기준의 1차 방어. 실제 크기는 confirm 에서 다시 본다.
        storageQuotaUseCase.checkQuota(userId, fileSize)
        val video = videoRepository.save(
            Video(
                userId = userId,
                title = filename.substringBeforeLast('.').ifBlank { "업로드 영상" }.take(100),
                fileSizeBytes = fileSize,
                originalFilename = filename,
                mediaType = MediaType.VIDEO,
                status = UploadStatus.UPLOADING,
            )
        )
        val videoId = requireNotNull(video.id) { "업로드 레코드 생성에 실패했습니다." }
        return PresignedUploadResult(
            videoId = videoId,
            uploadUrl = storageService.generateUploadUrl(videoId, filename, contentType, fileSize),
        )
    }

    /**
     * 업로드 확정. 여기서 지키는 불변식은 세 가지다.
     *
     * - DB 의 fileSizeBytes 는 **스토리지가 보고한 실제 크기**만 담는다. 신고치는 URL 을 받기 위한
     *   입력일 뿐이고, 그대로 믿으면 1바이트로 신고하고 수십 GB 를 올리는 우회가 그대로 통한다.
     * - 실제 크기로 플랜 한도를 다시 본다. init 의 검사는 신고치 기준이라 최종 근거가 될 수 없다.
     * - 실패하면 오브젝트와 행 어느 쪽도 성공 상태로 남기지 않는다. 검증에 걸린 업로드가 스토리지에
     *   남으면 과금은 되는데 사용자에게는 보이지 않는 고아가 된다.
     *
     * 재시도는 안전하다. 이미 확정된 행은 그대로 두고 끝내므로 같은 오브젝트가 두 번 계산되거나
     * 두 번 저장되지 않는다.
     */
    @Transactional
    fun confirmPresignedUpload(userId: Long, videoId: Long) {
        userWriteGuard.requireWritable(userId)
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")

        // 이미 확정된 업로드의 재호출 — 아무것도 다시 세지 않는다.
        if (video.status != UploadStatus.UPLOADING && !video.fileUrl.isNullOrBlank()) return

        val actualSize = readActualSizeOrDiscard(video, videoId)
        try {
            FileValidationUtil.validateFileSize(actualSize)
            // 이 영상의 예약분은 빼고 실제 크기로 다시 본다 — 같은 업로드를 두 번 세지 않는다.
            storageQuotaUseCase.checkQuota(userId, actualSize, excludeVideoId = videoId)
        } catch (e: Exception) {
            discardUpload(video, videoId, "검증 실패: ${e.message}")
            throw e
        }

        val fileUrl = try {
            storageService.getFileUrl(videoId)
        } catch (e: Exception) {
            discardUpload(video, videoId, "파일 URL 확인 실패")
            throw e
        }

        // 서버가 할당한 정확한 키를 함께 기록한다. 탈퇴 시 이 값이 없으면 무엇을 지울지
        // URL 로 추측해야 하고, 추측은 남의 파일을 지울 위험이 있어 허용하지 않는다.
        val objectKey = runCatching { storageService.getUploadedKey(videoId) }.getOrNull()
        videoRepository.update(
            video.copy(
                fileUrl = fileUrl,
                fileSizeBytes = actualSize,
                storageObjectKey = objectKey,
                status = UploadStatus.DRAFT,
            ),
        )
    }

    private fun readActualSizeOrDiscard(video: Video, videoId: Long): Long {
        val actualSize = runCatching { storageService.getUploadedSize(videoId) }.getOrNull()
        if (actualSize == null || actualSize <= 0) {
            // 오브젝트가 없거나 메타데이터를 못 읽었다. 확정할 근거가 없으므로 행을 남기지 않는다.
            discardUpload(video, videoId, "업로드된 파일을 확인할 수 없음")
            throw NotFoundException("업로드 파일", videoId)
        }
        return actualSize
    }

    /**
     * 확정에 실패한 업로드를 되돌린다.
     *
     * 오브젝트를 먼저 지우고, **삭제가 성공했을 때만** 행을 지운다. 스토리지 삭제가 실패했는데
     * 행까지 지우면 과금되는 오브젝트를 가리키는 유일한 단서가 사라져 아무도 못 찾는 고아가 된다.
     * 삭제에 실패하면 행을 UPLOADING 그대로 남겨 — 성공(DRAFT)으로 오해되지 않으면서 —
     * StaleUploadCleanupUseCase 가 다음 주기에 다시 회수를 시도하게 한다.
     */
    private fun discardUpload(video: Video, videoId: Long, reason: String) {
        log.warn("업로드 확정 실패 — 정리 수행 [videoId={}, userId={}, 사유={}]", videoId, video.userId, reason)
        val storageCleared = runCatching { storageService.deleteFile(videoId) }
            .onFailure { log.error("업로드 오브젝트 정리 실패 — 행을 남겨 추적 [videoId={}]", videoId, it) }
            .isSuccess
        if (!storageCleared) return
        runCatching { videoRepository.delete(videoId) }
            .onFailure { log.error("업로드 행 정리 실패 [videoId={}]", videoId, it) }
    }
}

data class PresignedUploadResult(val videoId: Long, val uploadUrl: String)
