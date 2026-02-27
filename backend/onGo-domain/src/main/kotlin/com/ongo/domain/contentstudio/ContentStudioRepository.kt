package com.ongo.domain.contentstudio

interface ContentStudioRepository {
    fun findClipById(id: Long): ContentClip?
    fun findClipsByUserId(userId: Long): List<ContentClip>
    fun saveClip(clip: ContentClip): ContentClip
    fun updateClip(clip: ContentClip): ContentClip
    fun deleteClip(id: Long)

    fun findCaptionById(id: Long): VideoCaption?
    fun findCaptionsByVideoId(videoId: Long): List<VideoCaption>
    fun saveCaption(caption: VideoCaption): VideoCaption
    fun updateCaption(caption: VideoCaption): VideoCaption
    fun deleteCaption(id: Long)

    fun findThumbnailById(id: Long): AiThumbnail?
    fun findThumbnailsByVideoId(videoId: Long): List<AiThumbnail>
    fun saveThumbnail(thumbnail: AiThumbnail): AiThumbnail
    fun deleteThumbnail(id: Long)
}
