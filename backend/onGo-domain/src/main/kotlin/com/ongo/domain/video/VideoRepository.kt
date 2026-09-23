package com.ongo.domain.video

import com.ongo.common.enums.UploadStatus
import java.time.LocalDateTime
import java.time.YearMonth

interface VideoRepository {
    fun findById(id: Long): Video?
    fun findByIds(ids: List<Long>): List<Video>
    fun findByUserId(userId: Long, page: Int, size: Int, status: UploadStatus? = null): List<Video>
    fun countByUserId(userId: Long, status: UploadStatus? = null): Long

    /**
     * 그 달에 만들어진 영상 중 [sources] 에 해당하는 것만 센다. 월 업로드 한도의 근거다.
     * 무엇을 셀지는 [MonthlyUploadPolicy.COUNTED_SOURCES] 가 정한다 — 호출부가 목록을 만들지 말 것.
     */
    fun countByUserIdAndMonthAndSources(
        userId: Long,
        yearMonth: YearMonth,
        sources: Set<com.ongo.domain.contentsource.VideoSource>,
    ): Long
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
     * **생성도 마지막 업로드 활동도** 기준 시각보다 오래된 행뿐이다 — DRAFT 나 게시된 행은 절대 포함되지 않는다.
     *
     * 생성 시각만 보던 때는 3시간 넘게 걸리는 정상 업로드(10GB 를 초당 7Mbps 미만으로 올리는 경우)를
     * 올리는 도중에 지웠다. 활동은 [touchUploadActivity] 가 조각 URL 을 발급할 때마다 남긴다.
     */
    fun findStaleUploading(inactiveSince: LocalDateTime, limit: Int): List<Video>

    /** 업로드가 아직 진행 중이라는 신호. UPLOADING 행의 `updated_at` 만 갱신한다. */
    fun touchUploadActivity(videoId: Long, at: LocalDateTime)
}
