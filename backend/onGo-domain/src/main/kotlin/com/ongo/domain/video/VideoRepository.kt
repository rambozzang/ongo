package com.ongo.domain.video

import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime
import java.time.YearMonth

interface VideoRepository {
    fun findById(id: Long): Video?
    fun findByIds(ids: List<Long>): List<Video>
    fun findByUserId(userId: Long, page: Int, size: Int, status: UploadStatus? = null): List<Video>
    fun countByUserId(userId: Long, status: UploadStatus? = null): Long
    fun countByUserIdAndMonth(userId: Long, yearMonth: YearMonth): Long
    fun save(video: Video): Video
    fun update(video: Video): Video
    /** Atomically reserves a DRAFT for one publish request. */
    fun claimForPublish(userId: Long, videoId: Long): Boolean
    fun delete(id: Long)

    /**
     * 확정되지 않은 채 방치된 업로드 행.
     *
     * presigned URL 이 만료되면 사용자는 더 이상 그 행을 완료할 수 없는데, 그 사이 업로드된
     * 오브젝트는 스토리지에 남아 계속 과금된다. 회수 대상은 UPLOADING 이면서 fileUrl 이 없고
     * 생성된 지 기준 시각보다 오래된 행뿐이다 — DRAFT 나 게시된 행은 절대 포함되지 않는다.
     */
    fun findStaleUploading(createdBefore: LocalDateTime, limit: Int): List<Video>
}
