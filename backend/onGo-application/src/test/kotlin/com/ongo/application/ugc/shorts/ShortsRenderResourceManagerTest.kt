package com.ongo.application.ugc.shorts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ShortsRenderResourceManagerTest {

    @Test
    fun `동시 렌더 수는 설정된 한도를 넘지 않는다`() {
        val manager = ShortsRenderResourceManager(1)
        val executor = Executors.newFixedThreadPool(2)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val peak = AtomicInteger(0)
        val running = AtomicInteger(0)

        try {
            val first = executor.submit {
                manager.withPermit(1) {
                    peak.updateAndGet { maxOf(it, running.incrementAndGet()) }
                    firstStarted.countDown()
                    assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
                    running.decrementAndGet()
                }
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

            val second = executor.submit {
                manager.withPermit(2) {
                    peak.updateAndGet { maxOf(it, running.incrementAndGet()) }
                    running.decrementAndGet()
                }
            }

            assertTrue(waitUntil { manager.queuedRenders == 1 })
            assertEquals(1, manager.activeRenders)
            assertEquals(1, peak.get())
            releaseFirst.countDown()
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertEquals(0, manager.activeRenders)
            assertEquals(0, manager.queuedRenders)
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `작업 실패 후에도 다음 렌더가 자원을 얻는다`() {
        val manager = ShortsRenderResourceManager(1)

        assertThrows(IllegalStateException::class.java) {
            manager.withPermit(1) { error("ffmpeg failed") }
        }

        var ran = false
        manager.withPermit(2) { ran = true }
        assertTrue(ran)
        assertEquals(0, manager.activeRenders)
        assertEquals(0, manager.queuedRenders)
    }

    @Test
    fun `동시성 제한은 1 이상 32 이하만 허용한다`() {
        assertThrows(IllegalArgumentException::class.java) { ShortsRenderResourceManager(0) }
        assertThrows(IllegalArgumentException::class.java) { ShortsRenderResourceManager(33) }
        assertEquals(2, ShortsRenderResourceManager(2).maxConcurrent)
    }

    private fun waitUntil(predicate: () -> Boolean): Boolean {
        repeat(100) {
            if (predicate()) return true
            Thread.sleep(10)
        }
        return predicate()
    }
}
