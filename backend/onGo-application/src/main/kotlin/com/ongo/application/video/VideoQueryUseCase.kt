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
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.video.ContentImage
import com.ongo.domain.video.ContentImageRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadTarget
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
    private val analyticsRepository: AnalyticsRepository,
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
        val uploadIds = uploadsByVideoId.values.flatten().mapNotNull { it.id }
        val viewsByUploadId = analyticsRepository.findByVideoUploadIds(uploadIds)
            .groupingBy { it.videoUploadId }
            .fold(0L) { total, row -> total + row.views }

        val items = videos.map { video ->
            val vid = video.id!!
            val uploads = uploadsByVideoId[vid] ?: emptyList()
            val filteredUploads = if (platform != null) {
                uploads.filter { it.platform == platform }
            } else {
                uploads
            }
            val totalViews = filteredUploads.sumOf { viewsByUploadId[it.id] ?: 0L }

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
                totalViews = totalViews,
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
                channelId = upload.channelId,
                channelName = upload.channelId?.let { channelRepository.findById(it)?.channelName },
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
            fileUrl = currentFileUrl(video),
            fileSize = video.fileSizeBytes,
            thumbnails = video.thumbnailUrls,
            mediaType = video.mediaType,
            status = video.status,
            contentImages = images,
            uploads = uploadDetails,
            createdAt = video.createdAt,
        )
    }

    /**
     * 응답에만 담을 최신 파일 URL. DB 의 fileUrl 은 건드리지 않는다.
     *
     * S3StorageClient.uploadFile 이 7일짜리 presigned URL 을 돌려주고 그 값이 그대로 저장되기
     * 때문에, 저장 시점에서 7일이 지나면 상세 화면의 프리뷰와 다운로드 링크가 죽는다. 납품이
     * 곧 제품인 쇼츠 파일럿에서는 고객이 지난 결과물을 다시 받지 못한다는 뜻이라, 조회할 때마다
     * 유효한 URL 을 새로 서명해 준다.
     *
     * 재서명이 실패하면 저장된 값을 그대로 돌려준다. 외부 remote URL 이거나 오브젝트가 지워졌거나
     * 스토리지가 흔들릴 때 상세 조회 자체가 500 이 되면 안 되고, 그 경우들의 기존 동작도 그대로
     * 유지돼야 한다. 게시 직전에 StreamPublishUseCase 가 쓰는 것과 같은 방식이다.
     */
    private fun currentFileUrl(video: Video): String? {
        val stored = video.fileUrl
        // 파일이 없는 영상은 스토리지를 부를 이유가 없다.
        if (stored.isNullOrBlank()) return stored
        val videoId = video.id ?: return stored
        return runCatching { storageService.getFileUrl(videoId, stored) }.getOrNull() ?: stored
    }

    @Transactional
    fun updateVideo(
        userId: Long,
        videoId: Long,
        title: String?,
        description: String?,
        tags: List<String>?,
        category: String?,
        thumbnailIndex: Int?,
        platformDrafts: List<VideoPlatformDraft>? = null,
    ): VideoDetailResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }
        if (platformDrafts != null && video.status != UploadStatus.DRAFT) {
            throw IllegalStateException("게시 준비 중이거나 게시된 영상의 플랫폼 초안은 수정할 수 없습니다.")
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

        if (platformDrafts != null) {
            savePlatformDrafts(userId, video, platformDrafts)
        }

        // 플랫폼 메타데이터 동기화 (제목/설명/태그 변경 시)
        if (title != null || description != null || tags != null) {
            val uploads = videoUploadRepository.findByVideoId(videoId)
            uploads.forEach { upload ->
                val platformVideoId = upload.platformVideoId
                if (platformVideoId != null && upload.status == UploadStatus.PUBLISHED) {
                    try {
                        val uploadChannelId = upload.channelId
                        val channel = if (uploadChannelId != null) {
                            channelRepository.findById(uploadChannelId)
                                ?.takeIf { it.userId == userId && it.platform == upload.platform }
                        } else {
                            channelRepository.findByUserIdAndPlatform(userId, upload.platform)
                        }
                        if (channel != null && channel.status == ChannelStatus.ACTIVE) {
                            val accessToken = tokenEncryptionPort.decrypt(channel.accessToken)
                            val updatedRemotely = platformClientPort.updateVideoMetadata(
                                upload.platform,
                                platformVideoId,
                                accessToken,
                                newTitle,
                                newDescription,
                                newTags,
                            )
                            if (updatedRemotely) {
                                log.info("플랫폼 영상 메타데이터 업데이트 완료: platform={}, videoId={}", upload.platform, platformVideoId)
                            } else {
                                log.info("플랫폼이 영상 메타데이터 업데이트를 지원하지 않아 로컬 정보만 저장했습니다: platform={}, videoId={}", upload.platform, platformVideoId)
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("플랫폼 영상 메타데이터 업데이트 실패 (계속 진행): platform={}, videoId={}, error={}", upload.platform, platformVideoId, e.message)
                    }
                }
            }
        }

        return getVideoDetail(userId, videoId)
    }

    /**
     * Save only metadata rows. This path is deliberately restricted to a
     * parent DRAFT and never changes an upload into an active job.
     */
    private fun savePlatformDrafts(userId: Long, video: Video, drafts: List<VideoPlatformDraft>) {
        val duplicates = drafts.groupingBy { it.channelId ?: it.platform }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "같은 게시 계정을 중복 저장할 수 없습니다: ${duplicates.joinToString()}" }
        drafts.forEach { draft ->
            if (draft.channelId != null) {
                val channel = channelRepository.findById(draft.channelId)
                require(channel?.userId == userId && channel.platform == draft.platform) {
                    "게시 계정이 현재 사용자 또는 플랫폼과 일치하지 않습니다: ${draft.platform}/${draft.channelId}"
                }
            }
            require(draft.title.isNotBlank()) { "${draft.platform} 플랫폼별 제목을 입력해주세요." }
            val capability = PlatformUploadCapabilities.get(draft.platform)
            if (capability != null) {
                require(draft.title.length <= capability.maxTitleLength) {
                    "${draft.platform} 제목은 ${capability.maxTitleLength}자까지 입력할 수 있습니다."
                }
                if (capability.maxDescriptionLength > 0) {
                    require(draft.description.orEmpty().length <= capability.maxDescriptionLength) {
                        "${draft.platform} 설명은 ${capability.maxDescriptionLength}자까지 입력할 수 있습니다."
                    }
                }
                require(draft.tags.size <= capability.maxTagCount) {
                    "${draft.platform} 태그는 ${capability.maxTagCount}개까지 입력할 수 있습니다."
                }
                capability.maxCaptionLength?.let { limit ->
                    val caption = PlatformCaptionRules.compose(
                        draft.platform,
                        draft.title,
                        draft.description.orEmpty(),
                        draft.tags,
                    ).orEmpty()
                    require(caption.length <= limit) {
                        "${draft.platform} 게시 문구는 ${limit}자까지 입력할 수 있습니다."
                    }
                }
            }
        }

        val existing = videoUploadRepository.findByVideoId(video.id!!)
        val videoId = video.id!!
        val existingByTarget = existing.associateBy { VideoUploadTarget(it.platform, it.channelId) }
        val requestedTargets = drafts.map { VideoUploadTarget(it.platform, it.channelId) }.toSet()
        val nonEditableRemoved = existing.filter {
            VideoUploadTarget(it.platform, it.channelId) !in requestedTargets &&
                it.status !in setOf(UploadStatus.DRAFT, UploadStatus.CANCELLED)
        }
        require(nonEditableRemoved.isEmpty()) {
            "이미 게시되었거나 게시 중인 플랫폼은 초안에서 제거할 수 없습니다: " +
                nonEditableRemoved.joinToString { it.platform.name }
        }

        drafts.forEach { draft ->
            val existingUpload = existingByTarget[VideoUploadTarget(draft.platform, draft.channelId)]
            val upload = when (existingUpload?.status) {
                null -> videoUploadRepository.save(
                    VideoUpload(
                        videoId = videoId,
                        platform = draft.platform,
                        channelId = draft.channelId,
                        status = UploadStatus.DRAFT,
                    )
                )
                UploadStatus.DRAFT, UploadStatus.CANCELLED -> videoUploadRepository.update(
                    existingUpload.copy(
                        status = UploadStatus.DRAFT,
                        platformVideoId = null,
                        platformUrl = null,
                        errorMessage = null,
                        attemptCount = 0,
                        nextRetryAt = null,
                        leaseOwner = null,
                        leaseUntil = null,
                        pollToken = null,
                        lastError = null,
                        scheduledAt = null,
                        publishedAt = null,
                    )
                )
                else -> throw IllegalStateException(
                    "${draft.platform}는 현재 ${existingUpload.status} 상태라 초안을 수정할 수 없습니다."
                )
            }
            val uploadId = upload.id ?: error("플랫폼 초안 저장 후 ID를 받지 못했습니다.")
            val meta = VideoPlatformMeta(
                videoUploadId = uploadId,
                title = draft.title,
                description = draft.description,
                tags = draft.tags,
                visibility = draft.visibility,
                customThumbnailUrl = draft.customThumbnailUrl,
            )
            val existingMeta = videoPlatformMetaRepository.findByVideoUploadId(uploadId)
            if (existingMeta == null) videoPlatformMetaRepository.save(meta)
            else videoPlatformMetaRepository.update(meta.copy(id = existingMeta.id))
        }

        // A removed platform is removed only while it is still an editable
        // draft. Published/processing rows were rejected above and are kept.
        videoUploadRepository.deleteEditableByVideoIdExceptTargets(videoId, requestedTargets)
    }

    @Transactional
    fun deleteVideo(userId: Long, videoId: Long): VideoDeletionResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        log.info("[AUDIT] 영상 삭제: userId={}, videoId={}, title={}", userId, videoId, video.title)

        // 외부 플랫폼에서 영상 삭제
        val uploads = videoUploadRepository.findByVideoId(videoId)
        val externalFailures = buildList {
            uploads.forEach { upload ->
                val platformVideoId = upload.platformVideoId
                if (platformVideoId != null) {
                    try {
                        val uploadChannelId = upload.channelId
                        val channel = if (uploadChannelId != null) {
                            channelRepository.findById(uploadChannelId)
                                ?.takeIf { it.userId == userId && it.platform == upload.platform }
                        } else {
                            channelRepository.findByUserIdAndPlatform(userId, upload.platform)
                        }
                        if (channel == null || channel.status != ChannelStatus.ACTIVE) {
                            add(
                                ExternalDeletionFailure(
                                    platform = upload.platform,
                                    reason = "연결된 활성 채널을 찾지 못해 외부 영상 삭제를 확인하지 못했습니다.",
                                ),
                            )
                        } else {
                            val accessToken = tokenEncryptionPort.decrypt(channel.accessToken)
                            val deleted = platformClientPort.deleteVideo(upload.platform, platformVideoId, accessToken)
                            if (deleted) {
                                log.info("플랫폼 영상 삭제 완료: platform={}, videoId={}", upload.platform, platformVideoId)
                            } else {
                                add(
                                    ExternalDeletionFailure(
                                        platform = upload.platform,
                                        reason = "플랫폼에서 삭제를 확인하지 못했습니다. 플랫폼에서 직접 확인해 주세요.",
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("플랫폼 영상 삭제 실패 (계속 진행): platform={}, videoId={}, error={}", upload.platform, platformVideoId, e.message)
                        add(
                            ExternalDeletionFailure(
                                platform = upload.platform,
                                reason = "플랫폼 삭제 요청 중 오류가 발생했습니다. 플랫폼에서 직접 확인해 주세요.",
                            ),
                        )
                    }
                }
            }
        }

        // 스토리지에서 파일 삭제
        val storageDeletionFailed = try {
            storageService.deleteFile(videoId)
            false
        } catch (_: Exception) {
            // 파일이 없어도 DB 레코드 삭제는 계속 진행하되, 성공으로 숨기지 않는다.
            true
        }

        // 관련 레코드 삭제
        contentImageRepository.deleteByVideoId(videoId)
        videoRepository.delete(videoId)

        return VideoDeletionResult(
            videoId = videoId,
            externalFailures = externalFailures,
            storageDeletionFailed = storageDeletionFailed,
        )
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

        val savedImages = contentImageRepository.saveAll(images)
        // Image posts use the same durable publish pipeline as videos. Keep
        // the first stored image as the canonical media URL so scheduled and
        // retry paths can resolve a real source without special-case state.
        if (video.mediaType == MediaType.IMAGE && video.fileUrl.isNullOrBlank()) {
            savedImages.firstOrNull()?.imageUrl?.let { imageUrl ->
                videoRepository.update(video.copy(fileUrl = imageUrl))
            }
        }
        return savedImages.map { img ->
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
    val totalViews: Long,
    val createdAt: java.time.LocalDateTime?,
)

data class VideoDeletionResult(
    val videoId: Long,
    val externalFailures: List<ExternalDeletionFailure> = emptyList(),
    val storageDeletionFailed: Boolean = false,
)

data class ExternalDeletionFailure(
    val platform: Platform,
    val reason: String,
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
    val channelId: Long?,
    val channelName: String? = null,
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
