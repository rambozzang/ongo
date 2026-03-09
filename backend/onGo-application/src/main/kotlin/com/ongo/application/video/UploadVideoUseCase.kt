package com.ongo.application.video

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UploadVideoUseCase(
    private val videoRepository: VideoRepository,
) {

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
}
