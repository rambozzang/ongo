package com.ongo.infrastructure.persistence.ai

import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
class InMemoryWeeklyDigestRepository : WeeklyDigestRepository {

    private val store = ConcurrentHashMap<Long, WeeklyDigest>()
    private val idGenerator = AtomicLong(1)

    override fun findById(id: Long): WeeklyDigest? = store[id]

    override fun findLatestByUserId(userId: Long): WeeklyDigest? =
        store.values
            .filter { it.userId == userId }
            .maxByOrNull { it.generatedAt }

    override fun findByUserId(userId: Long, page: Int, size: Int): List<WeeklyDigest> =
        store.values
            .filter { it.userId == userId }
            .sortedByDescending { it.generatedAt }
            .drop(page * size)
            .take(size)

    override fun countByUserId(userId: Long): Int =
        store.values.count { it.userId == userId }

    override fun save(digest: WeeklyDigest): WeeklyDigest {
        val now = LocalDateTime.now()
        val id = idGenerator.getAndIncrement()
        val saved = digest.copy(
            id = id,
            createdAt = now,
            updatedAt = now,
        )
        store[id] = saved
        return saved
    }
}
