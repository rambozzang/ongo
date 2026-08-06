package com.ongo.infrastructure.lock

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * 분산 락의 실제 동작을 PostgreSQL 로 고정한다.
 *
 * `pg_try_advisory_lock` 은 세션 락이라 커넥션 풀과 조합하면 함정이 많다.
 * 획득과 해제가 다른 커넥션에서 일어나면 해제가 조용히 무시되고 락이 풀에 남아
 * 이후 배치가 통째로 스킵된다. 이 파일이 그 계약을 지킨다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
// infrastructure.lock 은 InfrastructureTestApplication 스캔 범위 밖이라 직접 등록한다.
@Import(DistributedLockService::class)
class DistributedLockServiceIT {

    @Autowired lateinit var lock: DistributedLockService
    @Autowired lateinit var dataSource: DataSource

    private lateinit var appender: ListAppender<ILoggingEvent>
    private lateinit var serviceLogger: Logger

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }

        private const val LOCK_ID = 990_001L
    }

    @BeforeEach
    fun setUp() {
        serviceLogger = LoggerFactory.getLogger(DistributedLockService::class.java) as Logger
        appender = ListAppender<ILoggingEvent>().apply { start() }
        serviceLogger.addAppender(appender)
        serviceLogger.level = Level.INFO
    }

    @AfterEach
    fun tearDown() {
        serviceLogger.detachAppender(appender)
        appender.stop()
    }

    private fun errorLogs() = appender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }

    /** 별도 세션에서 락을 잡고 유지한다. 반환된 커넥션을 닫으면 락이 풀린다. */
    private fun holdLockInSeparateSession(lockId: Long): Connection {
        val conn = dataSource.connection
        conn.prepareStatement("SELECT pg_advisory_lock(?)").use { stmt ->
            stmt.setLong(1, lockId)
            stmt.execute()
        }
        return conn
    }

    private fun lockIsFree(lockId: Long): Boolean =
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
                stmt.setLong(1, lockId)
                val got = stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
                if (got) {
                    conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { u ->
                        u.setLong(1, lockId); u.execute()
                    }
                }
                got
            }
        }

    @Test
    @DisplayName("동시에 여러 배치가 돌아도 정확히 하나만 실행된다")
    fun onlyOneWinnerAmongConcurrentRuns() {
        val workers = 6
        val pool = Executors.newFixedThreadPool(workers)
        val startLine = CountDownLatch(1)
        val executed = AtomicInteger(0)
        val inside = CountDownLatch(1)

        try {
            val futures = (1..workers).map {
                pool.submit(Callable {
                    startLine.await()
                    lock.withLock(LOCK_ID) {
                        executed.incrementAndGet()
                        // 승자가 락을 쥔 동안 나머지가 시도하도록 잠시 붙잡는다
                        inside.await(2, TimeUnit.SECONDS)
                    }
                })
            }
            startLine.countDown()
            Thread.sleep(300)
            inside.countDown()
            val ran = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, ran.count { it }, "정확히 하나만 true 여야 한다: $ran")
            assertEquals(1, executed.get(), "블록이 한 번만 실행돼야 한다")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    @DisplayName("블록에서 예외가 나도 finally 로 락이 해제된다")
    fun lockIsReleasedEvenWhenBlockThrows() {
        assertThrows<IllegalStateException> {
            lock.withLock(LOCK_ID) { throw IllegalStateException("배치 중 실패") }
        }

        assertTrue(lockIsFree(LOCK_ID), "예외로 빠져나가도 락이 남아 있으면 다음 실행이 영원히 스킵된다")
    }

    @Test
    @DisplayName("정상 종료 후에도 락이 해제된다 — 반복 실행이 가능해야 한다")
    fun lockIsReleasedAfterNormalCompletion() {
        assertTrue(lock.withLock(LOCK_ID) { })
        assertTrue(lockIsFree(LOCK_ID))

        // 같은 락으로 다시 돌 수 있어야 한다. 누수되면 여기서 false 가 된다.
        assertTrue(lock.withLock(LOCK_ID) { }, "해제가 제대로 됐다면 재실행이 가능하다")
    }

    @Test
    @DisplayName("락 획득에 실패하면 블록을 실행하지 않고 남의 락을 해제하지도 않는다")
    fun failedAcquisitionIsHarmless() {
        val holder = holdLockInSeparateSession(LOCK_ID)
        try {
            var executed = false
            val ran = lock.withLock(LOCK_ID) { executed = true }

            assertFalse(ran, "락을 못 잡았으므로 false 여야 한다")
            assertFalse(executed, "블록이 실행되면 안 된다")
            assertTrue(
                errorLogs().none { it.contains("해제가 무시") },
                "획득에 실패했으면 해제를 시도조차 하지 않아야 한다. 남의 락을 건드리면 안 된다",
            )
            assertFalse(lockIsFree(LOCK_ID), "보유자의 락이 그대로 유지돼야 한다")
        } finally {
            holder.close()
        }
    }

    @Test
    @DisplayName("해제가 무시되면 error 로그로 드러난다 — 조용한 누수를 막는 안전망")
    fun ignoredReleaseIsLoggedAsError() {
        // 레거시 경로는 획득과 해제가 다른 커넥션일 수 있어 해제가 무시된다.
        // 그 상황이 로그로 드러나는지 확인한다.
        @Suppress("DEPRECATION")
        lock.releaseLock(999_999L) // 아무도 쥐고 있지 않은 락

        assertTrue(
            errorLogs().any { it.contains("해제가 무시") },
            "해제 실패가 로그에 남지 않으면 누수를 알아챌 수 없다. 실제 로그: ${errorLogs()}",
        )
    }
}
