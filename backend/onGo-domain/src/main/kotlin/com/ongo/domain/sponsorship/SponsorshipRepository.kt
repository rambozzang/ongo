package com.ongo.domain.sponsorship

interface SponsorshipRepository {
    fun findByUserId(userId: Long): List<Sponsorship>
    fun findByUserIdAndStatus(userId: Long, status: String): List<Sponsorship>
    fun findById(id: Long): Sponsorship?
    fun save(sponsorship: Sponsorship): Sponsorship
    fun update(sponsorship: Sponsorship): Sponsorship
    fun deleteById(id: Long)
}
