package com.ongo.domain.video

import com.ongo.common.enums.Platform

interface VideoVariantRepository {
    fun findById(id: Long): VideoVariant?
    fun findByVideoId(videoId: Long): List<VideoVariant>
    fun findByVideoIdAndPlatform(videoId: Long, platform: Platform): VideoVariant?
    fun findByVideoIds(videoIds: List<Long>): Map<Long, List<VideoVariant>>
    fun save(variant: VideoVariant): VideoVariant
    fun update(variant: VideoVariant): VideoVariant
    fun deleteByVideoId(videoId: Long)
}
