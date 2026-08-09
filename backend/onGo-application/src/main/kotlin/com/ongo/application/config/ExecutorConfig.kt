package com.ongo.application.config

import com.ongo.common.enums.Platform
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ConcurrentHashMap

/**
 * 애플리케이션 전역 스레드 풀 관리
 *
 * Virtual Thread는 가볍지만 무제한 생성 시 외부 리소스(DB 커넥션, 플랫폼 API)에
 * 동시 접근이 폭증할 수 있으므로 Semaphore로 동시 실행 수를 제한합니다.
 */
object ExecutorConfig {

    /** 플랫폼 업로드용 — 한 JVM에서 동시에 실행할 수 있는 외부 업로드 상한 */
    val uploadSemaphore = Semaphore(20)

    /** 스트리밍 작업의 임시 파일 처리 상한. 외부 API 상한과 분리한다. */
    val streamingJobSemaphore = Semaphore(20)

    /**
     * 플랫폼별 API rate limit에 맞춘 상한이다.
     * 알 수 없는/아직 명시되지 않은 플랫폼은 보수적으로 5건만 허용한다.
     */
    private val platformUploadLimits = mapOf(
        Platform.YOUTUBE to 20,
        Platform.TIKTOK to 20,
        Platform.INSTAGRAM to 10,
        Platform.NAVER_CLIP to 5,
        Platform.THREADS to 10,
        Platform.TWITTER to 5,
        Platform.FACEBOOK to 5,
    )
    private val platformUploadSemaphores = ConcurrentHashMap<Platform, Semaphore>()

    fun platformUploadSemaphore(platform: Platform): Semaphore =
        platformUploadSemaphores.computeIfAbsent(platform) {
            Semaphore(platformUploadLimits[platform] ?: 5, true)
        }

    /**
     * PostgreSQL advisory-lock 슬롯. JVM Semaphore와 동일한 개수로 만들되,
     * 모든 인스턴스가 같은 ID를 사용해 클러스터 전체 한도를 공유한다.
     */
    fun platformUploadLockIds(platform: Platform): List<Long> {
        val count = platformUploadLimits[platform] ?: 5
        val platformNamespace = (platform.ordinal + 1).toLong() shl 20
        return (0 until count).map { SLOT_LOCK_NAMESPACE or platformNamespace or it.toLong() }
    }

    internal fun platformUploadLimit(platform: Platform): Int = platformUploadLimits[platform] ?: 5

    /** AI 배치 처리용 — 동시 AI 호출 최대 5건 */
    val aiBatchSemaphore = Semaphore(5)

    /** Virtual Thread 기반 공유 ExecutorService */
    fun newVirtualExecutor(): ExecutorService =
        Executors.newVirtualThreadPerTaskExecutor()

    private const val SLOT_LOCK_NAMESPACE = 0x4f4e474f00000000L
}
