package com.ongo.application.ugc.shorts

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.nanoseconds

/**
 * 서버의 CPU/디스크를 오래 점유하는 렌더 작업의 동시 실행을 제한한다.
 *
 * 렌더 작업은 이 세마포어를 기다리는 동안 일반 요청 스레드를 점유하지 않아야 한다.
 * 호출자는 별도의 렌더 작업 실행기에서 [withPermit]을 호출하고, 작업 상태는 세마포어
 * 대기와 무관하게 QUEUED로 먼저 저장해야 재시작 후에도 작업을 복구할 수 있다.
 */
@Component
class ShortsRenderResourceManager(
    @Value("\${shorts.render.max-concurrent:1}") maxConcurrent: Int,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val permits = Semaphore(validateLimit(maxConcurrent), true)
    private val queued = AtomicInteger(0)
    private val active = AtomicInteger(0)

    val maxConcurrent: Int = validateLimit(maxConcurrent)

    /** 현재 대기 중인 렌더 작업 수(세마포어를 기다리는 작업). */
    val queuedRenders: Int
        get() = queued.get()

    /** 현재 ffmpeg를 실행 중인 렌더 작업 수. */
    val activeRenders: Int
        get() = active.get()

    /**
     * 렌더 자원을 확보한 뒤 블록을 실행한다.
     *
     * 대기는 의도적으로 무기한이지만 interrupt에는 즉시 반응한다. 큐 타임아웃으로
     * 작업을 실패시키면 일시적인 부하가 영구 실패로 바뀌므로, 영속 작업 상태(QUEUED)
     * 와 재시도 정책은 호출자/작업 저장소가 담당한다.
     */
    fun <T> withPermit(runId: Long, block: () -> T): T {
        val queuedAt = System.nanoTime()
        queued.incrementAndGet()
        log.info(
            "shorts_render event=queued runId={} queued={} active={} capacity={}",
            runId,
            queuedRenders,
            activeRenders,
            maxConcurrent,
        )

        var acquired = false
        try {
            try {
                permits.acquire()
                acquired = true
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("shorts_render event=queue_interrupted runId={} outcome=FAILED", runId)
                throw ShortsRenderCapacityException("렌더 작업이 취소되어 대기열에서 제거되었습니다", e)
            }

            queued.decrementAndGet()
            active.incrementAndGet()
            val waitMillis = (System.nanoTime() - queuedAt).nanoseconds.inWholeMilliseconds
            log.info(
                "shorts_render event=started runId={} queueWaitMs={} queued={} active={} capacity={}",
                runId,
                waitMillis,
                queuedRenders,
                activeRenders,
                maxConcurrent,
            )

            val startedAt = System.nanoTime()
            return try {
                block().also {
                    log.info(
                        "shorts_render event=completed runId={} durationMs={} active={} capacity={}",
                        runId,
                        (System.nanoTime() - startedAt).nanoseconds.inWholeMilliseconds,
                        activeRenders,
                        maxConcurrent,
                    )
                }
            } catch (e: Exception) {
                log.warn(
                    "shorts_render event=failed runId={} durationMs={} errorType={} outcome=FAILED",
                    runId,
                    (System.nanoTime() - startedAt).nanoseconds.inWholeMilliseconds,
                    e::class.simpleName ?: "Unknown",
                )
                throw e
            } finally {
                active.decrementAndGet()
                permits.release()
            }
        } finally {
            // 정상 획득 후에는 아래에서 감소했고, 인터럽트로 획득하지 못한 경우만
            // 여기서 대기 카운터를 보정한다.
            if (!acquired) queued.decrementAndGet()
        }
    }

    companion object {
        private fun validateLimit(value: Int): Int =
            value.takeIf { it in 1..32 }
                ?: throw IllegalArgumentException("shorts.render.max-concurrent must be between 1 and 32")
    }
}

class ShortsRenderCapacityException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
