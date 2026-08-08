package com.ongo.application.video

import com.ongo.common.config.PageResponse
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.FileValidationUtil
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.ContentImage
import com.ongo.domain.video.ContentImageRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional(readOnly = true)
class VideoQueryUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val contentImageRepository: ContentImageRepository,
    private val storageService: StorageService,
    private val channelRepository: ChannelRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val platformClientPort: PlatformClientPort,
) {

    private val log = LoggerFactory.getLogger(VideoQueryUseCase::class.java)

    fun validateOwnership(userId: Long, videoId: Long) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }
    }

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
                mediaType = video.mediaType,
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

        // ContentImage 목록 조회 (이미지 타입인 경우)
        val images = if (video.mediaType == MediaType.IMAGE) {
            contentImageRepository.findByVideoId(videoId).map { img ->
                ContentImageResult(
                    id = img.id,
                    imageUrl = img.imageUrl,
                    displayOrder = img.displayOrder,
                    width = img.width,
                    height = img.height,
                    fileSizeBytes = img.fileSizeBytes,
                    originalFilename = img.originalFilename,
                )
            }
        } else {
            emptyList()
        }

        return VideoDetailResult(
            id = video.id!!,
            title = video.title,
            description = video.description,
            tags = video.tags,
            category = video.category,
            fileUrl = video.fileUrl,
            fileSize = video.fileSizeBytes,
            thumbnails = video.thumbnailUrls,
            mediaType = video.mediaType,
            status = video.status,
            contentImages = images,
            uploads = uploadDetails,
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

        val newTitle = title ?: video.title
        val newDescription = description ?: video.description ?: ""
        val newTags = tags ?: video.tags

        val updatedVideo = video.copy(
            title = newTitle,
            description = newDescription,
            tags = newTags,
            category = category ?: video.category,
        )
        videoRepository.update(updatedVideo)

        // 플랫폼 메타데이터 동기화 (제목/설명/태그 변경 시)
        if (title != null || description != null || tags != null) {
            val uploads = videoUploadRepository.findByVideoId(videoId)
            uploads.forEach { upload ->
                val platformVideoId = upload.platformVideoId
                if (platformVideoId != null && upload.status == UploadStatus.PUBLISHED) {
                    try {
                        val channel = channelRepository.findByUserIdAndPlatform(userId, upload.platform)
                        if (channel != null && channel.status == ChannelStatus.ACTIVE) {
                            val accessToken = tokenEncryptionPort.decrypt(channel.accessToken).value
                            platformClientPort.updateVideoMetadata(
                                upload.platform,
                                platformVideoId,
                                accessToken,
                                newTitle,
                                newDescription,
                                newTags,
                            )
                            log.info("플랫폼 영상 메타데이터 업데이트 완료: platform={}, videoId={}", upload.platform, platformVideoId)
                        }
                    } catch (e: Exception) {
                        log.warn("플랫폼 영상 메타데이터 업데이트 실패 (계속 진행): platform={}, videoId={}, error={}", upload.platform, platformVideoId, e.message)
                    }
                }
            }
        }

        return getVideoDetail(userId, videoId)
    }

    @Transactional
    fun deleteVideo(userId: Long, videoId: Long) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        log.info("[AUDIT] 영상 삭제: userId={}, videoId={}, title={}", userId, videoId, video.title)

        // 외부 플랫폼에서 영상 삭제
        val uploads = videoUploadRepository.findByVideoId(videoId)
        uploads.forEach { upload ->
            val platformVideoId = upload.platformVideoId
            if (platformVideoId != null) {
                try {
                    val channel = channelRepository.findByUserIdAndPlatform(userId, upload.platform)
                    if (channel != null && channel.status == ChannelStatus.ACTIVE) {
                        val accessToken = tokenEncryptionPort.decrypt(channel.accessToken).value
                        platformClientPort.deleteVideo(upload.platform, platformVideoId, accessToken)
                        log.info("플랫폼 영상 삭제 완료: platform={}, videoId={}", upload.platform, platformVideoId)
                    }
                } catch (e: Exception) {
                    log.warn("플랫폼 영상 삭제 실패 (계속 진행): platform={}, videoId={}, error={}", upload.platform, platformVideoId, e.message)
                }
            }
        }

        // 스토리지에서 파일 삭제
        try {
            storageService.deleteFile(videoId)
        } catch (_: Exception) {
            // 파일이 없어도 DB 레코드 삭제는 계속 진행
        }

        // 관련 레코드 삭제
        contentImageRepository.deleteByVideoId(videoId)
        videoRepository.delete(videoId)
    }

    @Transactional
    fun uploadContentImages(userId: Long, videoId: Long, files: List<MultipartFile>): List<ContentImageResult> {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("콘텐츠", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 콘텐츠에 대한 접근 권한이 없습니다")
        }

        val existingCount = contentImageRepository.findByVideoId(videoId).size
        val images = files.mapIndexed { index, file ->
            val filename = file.originalFilename ?: "image_${index}.jpg"
            val contentType = file.contentType ?: "image/jpeg"
            FileValidationUtil.validateImage(filename, contentType, file.size)

            val key = "content/$videoId/images/${System.currentTimeMillis()}_$filename"
            val imageUrl = storageService.uploadFile(key, file.inputStream, contentType, file.size)

            ContentImage(
                videoId = videoId,
                imageUrl = imageUrl,
                displayOrder = existingCount + index,
                fileSizeBytes = file.size,
                originalFilename = filename,
                contentType = contentType,
            )
        }

        return contentImageRepository.saveAll(images).map { img ->
            ContentImageResult(
                id = img.id,
                imageUrl = img.imageUrl,
                displayOrder = img.displayOrder,
                width = img.width,
                height = img.height,
                fileSizeBytes = img.fileSizeBytes,
                originalFilename = img.originalFilename,
            )
        }
    }

    fun getContentImages(userId: Long, videoId: Long): List<ContentImageResult> {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("콘텐츠", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 콘텐츠에 대한 접근 권한이 없습니다")
        }

        return contentImageRepository.findByVideoId(videoId).map { img ->
            ContentImageResult(
                id = img.id,
                imageUrl = img.imageUrl,
                displayOrder = img.displayOrder,
                width = img.width,
                height = img.height,
                fileSizeBytes = img.fileSizeBytes,
                originalFilename = img.originalFilename,
            )
        }
    }

    @Transactional
    fun reorderContentImages(userId: Long, videoId: Long, imageIds: List<Long>) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("콘텐츠", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 콘텐츠에 대한 접근 권한이 없습니다")
        }

        contentImageRepository.updateOrder(videoId, imageIds)
    }
}

data class VideoListResult(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val mediaType: MediaType = MediaType.VIDEO,
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
    val thumbnails: List<String>,
    val mediaType: MediaType = MediaType.VIDEO,
    val status: UploadStatus,
    val contentImages: List<ContentImageResult> = emptyList(),
    val uploads: List<PlatformUploadDetailResult>,
    val createdAt: java.time.LocalDateTime?,
)

data class ContentImageResult(
    val id: Long?,
    val imageUrl: String,
    val displayOrder: Int,
    val width: Int?,
    val height: Int?,
    val fileSizeBytes: Long?,
    val originalFilename: String?,
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
