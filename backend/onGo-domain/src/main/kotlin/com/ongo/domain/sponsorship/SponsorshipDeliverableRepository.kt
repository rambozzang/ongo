package com.ongo.domain.sponsorship

interface SponsorshipDeliverableRepository {
    fun findBySponsorshipId(sponsorshipId: Long): List<SponsorshipDeliverable>
    fun findById(id: Long): SponsorshipDeliverable?
    fun save(deliverable: SponsorshipDeliverable): SponsorshipDeliverable
    fun update(deliverable: SponsorshipDeliverable): SponsorshipDeliverable
    fun deleteById(id: Long)
}
