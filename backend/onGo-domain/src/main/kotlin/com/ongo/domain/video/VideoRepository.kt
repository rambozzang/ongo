package com.ongo.domain.video

import com.ongo.common.enums.UploadStatus
import java.time.YearMonth

interface VideoRepository {
    fun findById(id: Long): Video?
    fun findByIds(ids: List<Long>): List<Video>
    fun findByUserId(userId: Long, page: Int, size: Int, status: UploadStatus? = null): List<Video>
    fun countByUserId(userId: Long, status: UploadStatus? = null): Long
    fun countByUserIdAndMonth(userId: Long, yearMonth: YearMonth): Long
    fun save(video: Video): Video
    fun update(video: Video): Video
    fun delete(id: Long)
    fun findByContentHash(contentHash: String): Video?
}
