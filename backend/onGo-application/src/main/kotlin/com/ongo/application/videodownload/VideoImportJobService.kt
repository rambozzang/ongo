package com.ongo.application.videodownload

import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * URL 가져오기를 **요청 밖에서** 돌린다.
 *
 * ## 왜 비동기인가
 *
 * 예전에는 POST 한 번이 다운로드·업로드가 끝날 때까지 붙잡혀 있었다. nginx `/api/` 의 읽기 제한은
 * 기본 60초라, 1분 넘게 걸리는 가져오기는 **서버가 끝까지 해내는데도 사용자는 504 실패를 봤다**.
 * 10GB 원본은 수십 분이 걸린다. 연결을 한 시간 붙잡는 것은 모바일·절전에서 쉽게 끊기므로,
 * 작업 번호를 바로 돌려주고 화면이 상태를 묻게 한다.
 *
 * ## 작업 상태는 메모리에 있다 — 의도한 한계
 *
 * 재기동하면 진행 중이던 작업 상태는 사라진다. 그 경우 조회는 `NOT_FOUND` 이고, 화면은 "상태를
 * 확인할 수 없으니 내 영상 목록을 보라" 고 안내한다(영상이 이미 만들어졌을 수 있다). 결과 영상 자체는
 * DB 에 있으므로 유실되지 않는다. 다중 인스턴스가 되면 DB 기반 작업 테이블로 옮겨야 한다.
 *
 * ## 동시 실행 제한
 *
 * 다운로드는 서버 디스크·대역폭을 크게 쓴다(10GB 원본은 병합 중 약 20GB). [maxConcurrent] 개까지만
 * 동시에 받고 나머지는 QUEUED 로 기다린다.
 */
@Service
class VideoImportJobService(
    private val videoDownloadUseCase: VideoDownloadUseCase,
    @param:Value("\${videodownload.max-concurrent-jobs:2}")
    private val maxConcurrent: Int,
) : DisposableBean {
    /** 테스트가 시간을 고정할 수 있게 열어 둔다. */
    internal var clock: Clock = Clock.systemUTC()

    private val log = LoggerFactory.getLogger(javaClass)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val slots = Semaphore(maxConcurrent.coerceAtLeast(1), true)
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    enum class Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    data class Job(
        val id: String,
        val userId: Long,
        val status: Status,
        val createdAt: Instant,
        val finishedAt: Instant? = null,
        val result: VideoDownloadResult? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null,
    )

    /**
     * 작업을 등록하고 바로 돌려준다. **URL 형식과 월 업로드 한도는 여기서 즉시 본다** — 몇 분 기다린 뒤에
     * "지원하지 않는 URL" 을 알려주면 안 된다. 최종 판정은 실행 중에 [VideoDownloadUseCase] 가 다시 한다.
     */
    fun start(userId: Long, request: VideoDownloadRequest): Job {
        evictExpired()
        VideoDownloadUrl.parse(request.url)
        videoDownloadUseCase.precheck(userId)

        val job = Job(id = UUID.randomUUID().toString(), userId = userId, status = Status.QUEUED, createdAt = clock.instant())
        jobs[job.id] = job
        executor.submit { run(job.id, userId, request) }
        return job
    }

    /** 남의 작업은 없는 것과 같다 — 존재 여부도 드러내지 않는다. */
    fun get(userId: Long, jobId: String): Job {
        evictExpired()
        return jobs[jobId]?.takeIf { it.userId == userId } ?: throw NotFoundException("가져오기 작업", jobId)
    }

    private fun run(jobId: String, userId: Long, request: VideoDownloadRequest) {
        slots.acquire()
        try {
            jobs.computeIfPresent(jobId) { _, job -> job.copy(status = Status.RUNNING) }
            val result = videoDownloadUseCase.importVideo(userId, request)
            finish(jobId) { it.copy(status = Status.SUCCEEDED, result = result) }
        } catch (e: BusinessException) {
            finish(jobId) { it.copy(status = Status.FAILED, errorCode = e.code, errorMessage = e.message) }
        } catch (e: Exception) {
            log.error("URL 가져오기 작업 실패: jobId={}, userId={}", jobId, userId, e)
            finish(jobId) {
                it.copy(status = Status.FAILED, errorCode = "VIDEO_DOWNLOAD_FAILED", errorMessage = "소스 영상을 가져오지 못했습니다.")
            }
        } finally {
            slots.release()
        }
    }

    private fun finish(jobId: String, update: (Job) -> Job) {
        jobs.computeIfPresent(jobId) { _, job -> update(job).copy(finishedAt = clock.instant()) }
    }

    /** 끝난 작업은 [RETENTION] 뒤에 지운다. 진행 중인 작업은 지우지 않는다. */
    private fun evictExpired() {
        val cutoff = clock.instant().minus(RETENTION)
        jobs.entries.removeIf { (_, job) -> job.finishedAt?.isBefore(cutoff) == true }
    }

    override fun destroy() {
        executor.shutdownNow()
    }

    companion object {
        val RETENTION: Duration = Duration.ofHours(1)
    }
}
