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

    internal fun platformUploadLimit(platform: Platform): Int = platformUploadLimits[platform] ?: 5

    /** AI 배치 처리용 — 동시 AI 호출 최대 5건 */
    val aiBatchSemaphore = Semaphore(5)

    /** Virtual Thread 기반 공유 ExecutorService */
    fun newVirtualExecutor(): ExecutorService =
        Executors.newVirtualThreadPerTaskExecutor()
}
