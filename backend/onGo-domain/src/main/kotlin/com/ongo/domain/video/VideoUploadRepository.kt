package com.ongo.domain.video

import com.ongo.common.enums.Platform

interface VideoUploadRepository {
    fun findById(id: Long): VideoUpload?
    fun findByVideoId(videoId: Long): List<VideoUpload>
    fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoUpload>>
    fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoUpload?
    fun findByPlatformAndUserId(platform: Platform, userId: Long): List<VideoUpload>
    fun save(upload: VideoUpload): VideoUpload
    fun update(upload: VideoUpload): VideoUpload
    fun findPendingUploads(): List<VideoUpload>
    fun findByUserId(userId: Long): List<VideoUpload>
}
