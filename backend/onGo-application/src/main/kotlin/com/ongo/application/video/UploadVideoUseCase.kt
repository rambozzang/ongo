package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.DuplicateResourceException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.FileValidationUtil
import com.ongo.domain.channel.ChannelRepository
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
    private val channelRepository: ChannelRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun initUpload(userId: Long, filename: String, fileSize: Long, contentType: String): InitUploadResult {
        // 1. 파일 검증: 확장자 + MIME 타입 + 크기
        FileValidationUtil.validate(filename, contentType, fileSize)

        // 2. 플랜 한도 확인 (월간 업로드 횟수)
        val subscription = subscriptionRepository.findByUserId(userId)
        val planType = subscription?.planType ?: com.ongo.common.enums.PlanType.FREE
        val monthlyCount = videoRepository.countByUserIdAndMonth(userId, YearMonth.now())
        if (monthlyCount >= planType.monthlyUploads) {
            throw PlanLimitExceededException("월간 업로드", planType.monthlyUploads)
        }

        // 3. Video 레코드 생성 (DRAFT 상태)
        val video = videoRepository.save(
            Video(
                userId = userId,
                title = filename.substringBeforeLast('.'),
                originalFilename = filename,
                fileSizeBytes = fileSize,
                status = UploadStatus.DRAFT,
            )
        )

        // 4. 스토리지 업로드 URL 생성 (Tus 엔드포인트)
        val savedVideoId = video.id!!
        val uploadUrl = storageService.generateUploadUrl(savedVideoId, filename, contentType)
        val tusEndpoint = storageService.getTusEndpoint(savedVideoId)

        return InitUploadResult(
            videoId = savedVideoId,
            uploadUrl = uploadUrl,
            tusEndpoint = tusEndpoint,
        )
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

        // 연결된 채널 기반으로 트랜스코딩 이벤트 발행
        val channels = channelRepository.findByUserId(video.userId)
        if (channels.isNotEmpty()) {
            eventPublisher.publishEvent(
                VideoTranscodingEvent(
                    videoId = videoId,
                    userId = video.userId,
                    fileUrl = fileUrl,
                    platforms = channels.map { it.platform },
                )
            )
        }
    }
}

data class InitUploadResult(
    val videoId: Long,
    val uploadUrl: String,
    val tusEndpoint: String,
)
