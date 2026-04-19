package com.ongo.domain.keyword

interface KeywordResearchRepository {
    fun save(research: KeywordResearch): KeywordResearch
    fun findByUserId(userId: Long, page: Int, size: Int): List<KeywordResearch>
    fun countByUserId(userId: Long): Int
}
