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
        val video = ownedVideo(userId, videoId)

        // 이미 확정된 업로드의 재호출 — 아무것도 다시 세지 않는다.
        if (isConfirmed(video)) return

        confirmUploaded(video, userId, videoId)
    }

    // ── 멀티파트 업로드 ──────────────────────────────────────────────────
    //
    // 단일 PUT 은 끊기면 처음부터 다시 올려야 하고, 클라이언트 30분 제한에 걸리면 2GB 를
    // 끝까지 보낼 수 없다. 멀티파트는 실패한 조각만 다시 보낸다.
    //
    // 검증은 단일 PUT 과 **같은 자리**에서 한다. 시작 시 신고 크기로 1차, 완료 시 스토리지가
    // 보고한 실제 크기로 2차([confirmUploaded] 재사용). 조각별 크기는 서명에 묶여 있어
    // 계획과 다른 조각은 스토리지가 거부한다.

    /**
     * 업로드를 시작한다. 스토리지가 멀티파트를 지원하지 않으면(MinIO) 단일 PUT URL 을 돌려준다 —
     * 클라이언트는 [UploadInitiation.multipart] 를 보고 분기한다.
     */
    fun initiateUpload(userId: Long, filename: String, contentType: String, fileSize: Long): UploadInitiation {
        if (!storageService.supportsMultipartUpload()) {
            val single = initiatePresignedUpload(userId, filename, contentType, fileSize)
            return UploadInitiation(videoId = single.videoId, multipart = false, uploadUrl = single.uploadUrl)
        }

        userWriteGuard.requireWritable(userId)
        FileValidationUtil.validate(filename, contentType, fileSize)
        storageQuotaUseCase.checkQuota(userId, fileSize)
        val plan = MultipartUploadPlan.forSize(fileSize)

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

        val session = try {
            storageService.startMultipartUpload(videoId, filename, contentType)
        } catch (e: Exception) {
            // 세션을 못 열었는데 행만 남으면 사용자에게 "업로드 중" 인 유령 영상이 보인다.
            runCatching { videoRepository.delete(videoId) }
            throw e
        }
        return UploadInitiation(
            videoId = videoId,
            multipart = true,
            uploadId = session.uploadId,
            objectKey = session.objectKey,
            partSize = plan.partSize,
            partCount = plan.partCount,
        )
    }

    /**
     * 조각 URL 을 **몇 개씩** 발급한다. 한꺼번에 주면 느린 회선에서 뒷조각 URL 이 만료된다.
     *
     * 조각 크기는 클라이언트가 아니라 **행에 저장된 신고 크기로 서버가 다시 계산**한다.
     * 클라이언트가 크기를 정하게 두면 서명에 무엇을 넣을지 알 수 없다.
     */
    fun presignUploadParts(
        userId: Long,
        videoId: Long,
        uploadId: String,
        objectKey: String,
        partNumbers: List<Int>,
    ): Map<Int, String> {
        userWriteGuard.requireWritable(userId)
        val video = ownedVideo(userId, videoId)
        if (video.status != UploadStatus.UPLOADING) {
            throw IllegalStateException("업로드 중인 영상이 아닙니다.")
        }
        require(partNumbers.isNotEmpty()) { "요청한 조각이 없습니다." }
        require(partNumbers.size <= MAX_PART_URLS_PER_REQUEST) {
            "조각 URL 은 한 번에 ${MAX_PART_URLS_PER_REQUEST}개까지 요청할 수 있습니다."
        }

        val plan = planOf(video)
        // sizeOf 가 범위 밖 번호를 거부한다. 중복은 한 번만 서명한다.
        val sizes = partNumbers.distinct().associateWith(plan::sizeOf)
        return storageService.presignUploadParts(videoId, objectKey, uploadId, sizes)
    }

    /**
     * 조각을 합치고 기존 확정 절차를 그대로 밟는다.
     *
     * 빠진 조각이 있으면 [IncompleteMultipartUploadException] 이 나가고 **아무것도 지우지
     * 않는다**. 세션이 살아 있어야 클라이언트가 빠진 조각만 다시 보낼 수 있다.
     */
    @Transactional
    fun completeMultipartUpload(userId: Long, videoId: Long, uploadId: String, objectKey: String) {
        userWriteGuard.requireWritable(userId)
        val video = ownedVideo(userId, videoId)
        if (isConfirmed(video)) return

        val plan = planOf(video)
        storageService.completeMultipartUpload(
            videoId,
            objectKey,
            uploadId,
            (1..plan.partCount).associateWith(plan::sizeOf),
        )
        confirmUploaded(video, userId, videoId)
    }

    /** 사용자가 취소했다. 올라온 조각과 행을 함께 정리한다. */
    fun abortMultipartUpload(userId: Long, videoId: Long, uploadId: String, objectKey: String) {
        userWriteGuard.requireWritable(userId)
        val video = ownedVideo(userId, videoId)
        if (isConfirmed(video)) throw IllegalStateException("이미 업로드가 끝난 영상은 취소할 수 없습니다.")

        runCatching { storageService.abortMultipartUpload(videoId, objectKey, uploadId) }
            .onFailure { log.warn("멀티파트 중단 실패 — 정리 경로에 맡김 [videoId={}]", videoId, it) }
        // deleteFile 이 prefix 아래 미완료 업로드도 함께 중단하므로, 위 호출이 실패해도 여기서 회수된다.
        discardUpload(video, videoId, "사용자 취소")
    }

    private fun ownedVideo(userId: Long, videoId: Long): Video {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        return video
    }

    private fun isConfirmed(video: Video): Boolean =
        video.status != UploadStatus.UPLOADING && !video.fileUrl.isNullOrBlank()

    /** 확정 전 행의 fileSizeBytes 는 시작 시 신고한 크기다. 그 값으로 계획을 다시 세운다. */
    private fun planOf(video: Video): MultipartUploadPlan {
        val declared = video.fileSizeBytes
            ?: throw IllegalStateException("업로드 크기 정보가 없습니다.")
        return MultipartUploadPlan.forSize(declared)
    }

    private fun confirmUploaded(video: Video, userId: Long, videoId: Long) {
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

/**
 * 업로드 시작 결과. [multipart] 가 false 면 [uploadUrl] 하나로 단일 PUT,
 * true 면 [uploadId]·[objectKey]·[partSize]·[partCount] 로 멀티파트를 진행한다.
 */
data class UploadInitiation(
    val videoId: Long,
    val multipart: Boolean,
    val uploadUrl: String? = null,
    val uploadId: String? = null,
    val objectKey: String? = null,
    val partSize: Long? = null,
    val partCount: Int? = null,
)

/** 조각 URL 한 번 요청의 상한. 클라이언트 동시 전송 수보다 넉넉하면 된다. */
const val MAX_PART_URLS_PER_REQUEST = 20
