package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentstudio.*
import org.springframework.stereotype.Repository

@Repository
class ContentStudioStubRepository : ContentStudioRepository {
    override fun findClipById(id: Long): ContentClip? = null
    override fun findClipsByUserId(userId: Long): List<ContentClip> = emptyList()
    override fun saveClip(clip: ContentClip): ContentClip = clip.copy(id = 1)
    override fun updateClip(clip: ContentClip): ContentClip = clip
    override fun deleteClip(id: Long) {}

    override fun findCaptionById(id: Long): VideoCaption? = null
    override fun findCaptionsByVideoId(videoId: Long): List<VideoCaption> = emptyList()
    override fun saveCaption(caption: VideoCaption): VideoCaption = caption.copy(id = 1)
    override fun deleteCaption(id: Long) {}

    override fun findThumbnailById(id: Long): AiThumbnail? = null
    override fun findThumbnailsByVideoId(videoId: Long): List<AiThumbnail> = emptyList()
    override fun saveThumbnail(thumbnail: AiThumbnail): AiThumbnail = thumbnail.copy(id = 1)
    override fun deleteThumbnail(id: Long) {}
}
