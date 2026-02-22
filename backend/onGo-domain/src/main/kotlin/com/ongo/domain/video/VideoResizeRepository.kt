package com.ongo.domain.video

import com.ongo.domain.video.entity.VideoResize

interface VideoResizeRepository {
    fun findById(id: Long): VideoResize?
    fun findByOriginalVideoId(videoId: Long): List<VideoResize>
    fun findByUserId(userId: Long): List<VideoResize>
    fun save(resize: VideoResize): VideoResize
    fun updateStatus(id: Long, status: String, fileUrl: String? = null, fileSizeBytes: Long? = null, errorMessage: String? = null)
}
