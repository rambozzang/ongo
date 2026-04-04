package com.ongo.application.config

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * 애플리케이션 전역 스레드 풀 관리
 *
 * Virtual Thread는 가볍지만 무제한 생성 시 외부 리소스(DB 커넥션, 플랫폼 API)에
 * 동시 접근이 폭증할 수 있으므로 Semaphore로 동시 실행 수를 제한합니다.
 */
object ExecutorConfig {

    /** 플랫폼 업로드용 — 동시 업로드 최대 20건 (4플랫폼 × 5건) */
    val uploadSemaphore = Semaphore(20)

    /** AI 배치 처리용 — 동시 AI 호출 최대 5건 */
    val aiBatchSemaphore = Semaphore(5)

    /** Virtual Thread 기반 공유 ExecutorService */
    fun newVirtualExecutor(): ExecutorService =
        Executors.newVirtualThreadPerTaskExecutor()
}
