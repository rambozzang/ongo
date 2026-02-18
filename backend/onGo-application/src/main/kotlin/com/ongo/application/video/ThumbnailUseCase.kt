package com.ongo.application.video

import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.video.VideoRepository
import com.ongo.application.common.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream

@Service
@Transactional(readOnly = true)
class ThumbnailUseCase(
    private val videoRepository: VideoRepository,
    private val storageService: StorageService,
) {

    fun getThumbnails(userId: Long, videoId: Long): ThumbnailResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")

        return ThumbnailResult(
            thumbnails = video.autoThumbnails,
            selectedIndex = video.selectedThumbnailIndex,
        )
    }

    @Transactional
    fun selectThumbnail(userId: Long, videoId: Long, index: Int) {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")

        if (index < 0 || index >= video.autoThumbnails.size) {
            throw IllegalArgumentException("유효하지 않은 썸네일 인덱스: $index (총 ${video.autoThumbnails.size}개)")
        }

        videoRepository.update(video.copy(selectedThumbnailIndex = index))
    }

    @Transactional
    fun uploadCustomThumbnail(userId: Long, videoId: Long, inputStream: InputStream, contentType: String, fileSize: Long): String {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")

        val key = "videos/$videoId/thumbnails/custom_${System.currentTimeMillis()}.jpg"
        val url = storageService.uploadFile(key, inputStream, contentType, fileSize)

        val updatedThumbnails = video.autoThumbnails + url
        val newIndex = updatedThumbnails.size - 1
        videoRepository.update(
            video.copy(
                autoThumbnails = updatedThumbnails,
                selectedThumbnailIndex = newIndex,
            )
        )

        return url
    }
}

data class ThumbnailResult(
    val thumbnails: List<String>,
    val selectedIndex: Int,
)
