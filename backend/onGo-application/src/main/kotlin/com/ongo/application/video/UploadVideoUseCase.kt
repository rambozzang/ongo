package com.ongo.application.video

import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.FileValidationUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UploadVideoUseCase(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
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

    @Transactional
    fun initiatePresignedUpload(
        userId: Long,
        filename: String,
        contentType: String,
        fileSize: Long,
    ): PresignedUploadResult {
        FileValidationUtil.validate(filename, contentType, fileSize)
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
            uploadUrl = storageService.generateUploadUrl(videoId, filename, contentType),
        )
    }

    @Transactional
    fun confirmPresignedUpload(userId: Long, videoId: Long) {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")

        val fileUrl = storageService.getFileUrl(videoId)
        videoRepository.update(video.copy(fileUrl = fileUrl, status = UploadStatus.DRAFT))
    }
}

data class PresignedUploadResult(val videoId: Long, val uploadUrl: String)
