package com.ongo.application.video

import com.ongo.application.analytics.AnalyticsRowPlatforms
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.common.config.PageResponse
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.FileValidationUtil
import com.ongo.application.storage.StorageQuotaUseCase
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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
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
    private val storageQuotaUseCase: StorageQuotaUseCase,
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
        /*
         * **조회수를 수집하는 업로드의 행만 더한다.**
         *
         * `TumblrClient.kt:141` 은 `views` 자리에 `total_notes`(노트 총합)를, Naver Clip 은
         * 아무것도 넣지 않는다. 그대로 합치면 목록의 "총 조회수" 가 실제보다 커진다.
         */
        val rowPlatforms = AnalyticsRowPlatforms.of(uploadsByVideoId.values.flatten())
        val viewsByUploadId = analyticsRepository.findByVideoUploadIds(uploadIds)
            .filter { rowPlatforms.reports(it, PlatformMetricAvailability.VIEWS) }
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
            /*
             * **`?: 0L` 을 하지 않는다.** 집계 행이 없는 업로드는 지도에 키가 없는데,
             * 예전에는 그 자리에 `0` 을 더해 합계에 넣었다. YouTube 에 올렸지만 아직 동기화
             * 전인 영상이 목록에서 **"조회수 0회"** 로 나갔다 — 실제로 0 회였던 영상과
             * 완전히 같은 모양이다.
             *
             * 세 상태를 구분한다.
             *
             * 1. 조회수를 주는 업로드가 아예 없다 → `null`, 대기 0 건.
             * 2. 있지만 집계 행이 하나도 없다 → `null`, 대기 N 건(기다리면 채워진다).
             * 3. 일부만 집계됐다 → **잰 것의 합**을 그대로 주고 대기 건수를 함께 알린다.
             *    측정값을 버리지도, 미수집을 합계에 숨기지도 않는다.
             *
             * 행이 있고 그 합이 0 이면 그 0 은 **실측**이다(그 키는 지도에 있다).
             */
            val viewReportingUploads = filteredUploads.filter {
                PlatformMetricAvailability.isAvailable(it.platform.name, PlatformMetricAvailability.VIEWS)
            }
            val measuredViews = viewReportingUploads.mapNotNull { upload ->
                upload.id?.let { viewsByUploadId[it] }
            }
            val totalViews = if (measuredViews.isEmpty()) null else measuredViews.sum()
            val pendingViewUploads = viewReportingUploads.size - measuredViews.size

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
                pendingViewUploads = pendingViewUploads,
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

        /*
         * 스토리지 정리. **영상 객체와 게시 이미지 객체를 모두** 지운다.
         *
         * 예전에는 `videos/{videoId}/` 만 지웠다. 이미지는 `content/{videoId}/` 라 닿지
         * 않아, 행은 사라지고 객체만 남는 고아가 됐다 — 사용자에게는 안 보이는데 용량은
         * 계속 나간다.
         *
         * 둘 중 하나만 실패해도 실패로 보고한다. 어느 쪽이든 파일이 남았다는 뜻이고,
         * 그것을 성공으로 숨기면 사용자가 확인할 기회를 잃는다. 실패해도 DB 행 삭제는
         * 계속한다 — 지우지 못한 객체는 로그로 추적하고, 사용자를 붙잡아 두지 않는다.
         */
        val videoObjectsFailed = runCatching { storageService.deleteFile(videoId) }
            .onFailure { log.warn("영상 객체 정리 실패: videoId={}", videoId, it) }
            .isFailure
        val imageObjectsFailed = runCatching { storageService.deleteContentImages(videoId) }
            .onFailure { log.warn("게시 이미지 객체 정리 실패: videoId={}", videoId, it) }
            .isFailure
        val storageDeletionFailed = videoObjectsFailed || imageObjectsFailed

        // 관련 레코드 삭제
        contentImageRepository.deleteByVideoId(videoId)
        videoRepository.delete(videoId)

        return VideoDeletionResult(
            videoId = videoId,
            externalFailures = externalFailures,
            storageDeletionFailed = storageDeletionFailed,
        )
    }

    /**
     * 게시 이미지를 올린다. **쿼터를 세고, 실패하면 올린 것을 되돌린다.**
     *
     * ## 쿼터
     *
     * 이미지도 우리 버킷을 차지한다. 영상·에셋은 검사하는데 이미지만 빠져 있어 요금제
     * 한도를 그대로 우회할 수 있었다. 검사 기준은 `MultipartFile.size` — 클라이언트가
     * 신고한 값이 아니라 **서버가 실제로 받아 버퍼링한 바이트 수**다.
     *
     * 장별이 아니라 합계로 본다. 나눠 보면 한도 직전에서 각각은 통과하고 합쳐서 넘긴다.
     * 검사와 행 저장이 같은 트랜잭션에 있어야 [StorageQuotaUseCase.checkQuota] 가 잡는
     * 사용자 행 잠금이 의미를 갖는다(그 사이 다른 요청이 끼어들지 못한다).
     *
     * ## 실패하면 고아를 남기지 않는다
     *
     * 트랜잭션은 DB 행만 되돌린다. 스토리지는 트랜잭션 밖이라 이미 올라간 객체는 그대로
     * 남고, 그것을 가리키던 행은 사라진다 — 아무도 찾을 수 없는데 과금만 되는 고아다.
     * 그래서 올린 키를 들고 있다가 두 경로 모두에서 되돌린다.
     *
     *  - 이 메서드 안에서 던진 경우: `catch` 가 지운다.
     *  - 여기서는 정상 종료했는데 **커밋이 실패한 경우**: `afterCompletion` 이 지운다.
     *    try/catch 만으로는 이 창을 못 막는다. [com.ongo.application.credit.CreditService]
     *    가 환불 영수증에 쓰는 것과 같은 방식이다.
     *
     * 키는 **올리기 전에** 목록에 넣는다. 올린 뒤에 넣으면 `uploadFile` 의 후처리(URL 생성)가
     * 던졌을 때 객체는 남고 키는 없는 창이 생긴다.
     *
     * 정리 자체가 실패하면 삼키지 않고 error 로 남긴다. 그 로그가 객체를 찾을 유일한 단서다.
     */
    @Transactional
    fun uploadContentImages(userId: Long, videoId: Long, files: List<MultipartFile>): List<ContentImageResult> {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("콘텐츠", videoId)
        if (video.userId != userId) {
            throw ForbiddenException("해당 콘텐츠에 대한 접근 권한이 없습니다")
        }

        // 검증을 먼저 끝낸다. 한 장이라도 규격에 맞지 않으면 아무것도 올리지 않는다.
        val prepared = files.mapIndexed { index, file ->
            val filename = file.originalFilename ?: "image_${index}.jpg"
            val contentType = file.contentType ?: "image/jpeg"
            FileValidationUtil.validateImage(filename, contentType, file.size)
            Triple(file, filename, contentType)
        }
        // 실제로 받은 바이트의 합계로 본다. 한 바이트도 올리기 전에 거절해야 고아가 없다.
        storageQuotaUseCase.checkQuota(userId, prepared.sumOf { it.first.size })

        val existingCount = contentImageRepository.findByVideoId(videoId).size
        val uploadedKeys = mutableListOf<String>()
        registerStorageRollback(videoId, uploadedKeys)

        val savedImages = try {
            val images = prepared.mapIndexed { index, (file, filename, contentType) ->
                val key = "content/$videoId/images/${System.currentTimeMillis()}_$filename"
                /*
                 * **키를 만든 즉시, 올리기 전에 등록한다.**
                 *
                 * `uploadFile` 은 객체를 만든 뒤 URL 을 만들어 돌려준다. 반환 뒤에 등록하면
                 * 그 후처리에서 던졌을 때 **객체는 이미 스토리지에 있는데** 키가 보상 목록에
                 * 없어 고아가 된다. 실패를 되돌린다고 믿는 코드가 남기는 고아라 더 나쁘다.
                 *
                 * 만들어지지 않은 객체의 키를 미리 등록해도 손해는 없다 — 없는 키를 지우는
                 * 것은 S3·MinIO 모두 무해한 no-op 이고, 정리는 건별 `runCatching` 이다.
                 */
                uploadedKeys += key
                val imageUrl = storageService.uploadFile(key, file.inputStream, contentType, file.size)

                ContentImage(
                    videoId = videoId,
                    imageUrl = imageUrl,
                    displayOrder = existingCount + index,
                    fileSizeBytes = file.size,
                    originalFilename = filename,
                    contentType = contentType,
                    // 서버가 할당한 정확한 키. 탈퇴 정리가 추측 없이 지울 근거다.
                    storageObjectKey = key,
                )
            }
            contentImageRepository.saveAll(images)
        } catch (e: Throwable) {
            discardUploadedImages(videoId, uploadedKeys)
            throw e
        }
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

    /**
     * 커밋이 실패한 경우의 보상. 여기까지 예외 없이 왔어도 커밋 자체는 실패할 수 있고,
     * 그때 행은 사라지는데 객체는 남는다.
     *
     * 트랜잭션이 없으면(단위 테스트 등) 등록할 곳이 없으므로 `catch` 경로만 동작한다.
     */
    private fun registerStorageRollback(videoId: Long, uploadedKeys: MutableList<String>) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        discardUploadedImages(videoId, uploadedKeys)
                    }
                }
            },
        )
    }

    /**
     * 올라간 이미지 객체를 되돌린다. **비운 목록을 남겨** `catch` 와 `afterCompletion` 이
     * 모두 도는 경우에도 같은 키를 두 번 지우지 않는다(두 번 지워도 무해하지만, 지웠다는
     * 로그가 두 번 나오면 추적이 어려워진다).
     */
    private fun discardUploadedImages(videoId: Long, uploadedKeys: MutableList<String>) {
        if (uploadedKeys.isEmpty()) return
        val keys = uploadedKeys.toList()
        uploadedKeys.clear()
        log.warn("이미지 업로드 실패 — 올린 객체 {}건을 되돌린다. videoId={}", keys.size, videoId)
        keys.forEach { key ->
            runCatching { storageService.deleteFileByKey(key) }
                .onFailure { log.error("이미지 객체 정리 실패 — 수기 확인 대상. videoId={} key={}", videoId, key, it) }
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
    /**
     * 이 영상의 총 조회수. **잰 적이 없으면 `null`** 이다.
     *
     * `null` 이 되는 경우는 두 가지이고 [pendingViewUploads] 로 구분한다.
     *
     * - 조회수를 수집하는 업로드가 하나도 없다(대기 0 건). `TumblrClient.kt:141` 은
     *   `views` 자리에 `total_notes`(노트 총합)를 넣고 Naver Clip 은 분석 API 자체가 없다.
     * - 있지만 아직 집계 행이 하나도 없다(대기 N 건). 기다리면 채워진다.
     *
     * 일부 업로드만 집계됐으면 **잰 것의 합**이 들어간다 — 측정값을 버리지 않는다.
     * 그리고 집계 행이 있고 그 합이 0 이면 그 `0` 은 **실측**이다.
     */
    val totalViews: Long?,
    /**
     * 조회수를 수집하는 업로드 중 **아직 집계 행이 없는** 개수.
     *
     * `0` 보다 크면 [totalViews] 가 그 업로드들을 포함하지 않는다는 뜻이다. 예전에는
     * 그 자리에 `0` 을 더해 합계에 숨겼다 — 동기화 전 영상이 "조회수 0회" 로 보였다.
     */
    val pendingViewUploads: Int = 0,
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
