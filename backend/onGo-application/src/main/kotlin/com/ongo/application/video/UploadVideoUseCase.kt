package com.ongo.application.video

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.FileValidationUtil
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth

@Service
class UploadVideoUseCase(
    private val videoRepository: VideoRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val storageService: StorageService,
    private val storageQuotaUseCase: StorageQuotaUseCase,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun initUpload(userId: Long, filename: String, fileSize: Long, contentType: String, mediaType: MediaType?): InitUploadResult {
        // 1. 미디어 타입 결정 (명시적 지정 또는 자동 감지)
        val resolvedMediaType = mediaType ?: FileValidationUtil.detectMediaType(contentType)

        // 2. 파일 검증: 미디어 타입에 맞는 확장자 + MIME 타입 + 크기
        FileValidationUtil.validateByMediaType(filename, contentType, fileSize, resolvedMediaType)

        // 3. 플랜 한도 확인 (월간 업로드 횟수)
        val subscription = subscriptionRepository.findByUserId(userId)
        val planType = subscription?.planType ?: com.ongo.common.enums.PlanType.FREE
        val monthlyCount = videoRepository.countByUserIdAndMonth(userId, YearMonth.now())
        if (monthlyCount >= planType.monthlyUploads) {
            throw PlanLimitExceededException("월간 업로드", planType.monthlyUploads)
        }

        // 3-1. 스토리지 쿼터 확인
        storageQuotaUseCase.checkQuota(userId, fileSize)

        // 4. Video 레코드 생성 (DRAFT 상태)
        val video = videoRepository.save(
            Video(
                userId = userId,
                title = filename.substringBeforeLast('.'),
                originalFilename = filename,
                fileSizeBytes = fileSize,
                mediaType = resolvedMediaType,
                status = UploadStatus.DRAFT,
            )
        )

        // 5. 스토리지 업로드 URL 생성 (Tus 엔드포인트)
        val savedVideoId = video.id!!
        val uploadUrl = storageService.generateUploadUrl(savedVideoId, filename, contentType)
        val tusEndpoint = storageService.getTusEndpoint(savedVideoId)

        return InitUploadResult(
            videoId = savedVideoId,
            uploadUrl = uploadUrl,
            tusEndpoint = tusEndpoint,
            mediaType = resolvedMediaType,
        )
    }

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
    fun completeUpload(videoId: Long, fileUrl: String, contentHash: String?) {
        val video = videoRepository.findById(videoId)
            ?: throw com.ongo.common.exception.NotFoundException("영상", videoId)

        // 중복 콘텐츠 해시 확인
        if (contentHash != null) {
            val existing = videoRepository.findByContentHash(contentHash)
            if (existing != null && existing.id != videoId) {
                throw DuplicateResourceException("영상", "contentHash=$contentHash")
            }
        }

        videoRepository.update(
            video.copy(
                fileUrl = fileUrl,
                contentHash = contentHash,
                status = UploadStatus.DRAFT,
            )
        )

        // 이미지는 후처리 불필요
        if (video.mediaType == MediaType.IMAGE) {
            return
        }

        // 동영상 후처리 이벤트 발행: probe → thumbnail → caption (트랜스코딩 없이)
        eventPublisher.publishEvent(
            VideoPostProcessEvent(
                videoId = videoId,
                userId = video.userId,
                fileUrl = fileUrl,
            )
        )
    }
}

data class InitUploadResult(
    val videoId: Long,
    val uploadUrl: String,
    val tusEndpoint: String,
    val mediaType: MediaType,
)
