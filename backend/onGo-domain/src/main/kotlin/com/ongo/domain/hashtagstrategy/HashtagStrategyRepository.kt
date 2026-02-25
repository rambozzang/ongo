package com.ongo.domain.hashtagstrategy

interface HashtagStrategyRepository {
    fun findById(id: Long): HashtagSet?
    fun findByUserId(userId: Long): List<HashtagSet>
    fun save(hashtagSet: HashtagSet): HashtagSet
    fun update(hashtagSet: HashtagSet): HashtagSet
    fun delete(id: Long)
}
