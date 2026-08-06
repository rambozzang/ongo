package com.ongo.infrastructure.lock

import com.ongo.domain.lock.DistributedLockPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.Connection
import javax.sql.DataSource

/**
 * PostgreSQL advisory lock 기반 분산 락.
 *
 * `pg_try_advisory_lock` 은 **세션 락**이다. 락을 잡은 커넥션이 그대로 보유하며,
 * 다른 세션은 획득도 해제도 할 수 없다. 커넥션 풀에서는 이게 함정이 된다.
 * 커넥션을 반납해도 세션은 살아 있어 락이 풀리지 않고, 해제할 때 다른 커넥션이 나오면
 * `pg_advisory_unlock` 이 false 를 반환하고 아무 일도 일어나지 않는다.
 * 그 뒤 실행은 락을 못 잡아 배치가 조용히 스킵된다.
 *
 * 그래서 [withLock] 은 커넥션 하나를 잡아 **획득부터 해제까지 같은 커넥션**을 쓴다.
 * 그동안 커넥션 1개를 점유하므로 운영 풀 크기는 최소 2 이상이어야 한다
 * (`spring.datasource.hikari.maximum-pool-size`, 현재 기본 50).
 * 배치가 `leak-detection-threshold`(60초)를 넘으면 leak 경고가 뜨는데, 이건 실제 누수가
 * 아니라 의도된 lease 다. 예상 최대 배치 시간에 맞춰 운영 설정을 조정한다.
 */
@Service
class DistributedLockService(
    private val dataSource: DataSource,
) : DistributedLockPort {

    private val log = LoggerFactory.getLogger(DistributedLockService::class.java)

    override fun withLock(lockId: Long, block: () -> Unit): Boolean {
        val conn = try {
            dataSource.connection
        } catch (e: Exception) {
            log.error("advisory lock 커넥션 확보 실패. lockId={}", lockId, e)
            return false
        }

        return conn.use { held ->
            if (!acquire(held, lockId)) {
                log.info("다른 인스턴스가 실행 중이라 건너뛴다. lockId={}", lockId)
                // 획득에 실패했으므로 해제하지 않는다. 남의 락을 풀면 안 된다.
                return@use false
            }
            try {
                block()
                true
            } finally {
                // 예외로 빠져나가도 반드시 해제한다.
                release(held, lockId)
            }
        }
    }

    @Deprecated("획득/해제가 다른 커넥션에서 일어나 락이 누수된다. withLock 을 사용할 것", ReplaceWith("withLock(lockId) { }"))
    override fun tryLock(lockId: Long): Boolean = try {
        dataSource.connection.use { conn -> acquire(conn, lockId) }
    } catch (e: Exception) {
        log.warn("Advisory lock 획득 실패. lockId={}", lockId, e)
        false
    }

    @Deprecated("tryLock 과 짝을 이루는 누수 경로다. withLock 을 사용할 것", ReplaceWith("withLock(lockId) { }"))
    override fun releaseLock(lockId: Long) {
        try {
            dataSource.connection.use { conn -> release(conn, lockId) }
        } catch (e: Exception) {
            log.warn("Advisory lock 해제 실패. lockId={}", lockId, e)
        }
    }

    private fun acquire(conn: Connection, lockId: Long): Boolean =
        conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
            stmt.setLong(1, lockId)
            stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
        }

    private fun release(conn: Connection, lockId: Long) {
        try {
            conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { stmt ->
                stmt.setLong(1, lockId)
                stmt.executeQuery().use { rs ->
                    // false 는 이 세션이 락을 쥐고 있지 않다는 뜻이다. 조용히 넘기면 누수를 놓친다.
                    if (rs.next() && !rs.getBoolean(1)) {
                        log.error("Advisory lock 해제가 무시됐다. lockId={}", lockId)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Advisory lock 해제 실패. lockId={}", lockId, e)
        }
    }

    companion object {
        fun lockIdFor(className: String): Long {
            return className.hashCode().toLong()
        }
    }
}
