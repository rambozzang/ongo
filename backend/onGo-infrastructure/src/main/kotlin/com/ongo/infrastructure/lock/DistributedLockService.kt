package com.ongo.infrastructure.lock

import com.ongo.domain.lock.DistributedLockPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class DistributedLockService(
    private val dataSource: DataSource,
) : DistributedLockPort {

    private val log = LoggerFactory.getLogger(DistributedLockService::class.java)

    override fun tryLock(lockId: Long): Boolean = try {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT pg_try_advisory_lock(?)").use { stmt ->
                stmt.setLong(1, lockId)
                stmt.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
            }
        }
    } catch (e: Exception) {
        log.warn("Advisory lock 획득 실패: lockId=$lockId", e)
        false
    }

    override fun releaseLock(lockId: Long) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT pg_advisory_unlock(?)").use { stmt ->
                    stmt.setLong(1, lockId)
                    stmt.execute()
                }
            }
        } catch (e: Exception) {
            log.warn("Advisory lock 해제 실패: lockId=$lockId", e)
        }
    }

    companion object {
        fun lockIdFor(className: String): Long {
            return className.hashCode().toLong()
        }
    }
}
