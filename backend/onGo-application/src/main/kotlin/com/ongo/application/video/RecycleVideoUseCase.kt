package com.ongo.application.video

import com.ongo.common.enums.Visibility
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Creates a new publishable record from an existing owned video.
 *
 * The media object is intentionally reused: object storage URLs are immutable and
 * copying a large file during a metadata-only recycle would make the operation
 * slow and unnecessarily expensive. The new record owns its publish lifecycle,
 * so retries and platform status remain independent from the original video.
 */
@Service
class RecycleVideoUseCase(
    private val videoRepository: VideoRepository,
    private val publishVideoUseCase: PublishVideoUseCase,
) {

    @Transactional
    fun recycle(
        userId: Long,
        sourceVideoId: Long,
        title: String,
        description: String?,
        tags: List<String>,
        category: String?,
        platforms: List<RecyclePlatformConfig>,
    ): PublishResult {
        val source = videoRepository.findById(sourceVideoId)
            ?: throw NotFoundException("영상", sourceVideoId)
        if (source.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }
        require(!source.fileUrl.isNullOrBlank()) { "재게시할 원본 영상 파일을 찾을 수 없습니다." }
        require(platforms.isNotEmpty()) { "재게시할 플랫폼을 하나 이상 선택해주세요." }

        val recycled = videoRepository.save(
            Video(
                userId = userId,
                title = title.trim(),
                description = description,
                tags = tags,
                category = category,
                fileUrl = source.fileUrl,
                fileSizeBytes = source.fileSizeBytes,
                originalFilename = source.originalFilename,
                thumbnailUrls = source.thumbnailUrls,
                mediaType = source.mediaType,
                source = source.source,
                status = com.ongo.common.enums.UploadStatus.DRAFT,
            )
        )
        val recycledId = requireNotNull(recycled.id) { "재게시 콘텐츠 생성에 실패했습니다." }

        return publishVideoUseCase.publishVideo(
            userId = userId,
            videoId = recycledId,
            configs = platforms.map { platform ->
                PlatformUploadConfig(
                    platform = platform.platform,
                    videoUploadId = 0,
                    channelId = platform.channelId,
                    title = platform.title?.trim().orEmpty().ifBlank { title.trim() },
                    description = platform.description ?: description,
                    tags = platform.tags.ifEmpty { tags },
                    visibility = platform.visibility,
                    thumbnailUrl = platform.thumbnailUrl ?: source.thumbnailUrls.firstOrNull(),
                    scheduledAt = platform.scheduledAt,
                    mediaType = source.mediaType,
                )
            },
        )
    }
}

data class RecyclePlatformConfig(
    val platform: com.ongo.common.enums.Platform,
    /** 같은 플랫폼의 여러 연결 계정을 재활용할 때 선택한 계정. */
    val channelId: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val visibility: Visibility = Visibility.PUBLIC,
    val thumbnailUrl: String? = null,
    val scheduledAt: LocalDateTime? = null,
)
