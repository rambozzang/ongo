package com.ongo.application.video

import com.ongo.common.config.PageResponse
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.VariantStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.video.VideoVariantRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class VideoQueryUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val videoVariantRepository: VideoVariantRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    fun listVideos(
        userId: Long,
        page: Int,
        size: Int,
        status: UploadStatus?,
        platform: Platform?,
        search: String?,
    ): PageResponse<VideoListResult> {
        val videos = videoRepository.findByUserId(userId, page, size, status)
        val totalElements = videoRepository.countByUserId(userId, status)

        // Batch fetch all uploads for the video page (eliminates N+1)
        val videoIds = videos.mapNotNull { it.id }
        val uploadsByVideoId = videoUploadRepository.findByVideoIds(videoIds)

        val items = videos.map { video ->
            val vid = video.id!!
            val uploads = uploadsByVideoId[vid] ?: emptyList()
            val filteredUploads = if (platform != null) {
                uploads.filter { it.platform == platform }
            } else {
                uploads
            }

            VideoListResult(
                id = vid,
                title = video.title,
                thumbnailUrl = video.thumbnailUrls.firstOrNull(),
                status = video.status,
                uploads = filteredUploads.map { upload ->
                    PlatformStatusResult(
                        platform = upload.platform,
                        status = upload.status,
                        platformUrl = upload.platformUrl,
                    )
                },
                createdAt = video.createdAt,
            )
        }

        // 검색 필터 적용 (제목/태그)
        val filteredItems = if (!search.isNullOrBlank()) {
            items.filter { item ->
                item.title.contains(search, ignoreCase = true)
            }
        } else {
            items
        }

        return PageResponse.of(filteredItems, page, size, totalElements)
    }

    fun getVideoDetail(userId: Long, videoId: Long): VideoDetailResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        val uploads = videoUploadRepository.findByVideoId(videoId)

        // Batch fetch all platform metas (eliminates N+1)
        val uploadIds = uploads.mapNotNull { it.id }
        val metasByUploadId = videoPlatformMetaRepository.findByVideoUploadIds(uploadIds)

        val uploadDetails = uploads.map { upload ->
            val uploadId = upload.id!!
            val meta = metasByUploadId[uploadId]
            PlatformUploadDetailResult(
                id = uploadId,
                platform = upload.platform,
                status = upload.status,
                platformVideoId = upload.platformVideoId,
                platformUrl = upload.platformUrl,
                errorMessage = upload.errorMessage,
                publishedAt = upload.publishedAt,
                meta = meta?.let {
                    PlatformMetaResult(
                        title = it.title,
                        description = it.description,
                        tags = it.tags,
                        visibility = it.visibility,
                        customThumbnailUrl = it.customThumbnailUrl,
                    )
                },
            )
        }

        // VideoVariant 정보 조회
        val variants = videoVariantRepository.findByVideoId(videoId)
        val variantItems = variants.map { v ->
            VideoVariantResult(
                platform = v.platform,
                status = v.status,
                fileUrl = v.fileUrl,
                fileSizeBytes = v.fileSizeBytes,
                width = v.width,
                height = v.height,
                errorMessage = v.errorMessage,
            )
        }

        return VideoDetailResult(
            id = video.id!!,
            title = video.title,
            description = video.description,
            tags = video.tags,
            category = video.category,
            fileUrl = video.fileUrl,
            fileSize = video.fileSizeBytes,
            duration = video.durationSeconds,
            resolution = video.resolution,
            thumbnails = video.thumbnailUrls,
            status = video.status,
            uploads = uploadDetails,
            variants = variantItems,
            createdAt = video.createdAt,
        )
    }

    @Transactional
    fun updateVideo(userId: Long, videoId: Long, title: String?, description: String?, tags: List<String>?, category: String?, thumbnailIndex: Int?): VideoDetailResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        val updatedVideo = video.copy(
            title = title ?: video.title,
            description = description ?: video.description,
            tags = tags ?: video.tags,
            category = category ?: video.category,
        )
        videoRepository.update(updatedVideo)

        return getVideoDetail(userId, videoId)
    }

    @Transactional
    fun retryTranscode(userId: Long, videoId: Long, platformName: String) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        val platform = safeValueOfOrThrow<Platform>(platformName)
        val variant = videoVariantRepository.findByVideoIdAndPlatform(videoId, platform)
        if (variant != null && variant.status != VariantStatus.FAILED) {
            throw IllegalStateException("실패 상태의 변환본만 재시도할 수 있습니다. 현재 상태: ${variant.status}")
        }

        val fileUrl = video.fileUrl
            ?: throw IllegalStateException("파일 업로드가 완료되지 않은 영상입니다")

        eventPublisher.publishEvent(
            VideoTranscodingEvent(
                videoId = videoId,
                userId = userId,
                fileUrl = fileUrl,
                platforms = listOf(platform),
            )
        )
    }

    @Transactional
    fun deleteVideo(userId: Long, videoId: Long) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        // variant 레코드 삭제 (CASCADE로도 삭제되지만 명시적으로)
        videoVariantRepository.deleteByVideoId(videoId)
        videoRepository.delete(videoId)
    }
}

data class VideoListResult(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val status: UploadStatus,
    val uploads: List<PlatformStatusResult>,
    val createdAt: java.time.LocalDateTime?,
)

data class PlatformStatusResult(
    val platform: Platform,
    val status: UploadStatus,
    val platformUrl: String? = null,
)

data class VideoDetailResult(
    val id: Long,
    val title: String,
    val description: String?,
    val tags: List<String>,
    val category: String?,
    val fileUrl: String?,
    val fileSize: Long?,
    val duration: Int?,
    val resolution: String?,
    val thumbnails: List<String>,
    val status: UploadStatus,
    val uploads: List<PlatformUploadDetailResult>,
    val variants: List<VideoVariantResult> = emptyList(),
    val createdAt: java.time.LocalDateTime?,
)

data class PlatformUploadDetailResult(
    val id: Long,
    val platform: Platform,
    val status: UploadStatus,
    val platformVideoId: String?,
    val platformUrl: String?,
    val errorMessage: String?,
    val publishedAt: java.time.LocalDateTime?,
    val meta: PlatformMetaResult?,
)

data class PlatformMetaResult(
    val title: String?,
    val description: String?,
    val tags: List<String>,
    val visibility: com.ongo.common.enums.Visibility,
    val customThumbnailUrl: String?,
)

data class VideoVariantResult(
    val platform: Platform,
    val status: VariantStatus,
    val fileUrl: String?,
    val fileSizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val errorMessage: String?,
)
