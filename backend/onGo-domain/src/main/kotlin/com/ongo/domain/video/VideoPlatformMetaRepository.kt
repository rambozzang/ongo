package com.ongo.domain.video

interface VideoPlatformMetaRepository {
    fun findByVideoUploadId(videoUploadId: Long): VideoPlatformMeta?
    fun findByVideoUploadIds(videoUploadIds: List<Long>): Map<Long, VideoPlatformMeta>
    fun save(meta: VideoPlatformMeta): VideoPlatformMeta
    fun update(meta: VideoPlatformMeta): VideoPlatformMeta
}
