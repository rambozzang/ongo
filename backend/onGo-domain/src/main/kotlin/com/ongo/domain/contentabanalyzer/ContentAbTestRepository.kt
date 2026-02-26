package com.ongo.domain.contentabanalyzer

interface ContentAbTestRepository {
    fun findById(id: Long): ContentAbTest?
    fun findByUserId(userId: Long): List<ContentAbTest>
    fun findByStatus(userId: Long, status: String): List<ContentAbTest>
    fun save(test: ContentAbTest): ContentAbTest
    fun updateStatus(id: Long, status: String, winner: String?)
}
