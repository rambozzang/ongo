package com.ongo.domain.competitor

interface CompetitorRepository {
    fun findById(id: Long): Competitor?
    fun findByUserId(userId: Long): List<Competitor>
    fun save(competitor: Competitor): Competitor
    fun update(competitor: Competitor): Competitor
    fun delete(id: Long)
    fun countByUserId(userId: Long): Int
}
