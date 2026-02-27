package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.hashtagstrategy.*
import org.springframework.stereotype.Repository

@Repository
class HashtagStrategyStubRepository : HashtagStrategyRepository {
    override fun findById(id: Long): HashtagSet? = null
    override fun findByUserId(userId: Long): List<HashtagSet> = emptyList()
    override fun save(hashtagSet: HashtagSet): HashtagSet = hashtagSet.copy(id = 1)
    override fun update(hashtagSet: HashtagSet): HashtagSet = hashtagSet
    override fun delete(id: Long) {}
}
